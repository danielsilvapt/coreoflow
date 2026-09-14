package pt.studioflow.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import pt.studioflow.config.TenantContext;
import pt.studioflow.model.ConfiguracaoPlataforma;
import pt.studioflow.model.LogEntry;
import pt.studioflow.repository.LogEntryRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Liga um appender do Logback que grava em BD todos os eventos de nível WARN e
 * superior, para o superadmin os poder consultar em /admin/logs. O appender
 * apenas enfileira; um worker em segundo plano grava em lotes. Nunca lança
 * exceções nem escreve logs (evita recursão).
 */
@Service
public class LogCaptureService {

    private final LogEntryRepository repo;
    private final SuporteService suporteService;

    @Value("${app.logs.retencao-dias:30}")
    private int retencaoDias;

    private final LinkedBlockingQueue<LogEntry> fila = new LinkedBlockingQueue<>(5000);
    private ScheduledExecutorService worker;
    private DbAppender appender;

    /** Último alerta enviado por chave (studio+origem+mensagem), para não repetir o mesmo erro em loop. */
    private final ConcurrentHashMap<String, LocalDateTime> ultimoAlertaPorChave = new ConcurrentHashMap<>();
    private static final Duration COOLDOWN_POR_CHAVE = Duration.ofMinutes(30);
    /** Limite global: nunca mais de um alerta por minuto, mesmo que sejam erros diferentes. */
    private volatile LocalDateTime ultimoAlertaGlobal = LocalDateTime.MIN;
    private static final Duration COOLDOWN_GLOBAL = Duration.ofSeconds(60);

    public LogCaptureService(LogEntryRepository repo, SuporteService suporteService) {
        this.repo = repo;
        this.suporteService = suporteService;
    }

    @PostConstruct
    void iniciar() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();

        appender = new DbAppender();
        appender.setName("db-log-appender");
        appender.setContext(ctx);
        appender.start();

        ch.qos.logback.classic.Logger root = ctx.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);

        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "db-log-writer");
            t.setDaemon(true);
            return t;
        });
        worker.scheduleWithFixedDelay(this::gravarLote, 2, 2, TimeUnit.SECONDS);
    }

    @PreDestroy
    void parar() {
        try {
            if (appender != null) {
                appender.stop();
                LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
                ctx.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).detachAppender(appender);
            }
            if (worker != null) {
                worker.shutdown();
            }
            gravarLote();
        } catch (Exception ignored) {
            // shutdown - nada a fazer
        }
    }

    private void gravarLote() {
        List<LogEntry> lote = new ArrayList<>();
        fila.drainTo(lote, 300);
        if (lote.isEmpty()) {
            return;
        }
        try {
            repo.saveAll(lote);
        } catch (Exception e) {
            // não voltar a pôr na fila para não crescer sem limite; perde-se este lote
            System.err.println("LogCaptureService: falha a gravar " + lote.size() + " logs - " + e.getMessage());
        }

        for (LogEntry e : lote) {
            if ("ERROR".equals(e.getNivel())) {
                avaliarAlerta(e);
            }
        }
    }

    /**
     * Envia um email de suporte para o erro, respeitando o cooldown por chave (mesmo
     * erro repetido) e o cooldown global (proteção contra rajadas de erros diferentes).
     * Corre sempre na thread de fundo do writer, nunca bloqueia quem gerou o log.
     */
    private void avaliarAlerta(LogEntry e) {
        try {
            ConfiguracaoPlataforma cfg = suporteService.getConfig();
            if (!cfg.isAlertaErroAtivo()) {
                return;
            }

            LocalDateTime agora = LocalDateTime.now();
            String chave = (e.getStudioSlug() != null ? e.getStudioSlug() : "-") + "|" + e.getLogger() + "|"
                    + truncar(e.getMensagem(), 150);
            LocalDateTime ultimo = ultimoAlertaPorChave.get(chave);
            if (ultimo != null && Duration.between(ultimo, agora).compareTo(COOLDOWN_POR_CHAVE) < 0) {
                return;
            }
            synchronized (this) {
                if (Duration.between(ultimoAlertaGlobal, agora).compareTo(COOLDOWN_GLOBAL) < 0) {
                    return;
                }
                ultimoAlertaGlobal = agora;
            }
            ultimoAlertaPorChave.put(chave, agora);

            String descricao = "Ocorreu um erro automático na aplicação.\n\n"
                    + "Origem: " + e.getLogger() + "\n"
                    + "Thread: " + e.getThread() + "\n"
                    + "Data: " + agora + "\n\n"
                    + "Mensagem:\n" + e.getMensagem()
                    + (e.getStacktrace() != null ? "\n\nStacktrace:\n" + truncar(e.getStacktrace(), 3500) : "");

            suporteService.registarPedido(
                    e.getStudioSlug() != null ? e.getStudioSlug() : "Plataforma",
                    "Sistema (alerta automático)",
                    null,
                    "ERRO_AUTOMATICO",
                    "Erro automático: " + e.getLoggerCurto(),
                    descricao);
        } catch (Throwable ignore) {
            // nunca deixar o alerta de erro gerar outro erro
        }
    }

    /** Limpeza diária: apaga logs mais antigos que a retenção configurada. */
    @Scheduled(cron = "0 15 4 * * *")
    public void limpar() {
        try {
            long n = repo.deleteByDataBefore(LocalDateTime.now().minusDays(Math.max(1, retencaoDias)));
            if (n > 0) {
                System.out.println("LogCaptureService: " + n + " logs antigos apagados.");
            }
        } catch (Exception e) {
            System.err.println("LogCaptureService.limpar: " + e.getMessage());
        }
    }

    private static String truncar(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private class DbAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent ev) {
            try {
                if (!ev.getLevel().isGreaterOrEqual(Level.WARN)) {
                    return;
                }
                // Não captura os próprios erros do writer (evita recursão)
                if (ev.getLoggerName() != null
                        && ev.getLoggerName().contains("LogCaptureService")) {
                    return;
                }
                LogEntry e = new LogEntry();
                e.setData(LocalDateTime.now());
                e.setNivel(ev.getLevel().toString());
                e.setLogger(truncar(ev.getLoggerName(), 250));
                e.setThread(truncar(ev.getThreadName(), 110));
                e.setMensagem(truncar(ev.getFormattedMessage(), 1990));
                if (ev.getThrowableProxy() != null) {
                    e.setStacktrace(truncar(ThrowableProxyUtil.asString(ev.getThrowableProxy()), 60000));
                }
                String slug = null;
                try {
                    if (ev.getMDCPropertyMap() != null) {
                        slug = ev.getMDCPropertyMap().get("studio");
                    }
                    if (slug == null && TenantContext.getCurrentStudio() != null) {
                        slug = TenantContext.getCurrentStudio().getSlug();
                    }
                } catch (Throwable ignore) {
                    // contexto Vaadin pode não estar disponível na thread do log
                }
                e.setStudioSlug(truncar(slug, 78));

                fila.offer(e); // se a fila estiver cheia, descarta silenciosamente
            } catch (Throwable ignore) {
                // um appender NUNCA pode rebentar
            }
        }
    }
}
