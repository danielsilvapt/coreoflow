package pt.studioflow.service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Professor;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.PresencaRepository;

@Service
public class AlertaAssiduidadeService {

    @Autowired
    private AlunoRepository alunoRepository;
    @Autowired
    private PresencaRepository presencaRepository;
    @Autowired
    private JavaMailSender mailSender;

    @Scheduled(cron = "0 0 9 * * MON")
    // TESTE
    // @Scheduled(fixedDelay = 10000)
    @Transactional(readOnly = true)
    public void verificarAusenciasSemanais() {
        LocalDate hoje = LocalDate.now();
        LocalDate umaSemanaAtras = hoje.minusDays(7);

        List<Aluno> alunosAtivos = alunoRepository.findByAtivo(1);

        Map<Professor, Set<Aluno>> ausenciasPorProfessor = new HashMap<>();

        for (Aluno aluno : alunosAtivos) {
            boolean veioNaUltimaSemana = presencaRepository
                    .existsByAlunoAndDataBetweenAndPresenteTrue(aluno, umaSemanaAtras, hoje);

            if (!veioNaUltimaSemana && !aluno.getTurmas().isEmpty()) {
                for (AlunoTurma at : aluno.getTurmas()) {
                    Professor prof = at.getTurma().getProfessor();
                    if (prof != null && prof.getEmail() != null) {
                        // Usamos Set para garantir que o aluno entre apenas uma vez por professor
                        ausenciasPorProfessor.computeIfAbsent(prof, k -> new HashSet<>()).add(aluno);
                    }
                }
            }
        }

        // Ordenar e enviar
        ausenciasPorProfessor.forEach((professor, setAlunos) -> {
            // Convertemos o Set para List para poder ordenar por nome
            List<Aluno> listaOrdenada = setAlunos.stream()
                    .sorted(Comparator.comparing(a -> a.getNomeCompleto().toLowerCase()))
                    .collect(Collectors.toList());

            enviarEmailTabelaAlerta(professor, listaOrdenada);
        });
    }

    private void enviarEmailTabelaAlerta(Professor professor, List<Aluno> alunos) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(professor.getEmail());
            helper.setCc("noreply@coreoflow.app");
            helper.setSubject("CoreoFlow - Relatório Semanal de Ausências");

            StringBuilder html = new StringBuilder();
            html.append("<h3>Olá, ").append(professor.getNome()).append("!</h3>");
            html.append("<p>Os seguintes alunos das tuas turmas não compareceram às aulas na última semana:</p>");

            html.append("<table border='1' cellpadding='10' style='border-collapse: collapse; width: 100%;'>");
            html.append("<tr style='background-color: #f2f2f2;'>")
                    .append("<th>Nome do Aluno</th>")
                    .append("<th>Turma(s)</th>")
                    .append("<th>Telemóvel (Enc. Ed.)</th>")
                    .append("</tr>");

            for (Aluno aluno : alunos) {
                // Filtramos apenas as turmas deste aluno que pertencem a este professor
                // específico
                String nomesTurmas = aluno.getTurmas().stream()
                        .filter(at -> at.getTurma().getProfessor().equals(professor))
                        .map(at -> at.getTurma().getDescricao())
                        .collect(Collectors.joining(", "));

                html.append("<tr>")
                        .append("<td>").append(aluno.getNomeCompleto()).append("</td>")
                        .append("<td>").append(nomesTurmas).append("</td>")
                        .append("<td>").append(aluno.getTelemovel() != null ? aluno.getTelemovel() : "N/A")
                        .append("</td>")
                        .append("</tr>");
            }

            html.append("</table>");
            html.append(
                    "<p style='margin-top: 20px; color: gray; font-size: 0.8em;'>Este é um lembrete automático do sistema de gestão CoreoFlow.</p>");

            helper.setText(html.toString(), true);
            mailSender.send(message);

        } catch (MessagingException e) {
            System.err.println("Erro ao enviar email para " + professor.getNome() + ": " + e.getMessage());
        }
    }
}