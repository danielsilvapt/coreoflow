package pt.studioflow.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.StudioRepository;

/**
 * Lembrete "mensalidade a pagamento": no dia 30 de cada mês (último dia em
 * fevereiro) envia aos alunos dos estúdios com {@link Studio#isLembreteMensalidade()}
 * ligado um email com a mensalidade do mês seguinte por pagar e o link do portal.
 * Um email por endereço (irmãos com o mesmo email recebem um só, com todos).
 */
@Service
public class LembreteMensalidadeService {

    private static final Locale PT = Locale.forLanguageTag("pt-PT");

    private final StudioRepository studioRepository;
    private final MensalidadeRepository mensalidadeRepository;
    private final EmailService emailService;
    private final EmailGate emailGate;
    private final MensalidadeConfig mensalidadeConfig;

    @Value("${app.base-url:https://app.coreoflow.me}")
    private String baseUrl;

    public LembreteMensalidadeService(StudioRepository studioRepository, MensalidadeRepository mensalidadeRepository,
            EmailService emailService, EmailGate emailGate, MensalidadeConfig mensalidadeConfig) {
        this.studioRepository = studioRepository;
        this.mensalidadeRepository = mensalidadeRepository;
        this.emailService = emailService;
        this.emailGate = emailGate;
        this.mensalidadeConfig = mensalidadeConfig;
    }

    /** Dia do mês em que sai o lembrete: 30, ou o último dia nos meses mais curtos (fevereiro). */
    public static int diaDoLembrete(YearMonth mes) {
        return Math.min(30, mes.lengthOfMonth());
    }

    /** Todos os dias às 9h; só faz alguma coisa no dia do lembrete. */
    @Scheduled(cron = "0 0 9 * * *")
    public void executar() {
        LocalDate hoje = LocalDate.now();
        if (hoje.getDayOfMonth() != diaDoLembrete(YearMonth.from(hoje))) {
            return;
        }
        YearMonth alvo = YearMonth.from(hoje).plusMonths(1);
        for (Studio s : studioRepository.findAll()) {
            if (!s.isAtivo() || !s.isLembreteMensalidade()) {
                continue;
            }
            try {
                int n = enviarParaStudio(s.getId(), alvo);
                System.out.println("Lembrete de mensalidade " + alvo + " — " + s.getNome() + ": " + n + " emails.");
            } catch (Exception e) {
                System.err.println("Lembrete de mensalidade falhou para " + s.getNome() + ": " + e.getMessage());
            }
        }
    }

    /** Linha do email: um aluno e a sua mensalidade (uma por turma). */
    record Linha(String aluno, String turma, double valor) {
    }

    /**
     * Envia o lembrete do mês {@code alvo} aos alunos do estúdio. Idempotente: se o
     * mês já foi enviado (ver {@link Studio#getLembreteMensalidadeUltimoMes()}) não faz nada.
     *
     * @return número de emails enviados
     */
    @Transactional
    public int enviarParaStudio(Long studioId, YearMonth alvo) {
        Studio studio = studioRepository.findById(studioId).orElse(null);
        if (studio == null || alvo.toString().equals(studio.getLembreteMensalidadeUltimoMes())) {
            return 0;
        }
        if (emailGate.bloqueado(studio)) {
            System.out.println("Lembrete de mensalidade: emails desativados para " + studio.getNome() + ".");
            return 0;
        }

        Map<String, List<Linha>> porEmail = agruparPorEmail(mensalidadeRepository.findAllByStudio(studio), alvo);
        String mesNome = alvo.getMonth().getDisplayName(TextStyle.FULL, PT);
        String assunto = "Mensalidade de " + mesNome + " a pagamento — " + studio.getNome();

        int enviados = 0;
        for (Map.Entry<String, List<Linha>> e : porEmail.entrySet()) {
            try {
                emailService.enviarLembreteMensalidade(studio, e.getKey(), assunto,
                        corpo(studio, alvo, mesNome, e.getValue()));
                enviados++;
            } catch (Exception ex) {
                System.err.println("Lembrete de mensalidade: falha a enviar para " + e.getKey() + " - " + ex.getMessage());
            }
        }
        // Marca o mês como tratado mesmo com falhas pontuais, para não reenviar a quem já recebeu.
        studio.setLembreteMensalidadeUltimoMes(alvo.toString());
        studioRepository.save(studio);
        return enviados;
    }

    /** Mensalidades do mês alvo por pagar, de alunos ativos com email, agrupadas por endereço. */
    static Map<String, List<Linha>> agruparPorEmail(List<Mensalidade> mensalidades, YearMonth alvo) {
        Map<String, List<Linha>> porEmail = new LinkedHashMap<>();
        mensalidades.stream()
                .filter(m -> m.getAno() == alvo.getYear() && m.getMes() == alvo.getMonth())
                .filter(m -> m.getEstado() != EstadoMensalidade.PAGO && m.getValor() > 0)
                .filter(m -> m.getAluno() != null && m.getAluno().isAtivo())
                .filter(m -> m.getAluno().getEmail() != null && !m.getAluno().getEmail().isBlank())
                .sorted(Comparator.comparing((Mensalidade m) -> m.getAluno().getNomeCompleto(),
                        String.CASE_INSENSITIVE_ORDER))
                .forEach(m -> {
                    Aluno a = m.getAluno();
                    porEmail.computeIfAbsent(a.getEmail().trim().toLowerCase(), k -> new ArrayList<>())
                            .add(new Linha(a.getNomeCompleto(),
                                    m.getTurma() != null ? m.getTurma().getDescricao() : "", m.getValor()));
                });
        return porEmail;
    }

    private String corpo(Studio studio, YearMonth alvo, String mesNome, List<Linha> linhas) {
        double total = linhas.stream().mapToDouble(Linha::valor).sum();
        long alunosDistintos = linhas.stream().map(Linha::aluno).distinct().count();
        String saudacao = alunosDistintos == 1 ? "Olá " + esc(primeiroNome(linhas.get(0).aluno())) + "," : "Olá,";

        StringBuilder tabela = new StringBuilder(
                "<table style='border-collapse:collapse;width:100%;margin:18px 0;'>"
                        + "<thead><tr style='background:#f5f5f5;'>"
                        + "<th style='padding:10px 8px;text-align:left;'>Aluno</th>"
                        + "<th style='padding:10px 8px;text-align:left;'>Turma</th>"
                        + "<th style='padding:10px 8px;text-align:right;'>Valor</th></tr></thead><tbody>");
        for (Linha l : linhas) {
            tabela.append("<tr style='border-bottom:1px solid #eee;'>")
                    .append("<td style='padding:10px 8px;'>").append(esc(l.aluno())).append("</td>")
                    .append("<td style='padding:10px 8px;'>").append(esc(l.turma())).append("</td>")
                    .append("<td style='padding:10px 8px;text-align:right;white-space:nowrap;'>")
                    .append(String.format(PT, "%.2f €", l.valor())).append("</td></tr>");
        }
        if (linhas.size() > 1) {
            tabela.append("<tr><td colspan='2' style='padding:10px 8px;text-align:right;font-weight:bold;'>Total</td>")
                    .append("<td style='padding:10px 8px;text-align:right;font-weight:bold;white-space:nowrap;'>")
                    .append(String.format(PT, "%.2f €", total)).append("</td></tr>");
        }
        tabela.append("</tbody></table>");

        Integer diaLimite = mensalidadeConfig.diaLimitePagamento(studio);
        String prazo = diaLimite != null
                ? "<p>O pagamento deve ser feito até <b>" + diaLimite + " de " + mesNome + "</b>.</p>"
                : "";

        String portalUrl = baseUrl + "/portal";
        String botao = studio.isMollieAtivo() ? "Pagar online no portal" : "Ver no portal";
        String textoPortal = studio.isMollieAtivo()
                ? "Podes pagar online, de forma rápida e segura, no portal do aluno:"
                : "Podes consultar as tuas mensalidades no portal do aluno:";

        return "<div style='font-family:Arial,Helvetica,sans-serif;color:#333;max-width:600px;'>"
                + "<p>" + saudacao + "</p>"
                + "<p>Informamos que já se encontra a pagamento a mensalidade de <b>" + mesNome + " de "
                + alvo.getYear() + "</b>" + (alunosDistintos > 1 ? ":" : " no <b>" + esc(studio.getNome()) + "</b>:")
                + "</p>"
                + tabela
                + prazo
                + "<p>" + textoPortal + "</p>"
                + "<p style='margin:20px 0;'><a href='" + portalUrl + "' style='background:#4A90E2;color:#fff;"
                + "padding:12px 24px;text-decoration:none;border-radius:6px;display:inline-block;font-weight:bold;'>"
                + botao + "</a></p>"
                + "<p style='font-size:13px;color:#888;'>Entra com o teu email; se não souberes a palavra-passe, "
                + "usa \"Esqueci-me da palavra-passe\" no ecrã de entrada. Se o botão não funcionar, copia este "
                + "endereço para o navegador: " + portalUrl + "</p>"
                + "<p style='font-size:13px;color:#888;'>Se já efetuaste o pagamento, ignora este email.</p>"
                + "</div>";
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) return "";
        return nome.trim().split("\\s+")[0];
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
