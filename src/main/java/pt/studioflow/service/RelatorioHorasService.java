package pt.studioflow.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
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
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.AulaRepository;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.RegistoHorasRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.TurmaRepository;

@Service
public class RelatorioHorasService {

    @Autowired private RegistoHorasRepository repository;
    @Autowired private JavaMailSender mailSender;
    @Autowired private StudioRepository studioRepository;
    @Autowired private ProfessorRepository professorRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private MensalidadeRepository mensalidadeRepository;
    @Autowired private AlunoTurmaRepository alunoTurmaRepository;
    @Autowired private AulaRepository aulaRepository;
    @Autowired private RemuneracaoService remuneracaoService;

    // Envio Automático todo o dia 1 às 08:00
    @Scheduled(cron = "0 0 8 1 * ?")
    public void enviarRelatorioMensal() {
        gerarEEnviarRelatorioManual(LocalDate.now().minusMonths(1));
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

            YearMonth mes = YearMonth.from(data);
            StringBuilder corpo = new StringBuilder();
            corpo.append("<h1 style='color: #d32f2f;'>Resumo de Horas e Pagamentos - CoreoFlow</h1>");
            corpo.append("<p>Período: ").append(mes).append("</p>");

            corpo.append("<table border='1' style='border-collapse: collapse; width: 100%; text-align: left;'>");
            corpo.append("<tr style='background-color: #f2f2f2;'>")
                    .append("<th>Professor</th>")
                    .append("<th>Total h</th>")
                    .append("<th>Regulares</th>")
                    .append("<th>Ensaios</th>")
                    .append("<th>Privadas</th>")
                    .append("<th>€ a pagar</th>")
                    .append("</tr>");

            // Pagamentos por professor, agregados por estúdio (a % precisa do estúdio).
            Map<String, Double> pagamentoPorNome = new java.util.HashMap<>();
            for (Studio s : studioRepository.findAll()) {
                if (!s.isAtivo()) continue;
                RemuneracaoService.Dados dados = new RemuneracaoService.Dados()
                        .registos(repository.findAllByStudio(s))
                        .mensalidades(mensalidadeRepository.findAllByStudio(s))
                        .inscricoes(alunoTurmaRepository.findAll().stream()
                                .filter(at -> at.getTurma() != null && at.getTurma().getStudio() != null
                                        && at.getTurma().getStudio().getId().equals(s.getId()))
                                .collect(Collectors.toList()))
                        .aulas(aulaRepository.findByTurmaStudio(s));
                remuneracaoService.pagamentosPorProfessor(
                        professorRepository.findAllByStudio(s), turmaRepository.findAllByStudio(s), mes, dados)
                        .forEach(l -> pagamentoPorNome.merge(l.nome(), l.total(), Double::sum));
            }

            Map<String, List<RegistoHoras>> porProfessor = registos.stream()
                    .collect(Collectors.groupingBy(RegistoHoras::getProfessor));

            porProfessor.forEach((professor, lista) -> {
                double total = lista.stream().mapToDouble(this::calcularHoras).sum();
                double regulares = lista.stream().filter(r -> "Aula regular".equalsIgnoreCase(r.getTipoAtividade()))
                        .mapToDouble(this::calcularHoras).sum();
                double ensaios = lista.stream().filter(r -> "Ensaio".equalsIgnoreCase(r.getTipoAtividade()))
                        .mapToDouble(this::calcularHoras).sum();
                double privadas = lista.stream().filter(r -> "Aula privada".equalsIgnoreCase(r.getTipoAtividade()))
                        .mapToDouble(this::calcularHoras).sum();
                double euros = pagamentoPorNome.getOrDefault(professor, 0.0);

                corpo.append("<tr>")
                        .append("<td><b>").append(professor).append("</b></td>")
                        .append("<td>").append(String.format("%.2f", total)).append("h</td>")
                        .append("<td>").append(String.format("%.2f", regulares)).append("h</td>")
                        .append("<td>").append(String.format("%.2f", ensaios)).append("h</td>")
                        .append("<td>").append(String.format("%.2f", privadas)).append("h</td>")
                        .append("<td><b>").append(String.format("%.2f", euros)).append(" €</b></td>")
                        .append("</tr>");
            });

            corpo.append("</table>");

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
