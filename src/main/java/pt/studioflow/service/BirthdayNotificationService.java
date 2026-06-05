package pt.studioflow.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma; // Importa a classe intermédia
import pt.studioflow.repository.AlunoRepository;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Service
public class BirthdayNotificationService {

    private final AlunoRepository repository;
    private final JavaMailSender mailSender;

    public BirthdayNotificationService(AlunoRepository repository, JavaMailSender mailSender) {
        this.repository = repository;
        this.mailSender = mailSender;
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
            try {
                enviarEmailIndividual(aluno);
            } catch (Exception e) {
                System.err.println("Erro ao enviar para: " + aluno.getNomeCompleto() + " -> " + e.getMessage());
            }
        }
    }

    private void enviarEmailIndividual(Aluno aluno) {
        SimpleMailMessage message = new SimpleMailMessage();

        String nomeExibicao = aluno.getNomeCompleto();

        message.setFrom("noreply@coreoflow.app");
        message.setTo(aluno.getEmail());

        // --- Lógica Corrigida para AlunoTurma ---
        Set<String> emailsCc = new HashSet<>();
        emailsCc.add("noreply@coreoflow.app");

        if (aluno.getTurmas() != null) {
            // O erro ocorria aqui: o elemento da lista é AlunoTurma, não Turma
            for (AlunoTurma relacao : aluno.getTurmas()) {
                if (relacao.getTurma() != null &&
                        relacao.getTurma().getProfessor() != null &&
                        relacao.getTurma().getProfessor().getEmail() != null) {

                    emailsCc.add(relacao.getTurma().getProfessor().getEmail());
                }
            }
        }

        message.setCc(emailsCc.toArray(new String[0]));
        // -----------------------------------------

        message.setSubject("Parabéns, " + nomeExibicao + "! 🎂 - CoreoFlow");

        StringBuilder texto = new StringBuilder();
        texto.append("Olá, ").append(nomeExibicao).append("!\n\n");
        texto.append("Muitos parabéns! 🎉\n\n");
        texto.append("A equipa da CoreoFlow deseja-te um dia fantástico, cheio de ritmo e alegria.\n");
        texto.append("Esperamos continuar a dançar contigo por muito tempo!\n\n");
        texto.append("Um grande abraço,\n");
        texto.append("A Equipa CoreoFlow");

        texto.append("\n\n*** Mensagem automática do Plataforma CoreoFlow ***");

        message.setText(texto.toString());
        mailSender.send(message);

        System.out.println(
                "Email enviado para: " + nomeExibicao + " (CC para " + (emailsCc.size() - 1) + " professores)");
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