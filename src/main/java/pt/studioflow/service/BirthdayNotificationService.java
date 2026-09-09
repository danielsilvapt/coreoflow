package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma; // Importa a classe intermédia
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.util.LogoUrl;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Service
public class BirthdayNotificationService {

    private final AlunoRepository repository;
    private final JavaMailSender mailSender;
    private final EmailGate emailGate;

    @Value("${app.mail.from:noreply@coreoflow.me}")
    private String mailFrom;

    @Value("${app.base-url:https://app.coreoflow.me}")
    private String baseUrl;

    public BirthdayNotificationService(AlunoRepository repository, JavaMailSender mailSender, EmailGate emailGate) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.emailGate = emailGate;
    }

    @Scheduled(cron = "0 30 8 * * *")
    // TESTE
    //@Scheduled(fixedDelay = 10000)
    @Transactional(readOnly = true)
    public void enviarAlertaAniversarios() {
        List<Aluno> aniversariantes = repository.findAniversariantesDeHoje();

        if (aniversariantes.isEmpty()) {
            System.out.println("Sem aniversários hoje!");
            return;
        }

        for (Aluno aluno : aniversariantes) {
            if (emailGate.bloqueado(aluno.getStudio())) continue;
            try {
                enviarEmailIndividual(aluno);
            } catch (Exception e) {
                System.err.println("Erro ao enviar para: " + aluno.getNomeCompleto() + " -> " + e.getMessage());
            }
        }
    }

    private void enviarEmailIndividual(Aluno aluno) throws Exception {
        Studio studio = aluno.getStudio();
        String nomeEstudio = (studio != null && studio.getNome() != null && !studio.getNome().isBlank())
                ? studio.getNome().trim() : "CoreoFlow";
        String cor = (studio != null && studio.getCorPrimaria() != null && !studio.getCorPrimaria().isBlank())
                ? studio.getCorPrimaria().trim() : "#4A90E2";
        String primeiro = formatarPrimeiroUltimoNome(aluno.getNomeCompleto()).split(" ")[0];

        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
        h.setFrom(mailFrom, nomeEstudio);
        h.setTo(aluno.getEmail());

        Set<String> cc = new HashSet<>();
        if (aluno.getTurmas() != null) {
            for (AlunoTurma relacao : aluno.getTurmas()) {
                if (relacao.getTurma() != null && relacao.getTurma().getProfessor() != null
                        && relacao.getTurma().getProfessor().getEmail() != null) {
                    cc.add(relacao.getTurma().getProfessor().getEmail());
                }
            }
        }
        if (!cc.isEmpty()) {
            h.setCc(cc.toArray(new String[0]));
        }

        h.setSubject("Parabéns, " + primeiro + "! 🎂");

        String logoImg = "";
        if (studio != null && studio.getLogoPath() != null && !studio.getLogoPath().isBlank()) {
            String lp = studio.getLogoPath().trim();
            String url = lp.startsWith("http") ? lp
                    : baseUrl.replaceAll("/$", "") + "/" + LogoUrl.normalizar(lp);
            logoImg = "<img src='" + url + "' alt='" + esc(nomeEstudio)
                    + "' style='max-height:64px;max-width:220px;margin-bottom:18px'>";
        }

        String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#333;max-width:520px;margin:0 auto\">"
                + "<div style=\"border-top:5px solid " + cor + ";padding:26px 10px\">"
                + logoImg
                + "<h2 style=\"margin:0 0 10px;color:" + cor + "\">Parabéns, " + esc(primeiro) + "! 🎉</h2>"
                + "<p style=\"font-size:15px;line-height:1.55\">Muitos parabéns! A equipa da <b>" + esc(nomeEstudio)
                + "</b> deseja-te um dia fantástico, cheio de ritmo e alegria. "
                + "Esperamos continuar a dançar contigo por muito tempo!</p>"
                + "<p style=\"font-size:15px;line-height:1.55\">Um grande abraço,<br><b>" + esc(nomeEstudio)
                + "</b></p>"
                + "</div>"
                + "<p style=\"font-size:11px;color:#9aa;text-align:center;margin-top:18px\">"
                + "Mensagem automática · enviada através do <b>CoreoFlow</b></p>"
                + "</div>";
        h.setText(html, true);
        mailSender.send(msg);

        System.out.println("Email de aniversário enviado a " + aluno.getNomeCompleto()
                + " (" + nomeEstudio + ", CC " + cc.size() + " prof.)");
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String formatarPrimeiroUltimoNome(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.trim().isEmpty()) {
            return "Aniversariante";
        }
        String[] partes = nomeCompleto.trim().split("\\s+");
        if (partes.length > 1) {
            return partes[0] + " " + partes[partes.length - 1];
        }
        return partes[0];
    }
}