package pt.studioflow.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import pt.studioflow.model.RegistoHoras;
import pt.studioflow.repository.RegistoHorasRepository;

@Service
public class RelatorioHorasService {

    @Autowired
    private RegistoHorasRepository repository;

    @Autowired
    private JavaMailSender mailSender;

    // Envio Automático todo o dia 1 às 08:00
    @Scheduled(cron = "0 0 8 1 * ?")
    public void enviarRelatorioMensal() {
        LocalDate mesAnterior = LocalDate.now().minusMonths(1);
        gerarEEnviarRelatorioManual(mesAnterior);
    }

    public void gerarEEnviarRelatorioManual(LocalDate dataReferencia) {
        int mes = dataReferencia.getMonthValue();
        int ano = dataReferencia.getYear();

        List<RegistoHoras> registos = repository.findAll().stream()
                .filter(r -> r.getMesNumero() == mes && r.getAno() == ano)
                .collect(Collectors.toList());

        if (!registos.isEmpty()) {
            enviarEmail(registos, dataReferencia);
        }
    }

    private void enviarEmail(List<RegistoHoras> registos, LocalDate data) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo("noreply@coreoflow.app");
            helper.setSubject("Resumo Mensal de Horas - " + data.getMonth().name() + " " + data.getYear());

            StringBuilder corpo = new StringBuilder();
            corpo.append("<h1 style='color: #d32f2f;'>Resumo de Horas - CoreoFlow</h1>");
            corpo.append("<p>Segue o resumo das atividades registadas no período:</p>");

            corpo.append("<table border='1' style='border-collapse: collapse; width: 100%; text-align: left;'>");
            corpo.append("<tr style='background-color: #f2f2f2;'>")
                    .append("<th>Professor</th>")
                    .append("<th>Total</th>")
                    .append("<th>Regulares</th>")
                    .append("<th>Ensaios</th>")
                    .append("<th>Privadas</th>")
                    .append("</tr>");

            Map<String, List<RegistoHoras>> porProfessor = registos.stream()
                    .collect(Collectors.groupingBy(RegistoHoras::getProfessor));

            porProfessor.forEach((professor, lista) -> {
                double total = lista.stream().mapToDouble(this::calcularHoras).sum();
                double regulares = lista.stream().filter(r -> "Aula regular".equals(r.getTipoAtividade()))
                        .mapToDouble(this::calcularHoras).sum();
                double ensaios = lista.stream().filter(r -> "Ensaio".equals(r.getTipoAtividade()))
                        .mapToDouble(this::calcularHoras).sum();
                double privadas = lista.stream().filter(r -> "Aula privada".equals(r.getTipoAtividade()))
                        .mapToDouble(this::calcularHoras).sum();

                corpo.append("<tr>")
                        .append("<td><b>").append(professor).append("</b></td>")
                        .append("<td>").append(String.format("%.2f", total)).append("h</td>")
                        .append("<td>").append(String.format("%.2f", regulares)).append("h</td>")
                        .append("<td>").append(String.format("%.2f", ensaios)).append("h</td>")
                        .append("<td>").append(String.format("%.2f", privadas)).append("h</td>")
                        .append("</tr>");
            });

            corpo.append("</table>");

            // Secção de Detalhes/Observações
            corpo.append("<h3>Detalhes e Observações:</h3>");
            corpo.append("<ul>");
            registos.stream().filter(r -> r.getObservacoes() != null && !r.getObservacoes().isEmpty()).forEach(r -> {
                corpo.append("<li><b>").append(r.getProfessor()).append("</b> (").append(r.getTurma()).append("): ")
                        .append(r.getObservacoes()).append("</li>");
            });
            corpo.append("</ul>");

            corpo.append("<br><p><i>Gerado automaticamente pela App de Gestão da CoreoFlow.</i></p>");

            helper.setText(corpo.toString(), true);
            mailSender.send(message);

        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }

    private double calcularHoras(RegistoHoras r) {
        return Duration.between(r.getInicio(), r.getFim()).toMinutes() / 60.0;
    }
}