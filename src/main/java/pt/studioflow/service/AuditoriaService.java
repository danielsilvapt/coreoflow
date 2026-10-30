package pt.studioflow.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import pt.studioflow.model.RegistoAuditoria;
import pt.studioflow.model.RegistoAuditoria.Tipo;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.RegistoAuditoriaRepository;
import pt.studioflow.repository.StudioRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Auditoria de acessos e ações dos utilizadores (consultada pelo superadmin em
 * /admin/acessos). Os pontos de captura (login/logout no SecurityConfig, páginas
 * abertas no AuditoriaNavegacaoListener, alterações de dados no
 * AuditoriaEntidadesListener) chamam os métodos registar*, que apenas
 * enfileiram — um worker em segundo plano grava em lotes. Nunca lança exceções:
 * uma falha na auditoria não pode partir a ação do utilizador.
 */
@Service
public class AuditoriaService {

    /** Atributo de pedido marcado pelo AuthProviderConfig quando o login usa a password mestra. */
    public static final String ATTR_PASSWORD_MESTRA = "auditoria.passwordMestra";

    private final RegistoAuditoriaRepository repo;
    private final StudioRepository studioRepository;

    @Value("${app.auditoria.retencao-dias:365}")
    private int retencaoDias;

    private final LinkedBlockingQueue<RegistoAuditoria> fila = new LinkedBlockingQueue<>(10000);
    private ScheduledExecutorService worker;

    public AuditoriaService(RegistoAuditoriaRepository repo, StudioRepository studioRepository) {
        this.repo = repo;
        this.studioRepository = studioRepository;
    }

    @PostConstruct
    void iniciar() {
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auditoria-writer");
            t.setDaemon(true);
            return t;
        });
        worker.scheduleWithFixedDelay(this::gravarLote, 2, 2, TimeUnit.SECONDS);
    }

    @PreDestroy
    void parar() {
        try {
            if (worker != null) {
                worker.shutdown();
            }
            gravarLote();
        } catch (Exception ignored) {
            // shutdown - nada a fazer
        }
    }

    // ---------------------------------------------------------------- API

    public void registarLogin(Authentication auth) {
        boolean mestra = Boolean.TRUE.equals(atributoPedido(ATTR_PASSWORD_MESTRA));
        RegistoAuditoria r = novo(Tipo.LOGIN, auth);
        if (r == null) {
            return;
        }
        r.setPasswordMestra(mestra);
        r.setDescricao(mestra ? "Sessão iniciada com a password mestra" : "Sessão iniciada");
        enfileirar(r);
    }

    public void registarLoginFalhado(String usernameTentado, String motivo) {
        try {
            RegistoAuditoria r = new RegistoAuditoria();
            r.setData(LocalDateTime.now());
            r.setTipo(Tipo.LOGIN_FALHADO);
            aplicarUsername(r, usernameTentado == null || usernameTentado.isBlank() ? "(vazio)" : usernameTentado.trim());
            r.setDescricao("Tentativa de login falhada" + (motivo != null ? ": " + motivo : ""));
            r.setIp(ipAtual());
            enfileirar(r);
        } catch (Throwable ignore) {
            // auditoria nunca rebenta
        }
    }

    public void registarLogout(Authentication auth) {
        RegistoAuditoria r = novo(Tipo.LOGOUT, auth);
        if (r != null) {
            r.setDescricao("Sessão terminada");
            enfileirar(r);
        }
    }

    public void registarNavegacao(String rota, String pagina) {
        RegistoAuditoria r = novo(Tipo.NAVEGACAO, null);
        if (r != null) {
            r.setDescricao("Abriu " + (pagina != null && !pagina.isBlank() ? pagina + " (/" + rota + ")" : "/" + rota));
            enfileirar(r);
        }
    }

    /**
     * Criação, alteração ou remoção de uma entidade. {@code studioIdEntidade} é o
     * estúdio a que a entidade pertence (quando se sabe) — tem prioridade sobre o
     * do utilizador, para que as ações do superadmin num estúdio fiquem associadas a ele.
     */
    public void registarDados(Tipo tipo, String entidade, String entidadeId, String descricao, Long studioIdEntidade) {
        RegistoAuditoria r = novo(tipo, null);
        if (r == null) {
            return;
        }
        r.setEntidade(truncar(entidade, 80));
        r.setEntidadeId(truncar(entidadeId, 40));
        r.setDescricao(descricao);
        if (studioIdEntidade != null) {
            r.setStudioId(studioIdEntidade);
            r.setStudioSlug(null);
        }
        enfileirar(r);
    }

    // ---------------------------------------------------------------- interno

    /** Cria um registo preenchido com o ator (auth dada ou a do contexto atual), data e IP. */
    private RegistoAuditoria novo(Tipo tipo, Authentication auth) {
        try {
            if (auth == null) {
                auth = SecurityContextHolder.getContext().getAuthentication();
            }
            RegistoAuditoria r = new RegistoAuditoria();
            r.setData(LocalDateTime.now());
            r.setTipo(tipo);
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                aplicarUsername(r, auth.getName());
                r.setPapel(auth.getAuthorities().stream().findFirst()
                        .map(GrantedAuthority::getAuthority)
                        .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                        .orElse(null));
            } else {
                // Sem utilizador: num pedido HTTP é uma página pública (inscrição, webhook...),
                // fora de um pedido é uma tarefa agendada.
                r.setUtilizador(RequestContextHolder.getRequestAttributes() != null ? "público" : "sistema");
            }
            r.setIp(ipAtual());
            return r;
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** Separa "slug:username" em estúdio + utilizador. */
    private static void aplicarUsername(RegistoAuditoria r, String nomeCompleto) {
        if (nomeCompleto.contains(CustomUserDetailsService.TENANT_SEPARATOR)) {
            String[] partes = nomeCompleto.split(CustomUserDetailsService.TENANT_SEPARATOR, 2);
            r.setStudioSlug(truncar(partes[0], 78));
            r.setUtilizador(truncar(partes[1], 150));
        } else {
            r.setUtilizador(truncar(nomeCompleto, 150));
        }
    }

    private void enfileirar(RegistoAuditoria r) {
        r.setDescricao(truncar(r.getDescricao(), 3990));
        fila.offer(r); // se a fila estiver cheia, descarta silenciosamente
    }

    private void gravarLote() {
        List<RegistoAuditoria> lote = new ArrayList<>();
        fila.drainTo(lote, 500);
        if (lote.isEmpty()) {
            return;
        }
        try {
            completarEstudios(lote);
            repo.saveAll(lote);
        } catch (Exception e) {
            System.err.println("AuditoriaService: falha a gravar " + lote.size() + " registos - " + e.getMessage());
        }
    }

    /** Preenche id/slug/nome do estúdio a partir do que cada registo já tiver. */
    private void completarEstudios(List<RegistoAuditoria> lote) {
        Map<Long, Studio> porId = new HashMap<>();
        Map<String, Studio> porSlug = new HashMap<>();
        for (Studio s : studioRepository.findAll()) {
            porId.put(s.getId(), s);
            if (s.getSlug() != null) {
                porSlug.put(s.getSlug(), s);
            }
        }
        for (RegistoAuditoria r : lote) {
            Studio s = r.getStudioId() != null ? porId.get(r.getStudioId())
                    : r.getStudioSlug() != null ? porSlug.get(r.getStudioSlug()) : null;
            if (s != null) {
                r.setStudioId(s.getId());
                r.setStudioSlug(truncar(s.getSlug(), 78));
                r.setStudioNome(truncar(s.getNome(), 150));
            }
        }
    }

    private static Object atributoPedido(String nome) {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getAttribute(nome, RequestAttributes.SCOPE_REQUEST) : null;
    }

    /** IP do cliente — atrás do nginx vem em X-Forwarded-For / X-Real-IP. */
    private static String ipAtual() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra)) {
            return null;
        }
        HttpServletRequest req = sra.getRequest();
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = req.getHeader("X-Real-IP");
            if (ip == null || ip.isBlank()) {
                ip = req.getRemoteAddr();
            }
        }
        return truncar(ip, 64);
    }

    /** Limpeza diária: apaga registos mais antigos que a retenção configurada. */
    @Scheduled(cron = "0 30 4 * * *")
    public void limpar() {
        try {
            int n = repo.apagarAnterioresA(LocalDateTime.now().minusDays(Math.max(30, retencaoDias)));
            if (n > 0) {
                System.out.println("AuditoriaService: " + n + " registos de auditoria antigos apagados.");
            }
        } catch (Exception e) {
            System.err.println("AuditoriaService.limpar: " + e.getMessage());
        }
    }

    private static String truncar(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
