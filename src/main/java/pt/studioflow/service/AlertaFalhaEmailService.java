package pt.studioflow.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import pt.studioflow.config.AsyncEmailConfig;
import pt.studioflow.model.RegistoEmailDestinatario.EstadoEntrega;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.StudioRepository;

/**
 * Avisa o estúdio (com cópia para o responsável da plataforma) quando um email
 * enviado pela plataforma dá erro, explicando o motivo e o que o estúdio pode fazer:
 * <ul>
 * <li><b>no envio</b> — o servidor SMTP recusou ou não respondeu;</li>
 * <li><b>na entrega</b> — o Brevo reportou devolução, bloqueio, endereço inválido,
 * caixa cheia ou erro (via webhook).</li>
 * </ul>
 *
 * <p>Para o webhook saber de que estúdio é um email automático, cada email sai
 * marcado com o cabeçalho {@code X-Mailin-custom: cf-studio=<id>}, que o Brevo
 * devolve nos eventos. Os próprios alertas saem marcados com {@code cf-alerta}
 * e nunca geram novo alerta (evita ciclos).
 */
@Service
public class AlertaFalhaEmailService {

    public static final String CABECALHO = "X-Mailin-custom";
    private static final String PREFIXO_STUDIO = "cf-studio=";
    private static final String MARCA_ALERTA = "cf-alerta";

    /** Não repete o mesmo alerta (estúdio + destinatário + assunto) dentro deste intervalo. */
    private static final long JANELA_REPETICAO_HORAS = 12;

    private final JavaMailSender mailSender;
    private final StudioRepository studioRepository;
    private final EmailAssinatura assinatura;
    private final Map<String, LocalDateTime> enviados = new ConcurrentHashMap<>();

    @Value("${app.mail.from:${spring.mail.username}}")
    private String mailFrom;

    @Value("${app.mail.alerta-cc:dfc.daniel@gmail.com}")
    private String alertaCc;

    public AlertaFalhaEmailService(JavaMailSender mailSender, StudioRepository studioRepository,
            EmailAssinatura assinatura) {
        this.mailSender = mailSender;
        this.studioRepository = studioRepository;
        this.assinatura = assinatura;
    }

    // ------------------------------------------------------------ marcação

    /** Marca o email com o estúdio, para o webhook do Brevo o conseguir associar. */
    public void marcar(MimeMessage msg, Studio studio) {
        try {
            Long id = studio != null ? studio.getId() : null;
            if (id != null && msg.getHeader(CABECALHO) == null) {
                msg.setHeader(CABECALHO, PREFIXO_STUDIO + id);
            }
        } catch (Exception e) {
            // Marcação é acessória: nunca impede o envio.
        }
    }

    /** Extrai o id do estúdio do valor do cabeçalho devolvido pelo Brevo (null se não houver). */
    public static Long studioDoCabecalho(String valor) {
        if (valor == null) {
            return null;
        }
        for (String parte : valor.split("[;,\\s]+")) {
            if (parte.startsWith(PREFIXO_STUDIO)) {
                try {
                    return Long.valueOf(parte.substring(PREFIXO_STUDIO.length()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** {@code true} se o valor do cabeçalho identifica um email de alerta (não se alerta sobre alertas). */
    public static boolean eAlerta(String valor) {
        return valor != null && valor.contains(MARCA_ALERTA);
    }

    // ------------------------------------------------------------ diagnóstico

    /** Causa provável da falha, explicada ao estúdio, e o que pode fazer. */
    public record Diagnostico(String causa, String explicacao, String acao) {
    }

    static final String CAUSA_DESCONHECIDA = "Motivo não identificado";
    static final String CAUSA_TEMPORARIA = "Problema temporário no servidor do destinatário";

    private static final String ACAO_CORRIGIR = "Confirme o email com o aluno/encarregado (por telefone, SMS ou "
            + "pessoalmente) e corrija-o na ficha do aluno (ou do professor) no CoreoFlow. "
            + "Depois volte a enviar a mensagem, se for importante.";

    private static final Pattern CAIXA_CHEIA = padrao("mailbox (is )?full", "quota", "storage", "insufficient",
            "exceeded", "caixa cheia", "4\\.2\\.2", "5\\.2\\.2");
    private static final Pattern DOMINIO = padrao("domain not found", "host not found", "no mx", "nxdomain",
            "unroutable", "unrouteable", "5\\.1\\.2", "domain does not exist", "no such domain",
            "name or service not known");
    private static final Pattern DESATIVADA = padrao("disabled", "inactive", "suspended", "deactivated",
            "account.*closed", "no longer");
    private static final Pattern NAO_EXISTE = padrao("user unknown", "unknown user", "no such user",
            "does not exist", "doesn't exist", "not exist", "recipient rejected", "invalid recipient",
            "address rejected", "mailbox unavailable", "mailbox not found", "user not found", "5\\.1\\.1",
            "invalid address", "invalid mailbox", "recipient not found", "undeliverable address");
    private static final Pattern SPAM = padrao("spam", "blacklist", "blocklist", "block list", "black list",
            "policy", "reputation", "5\\.7\\.", "denied");
    private static final Pattern TEMPORARIO = padrao("try again", "temporar", "timeout", "timed out", "deferred",
            "greylist", "4\\.\\d\\.\\d");
    private static final Pattern FORMATO = padrao("illegal address", "invalid address", "malformed", "parse",
            "address.*invalid");

    private static Pattern padrao(String... termos) {
        return Pattern.compile(String.join("|", termos), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    private static boolean tem(Pattern p, String s) {
        return s != null && p.matcher(s).find();
    }

    /**
     * Decide se um evento de entrega justifica alertar o estúdio. Falhas
     * definitivas sempre; devoluções temporárias só quando o motivo é claro
     * (ex.: caixa cheia), porque as restantes costumam resolver-se sozinhas.
     */
    public static boolean deveAlertar(EstadoEntrega estado, String motivo) {
        if (estado == null) {
            return false;
        }
        return switch (estado) {
            case DEVOLVIDO, BLOQUEADO, INVALIDO, ERRO -> true;
            case DEVOLVIDO_TEMP -> {
                String c = diagnosticar(estado, motivo).causa();
                yield !CAUSA_DESCONHECIDA.equals(c) && !CAUSA_TEMPORARIA.equals(c);
            }
            default -> false;
        };
    }

    /** Traduz o estado e o motivo técnico (do Brevo) numa explicação e numa ação para o estúdio. */
    public static Diagnostico diagnosticar(EstadoEntrega estado, String motivo) {
        if (tem(CAIXA_CHEIA, motivo)) {
            return new Diagnostico("Caixa de correio cheia",
                    "A caixa de email do destinatário está cheia e não aceita mais mensagens.",
                    "Avise o aluno/encarregado (por telefone, SMS ou WhatsApp) para apagar emails antigos e "
                            + "libertar espaço. Se já não usar esse email, peça-lhe um endereço alternativo e "
                            + "atualize-o na ficha do aluno no CoreoFlow. Depois volte a enviar a mensagem, "
                            + "se for importante.");
        }
        if (tem(DOMINIO, motivo)) {
            return new Diagnostico("O domínio do email não existe",
                    "A parte do endereço depois do «@» não existe — normalmente é um erro de escrita "
                            + "(ex.: «gmial.com» ou «hotmail.con» em vez de «gmail.com» / «hotmail.com»).",
                    ACAO_CORRIGIR);
        }
        if (tem(DESATIVADA, motivo)) {
            return new Diagnostico("Conta de email desativada",
                    "O endereço existiu, mas a conta foi desativada ou encerrada pelo fornecedor de email.",
                    "Peça ao aluno/encarregado um endereço de email que use atualmente e atualize-o "
                            + "na ficha do aluno no CoreoFlow.");
        }
        if (estado == EstadoEntrega.INVALIDO || tem(NAO_EXISTE, motivo)) {
            return new Diagnostico("O endereço de email não existe",
                    "O servidor do destinatário indica que este endereço não existe — muito provavelmente "
                            + "há um erro de escrita no email registado (uma letra a mais ou a menos, um ponto, etc.).",
                    ACAO_CORRIGIR);
        }
        if (estado == EstadoEntrega.BLOQUEADO) {
            return new Diagnostico("Endereço bloqueado para envio",
                    "O serviço de envio não tentou entregar porque este endereço já falhou antes "
                            + "(não existe ou devolveu emails) ou o destinatário cancelou a receção.",
                    ACAO_CORRIGIR + " Se o endereço estiver correto, contacte o suporte do CoreoFlow para o "
                            + "desbloquear.");
        }
        if (tem(SPAM, motivo)) {
            return new Diagnostico("Rejeitado pelos filtros anti-spam do destinatário",
                    "O servidor de email do destinatário recusou a mensagem por políticas anti-spam.",
                    "Peça ao aluno/encarregado que verifique a pasta de Spam/Lixo e adicione o endereço do "
                            + "estúdio aos contactos. Se acontecer com vários alunos, contacte o suporte do CoreoFlow.");
        }
        if (estado == EstadoEntrega.DEVOLVIDO_TEMP || tem(TEMPORARIO, motivo)) {
            return new Diagnostico(CAUSA_TEMPORARIA,
                    "O servidor do destinatário não aceitou a mensagem por agora.",
                    "Normalmente resolve-se sozinho. Se voltar a acontecer, confirme o email com o aluno/encarregado.");
        }
        return new Diagnostico(CAUSA_DESCONHECIDA,
                "O email foi devolvido sem um motivo claro.",
                ACAO_CORRIGIR);
    }

    /** Falha no envio (servidor SMTP da plataforma), normalmente não é culpa do endereço. */
    static Diagnostico diagnosticarEnvio(String motivo) {
        if (tem(FORMATO, motivo)) {
            return new Diagnostico("Endereço de email mal escrito",
                    "O endereço registado não tem um formato válido (ex.: espaços, falta do «@» ou do domínio).",
                    ACAO_CORRIGIR);
        }
        return new Diagnostico("Falha no servidor de email da plataforma",
                "O servidor de envio do CoreoFlow não aceitou o email ou não respondeu — o problema não é do "
                        + "endereço do destinatário. O suporte do CoreoFlow recebeu este aviso em cópia.",
                "Não precisa de alterar nada na ficha. Se a mensagem era importante, volte a enviá-la mais tarde "
                        + "ou contacte o destinatário por outra via.");
    }

    // ------------------------------------------------------------ alertas

    /** O servidor de email (SMTP) recusou o envio ou não respondeu. */
    @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
    public void alertarFalhaEnvio(Long studioId, List<String> destinatarios, String assunto, String motivo) {
        String para = destinatarios == null || destinatarios.isEmpty() ? "—" : String.join(", ", destinatarios);
        alertar(studioId, para, assunto, "Não enviado", motivo, diagnosticarEnvio(motivo));
    }

    /** O Brevo reportou que o email não foi entregue (devolvido, bloqueado, inválido, caixa cheia, erro). */
    @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
    public void alertarFalhaEntrega(Long studioId, String destinatario, String assunto, EstadoEntrega estado,
            String motivo) {
        alertar(studioId, destinatario, assunto, estado != null ? estado.getLabel() : "Falhou", motivo,
                diagnosticar(estado, motivo));
    }

    private void alertar(Long studioId, String destinatario, String assunto, String estado, String motivo,
            Diagnostico diag) {
        try {
            if (repetido(studioId + "|" + destinatario + "|" + assunto)) {
                return;
            }
            Studio studio = studioId != null ? studioRepository.findById(studioId).orElse(null) : null;
            String emailEstudio = studio != null && studio.getEmailContacto() != null
                    && !studio.getEmailContacto().isBlank() ? studio.getEmailContacto().trim() : null;
            boolean temCc = alertaCc != null && !alertaCc.isBlank();

            // Se o endereço que falhou é o do próprio estúdio, de nada serve mandar-lhe o aviso.
            boolean estudioEDestino = emailEstudio != null && destinatario != null
                    && destinatario.toLowerCase().contains(emailEstudio.toLowerCase());
            String to = emailEstudio != null && !estudioEDestino ? emailEstudio : null;
            if (to == null && !temCc) {
                return;
            }

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(mailFrom, "CoreoFlow");
            if (to != null) {
                h.setTo(to);
                if (temCc && !alertaCc.trim().equalsIgnoreCase(to)) {
                    h.setCc(alertaCc.trim());
                }
            } else {
                h.setTo(alertaCc.trim());
            }
            String nomeEstudio = studio != null && studio.getNome() != null ? studio.getNome() : "estúdio desconhecido";
            h.setSubject(assuntoAlerta(diag, destinatario));
            h.setText(corpoAlerta(nomeEstudio, destinatario, assunto, estado, motivo, diag) + assinatura.html(studio),
                    true);
            msg.setHeader(CABECALHO, MARCA_ALERTA);
            mailSender.send(msg);
        } catch (Exception e) {
            System.err.println("Falha ao enviar alerta de email não entregue (" + assunto + " → " + destinatario
                    + "): " + e.getMessage());
        }
    }

    static String assuntoAlerta(Diagnostico diag, String destinatario) {
        return "⚠ Email não entregue (" + diag.causa() + "): " + destinatario;
    }

    static String corpoAlerta(String nomeEstudio, String destinatario, String assunto, String estado,
            String motivo, Diagnostico diag) {
        return "<div style='font-family:Arial,Helvetica,sans-serif;color:#333;max-width:600px;'>"
                    + "<p>Olá,</p>"
                    + "<p>Um email enviado pela plataforma <b>não chegou ao destinatário</b>.</p>"
                    + "<ul style='background:#fdf2f2;padding:15px;border-left:4px solid #e74c3c;list-style-type:none;'>"
                    + "<li><b>Estúdio:</b> " + esc(nomeEstudio) + "</li>"
                    + "<li><b>Destinatário:</b> " + esc(destinatario) + "</li>"
                    + "<li><b>Assunto:</b> " + esc(assunto) + "</li>"
                    + "<li><b>Estado:</b> " + esc(estado) + "</li>"
                    + "<li><b>Data:</b> " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    + "</li></ul>"
                    + "<h3 style='margin:20px 0 6px;color:#c0392b;'>Motivo: " + esc(diag.causa()) + "</h3>"
                    + "<p style='margin:0 0 14px;'>" + esc(diag.explicacao()) + "</p>"
                    + "<div style='background:#f0f7ff;padding:12px 15px;border-left:4px solid #2E86DE;'>"
                    + "<b>O que fazer:</b><br>" + esc(diag.acao()) + "</div>"
                    + (motivo != null && !motivo.isBlank()
                            ? "<p style='font-size:12px;color:#999;margin-top:16px;'>Resposta técnica do servidor: "
                                    + esc(motivo) + "</p>"
                            : "")
                    + "<p style='font-size:0.8em;color:#999;'>Mensagem gerada automaticamente pela plataforma CoreoFlow.</p>"
                    + "</div>";
    }

    private boolean repetido(String chave) {
        LocalDateTime agora = LocalDateTime.now();
        enviados.values().removeIf(t -> t.isBefore(agora.minusHours(JANELA_REPETICAO_HORAS)));
        return enviados.putIfAbsent(chave, agora) != null;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
