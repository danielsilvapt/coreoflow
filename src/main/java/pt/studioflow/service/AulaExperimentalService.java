package pt.studioflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Aula;
import pt.studioflow.model.AulaExperimental;
import pt.studioflow.model.InterrupcaoLetiva;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.AulaExperimentalRepository;
import pt.studioflow.repository.AulaRepository;
import pt.studioflow.repository.InterrupcaoLetivaRepository;
import pt.studioflow.repository.TurmaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Aulas experimentais: sessões disponíveis (a partir do horário das turmas),
 * marcação, mudança de estado e consultas para o dashboard/menu.
 */
@Service
public class AulaExperimentalService {

    /** Até quantos dias à frente se oferecem sessões para marcar. */
    public static final int DIAS_A_FRENTE = 35;

    private static final Locale PT = Locale.forLanguageTag("pt-PT");
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("d MMM", PT);

    private final AulaExperimentalRepository repository;
    private final AulaRepository aulaRepository;
    private final TurmaRepository turmaRepository;
    private final AlunoRepository alunoRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final InterrupcaoLetivaRepository interrupcaoRepository;
    private final EmailService emailService;

    public AulaExperimentalService(AulaExperimentalRepository repository, AulaRepository aulaRepository,
            TurmaRepository turmaRepository, AlunoRepository alunoRepository,
            AlunoTurmaRepository alunoTurmaRepository, InterrupcaoLetivaRepository interrupcaoRepository,
            EmailService emailService) {
        this.repository = repository;
        this.aulaRepository = aulaRepository;
        this.turmaRepository = turmaRepository;
        this.alunoRepository = alunoRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.interrupcaoRepository = interrupcaoRepository;
        this.emailService = emailService;
    }

    /** Uma sessão concreta de uma turma (dia + hora), candidata a aula experimental. */
    public record Sessao(Turma turma, LocalDate data, LocalTime horaInicio, LocalTime horaFim) {
        public String label() {
            String dia = data.getDayOfWeek().getDisplayName(TextStyle.SHORT, PT);
            String hoje = data.equals(LocalDate.now()) ? "hoje, "
                    : data.equals(LocalDate.now().plusDays(1)) ? "amanhã, " : "";
            return hoje + dia + " " + data.format(DATA_CURTA)
                    + (horaInicio != null ? " · " + horaInicio + (horaFim != null ? "–" + horaFim : "") : "");
        }
    }

    /** Dados de uma nova marcação. */
    public record Pedido(String nome, String telemovel, String email, Sessao sessao,
            AulaExperimental.Origem origem, String observacoes) {
    }

    /** Turmas ativas do estúdio que têm pelo menos uma sessão marcável. */
    @Transactional(readOnly = true)
    public List<Turma> turmasComSessoes(Studio studio) {
        List<Turma> turmas = new ArrayList<>(turmaRepository.findAllByStudio(studio).stream()
                .filter(Turma::isAtivo)
                .filter(t -> !sessoesDisponiveis(t).isEmpty())
                .toList());
        turmas.sort(Comparator.comparing(t -> t.getDescricao() != null ? t.getDescricao() : ""));
        return turmas;
    }

    /**
     * Próximas sessões regulares da turma (sem ensaios, dentro do período da aula e
     * fora das interrupções letivas). Sessões de hoje que já começaram não contam.
     */
    @Transactional(readOnly = true)
    public List<Sessao> sessoesDisponiveis(Turma turma) {
        LocalDate hoje = LocalDate.now();
        LocalDateTime agora = LocalDateTime.now();
        List<Aula> aulas = aulaRepository.findByTurma(turma).stream()
                .filter(a -> a.getDia() != null && a.getHoraInicio() != null)
                .filter(a -> !"ENSAIO".equalsIgnoreCase(a.getTipo()))
                .toList();
        if (aulas.isEmpty()) return List.of();
        List<InterrupcaoLetiva> interrupcoes = turma.getStudio() != null
                ? interrupcaoRepository.findByStudioOrderByDataInicioAsc(turma.getStudio()) : List.of();

        List<Sessao> out = new ArrayList<>();
        for (int i = 0; i <= DIAS_A_FRENTE; i++) {
            LocalDate d = hoje.plusDays(i);
            if (interrupcoes.stream().anyMatch(it -> it.contem(d))) continue;
            for (Aula a : aulas) {
                if (a.getDia() != d.getDayOfWeek()) continue;
                if (a.getDataInicio() != null && d.isBefore(a.getDataInicio())) continue;
                if (a.getDataFim() != null && d.isAfter(a.getDataFim())) continue;
                if (d.atTime(a.getHoraInicio()).isBefore(agora)) continue;
                out.add(new Sessao(turma, d, a.getHoraInicio(), a.getHoraFim()));
            }
        }
        out.sort(Comparator.comparing(Sessao::data).thenComparing(Sessao::horaInicio));
        return out.stream().distinct().toList();
    }

    /**
     * Regista a marcação. Cria (ou reutiliza, pelo telemóvel/email) o aluno com estado
     * EXPERIMENTAL e inscreve-o na turma sem mensalidade, para aparecer nas presenças.
     */
    @Transactional
    public AulaExperimental marcar(Studio studio, Pedido p) {
        if (p.nome() == null || p.nome().isBlank()) throw new IllegalArgumentException("Indica o nome.");
        if ((p.telemovel() == null || p.telemovel().isBlank()) && (p.email() == null || p.email().isBlank()))
            throw new IllegalArgumentException("Indica um telemóvel ou email de contacto.");
        if (p.sessao() == null) throw new IllegalArgumentException("Escolhe a turma e a sessão.");

        Turma turma = p.sessao().turma();
        String tel = limpar(p.telemovel());
        String email = limpar(p.email());

        if (tel != null && repository.existsByTurmaAndDataAndTelemovelAndEstado(turma, p.sessao().data(), tel,
                AulaExperimental.Estado.MARCADA)) {
            throw new IllegalArgumentException("Já existe uma aula experimental marcada para este contacto nesta sessão.");
        }

        Aluno aluno = procurarAluno(studio, tel, email).orElse(null);
        if (aluno == null) {
            aluno = new Aluno();
            aluno.setNomeCompleto(p.nome().trim());
            aluno.setTelemovel(tel);
            aluno.setEmail(email);
            aluno.setStatus(Aluno.AlunoStatus.EXPERIMENTAL);
            aluno.setAtivo(false);
            aluno.setCarimboDataHora(LocalDate.now());
            aluno.setStudio(studio);
            aluno = alunoRepository.save(aluno);
        }
        // Alunos já ativos noutra turma não ficam inscritos nesta (só a marcação).
        if (aluno.getStatus() == Aluno.AlunoStatus.EXPERIMENTAL
                && !alunoTurmaRepository.existsByAlunoAndTurma(aluno, turma)) {
            AlunoTurma at = new AlunoTurma();
            at.setAluno(aluno);
            at.setTurma(turma);
            at.setAulasPorSemana(1);
            at.setSemMensalidade(true);
            alunoTurmaRepository.save(at);
        }

        AulaExperimental ae = new AulaExperimental();
        ae.setStudio(studio);
        ae.setTurma(turma);
        ae.setAluno(aluno);
        ae.setData(p.sessao().data());
        ae.setHoraInicio(p.sessao().horaInicio());
        ae.setHoraFim(p.sessao().horaFim());
        ae.setNomeCompleto(p.nome().trim());
        ae.setTelemovel(tel);
        ae.setEmail(email);
        ae.setOrigem(p.origem() != null ? p.origem() : AulaExperimental.Origem.OUTRO);
        ae.setObservacoes(limpar(p.observacoes()));
        ae = repository.save(ae);

        avisarProfessores(ae);

        // Marcações online avisam o estúdio por email (as registadas pela secretaria não).
        if (ae.getOrigem() == AulaExperimental.Origem.ONLINE) {
            emailService.enviarEmailAulaExperimentalMarcada(ae);
        }

        return ae;
    }

    @Transactional
    public AulaExperimental mudarEstado(AulaExperimental ae, AulaExperimental.Estado estado) {
        ae.setEstado(estado);
        // "Inscreveu-se": o candidato passa a pendente, para completar a ficha em "Validar Inscrições".
        if (estado == AulaExperimental.Estado.INSCRITO && ae.getAluno() != null
                && ae.getAluno().getStatus() == Aluno.AlunoStatus.EXPERIMENTAL) {
            Aluno a = ae.getAluno();
            a.setStatus(Aluno.AlunoStatus.PENDENTE);
            alunoRepository.save(a);
        }
        return repository.save(ae);
    }

    @Transactional(readOnly = true)
    public List<AulaExperimental> listar(Studio studio) {
        return repository.findByStudioOrderByDataDescHoraInicioDesc(studio);
    }

    /** Marcações (ainda por realizar) de hoje, opcionalmente só nas turmas indicadas. */
    @Transactional(readOnly = true)
    public List<AulaExperimental> deHoje(Studio studio, Set<Long> turmaIds) {
        return repository.findByStudioAndDataOrderByHoraInicioAsc(studio, LocalDate.now()).stream()
                .filter(a -> a.getEstado() == AulaExperimental.Estado.MARCADA)
                .filter(a -> turmaIds == null || turmaIds.contains(a.getTurma().getId()))
                .toList();
    }

    /**
     * Marcações da semana atual (segunda a domingo), sem as canceladas, opcionalmente
     * só nas turmas indicadas.
     */
    @Transactional(readOnly = true)
    public List<AulaExperimental> daSemana(Studio studio, Set<Long> turmaIds) {
        LocalDate seg = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        return repository.findByStudioAndDataBetweenOrderByDataAscHoraInicioAsc(studio, seg, seg.plusDays(6)).stream()
                .filter(a -> a.getEstado() != AulaExperimental.Estado.CANCELADA)
                .filter(a -> turmaIds == null || turmaIds.contains(a.getTurma().getId()))
                .toList();
    }

    /** Nº de marcações por realizar (hoje e futuras) — badge do menu. */
    @Transactional(readOnly = true)
    public long porRealizar(Studio studio) {
        return repository.countByStudioAndEstadoAndDataGreaterThanEqual(studio, AulaExperimental.Estado.MARCADA,
                LocalDate.now());
    }

    private Optional<Aluno> procurarAluno(Studio studio, String tel, String email) {
        String telDigitos = tel != null ? tel.replaceAll("\\D", "") : null;
        return alunoRepository.findAllByStudio(studio).stream()
                .filter(a -> (telDigitos != null && telDigitos.length() >= 9 && a.getTelemovel() != null
                        && a.getTelemovel().replaceAll("\\D", "").endsWith(telDigitos.substring(telDigitos.length() - 9)))
                        || (email != null && email.equalsIgnoreCase(a.getEmail())))
                .findFirst();
    }

    /**
     * Avisa por email o(s) professor(es) da sessão: o da aula (ou o principal da turma)
     * e os co-professores, com os dados do interessado e da aula.
     */
    private void avisarProfessores(AulaExperimental ae) {
        Aula aula = aulaRepository.findByTurma(ae.getTurma()).stream()
                .filter(a -> a.getDia() == ae.getData().getDayOfWeek() && a.getHoraInicio() != null
                        && a.getHoraInicio().equals(ae.getHoraInicio()))
                .findFirst().orElse(null);
        java.util.Map<String, String> destinos = new java.util.LinkedHashMap<>(); // email -> nome
        pt.studioflow.model.Professor principal = aula != null ? aula.getProfessorEfetivo() : ae.getTurma().getProfessor();
        java.util.List<pt.studioflow.model.Professor> profs = new java.util.ArrayList<>();
        if (principal != null) profs.add(principal);
        if (ae.getTurma().getCoProfessores() != null) profs.addAll(ae.getTurma().getCoProfessores());
        for (pt.studioflow.model.Professor pr : profs) {
            if (pr.getEmail() != null && !pr.getEmail().isBlank()) {
                destinos.putIfAbsent(pr.getEmail().trim().toLowerCase(), pr.getNome());
            }
        }
        pt.studioflow.model.Sala sala = aula != null && aula.getSala() != null ? aula.getSala() : ae.getTurma().getSala();
        if (destinos.isEmpty()) return;

        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT);
        String dia = ae.getData().format(f);
        String horas = ae.getHoraInicio() != null
                ? ae.getHoraInicio() + (ae.getHoraFim() != null ? "–" + ae.getHoraFim() : "") : "";
        String assunto = "Aula experimental: " + ae.getNomeCompleto() + " — " + ae.getTurma().getDescricao()
                + " (" + ae.getData().format(DateTimeFormatter.ofPattern("dd/MM")) + ")";
        StringBuilder linhas = new StringBuilder();
        linha(linhas, "Turma", ae.getTurma().getDescricao());
        linha(linhas, "Dia", Character.toUpperCase(dia.charAt(0)) + dia.substring(1));
        linha(linhas, "Hora", horas);
        if (sala != null) linha(linhas, "Sala", sala.getNome());
        linha(linhas, "Nome", ae.getNomeCompleto());
        linha(linhas, "Telemóvel", ae.getTelemovel());
        linha(linhas, "Email", ae.getEmail());
        linha(linhas, "Observações", ae.getObservacoes());
        linha(linhas, "Marcada por", ae.getOrigem().getLabel());

        for (java.util.Map.Entry<String, String> d : destinos.entrySet()) {
            String nomeProf = d.getValue() != null ? d.getValue().split(" ")[0] : "";
            String html = "<p>Olá " + esc(nomeProf) + ",</p>"
                    + "<p>Foi marcada uma <b>aula experimental</b> numa das tuas turmas:</p>"
                    + "<table style='border-collapse:collapse;background:#f6f3fa;border-left:4px solid #8E44AD;'>"
                    + linhas + "</table>"
                    + "<p>A pessoa já aparece nas presenças dessa aula. Bom trabalho!</p>";
            emailService.enviarEmailAulaExperimentalProfessor(ae.getStudio(), d.getKey(), assunto, html);
        }
    }

    private static void linha(StringBuilder sb, String rotulo, String valor) {
        if (valor == null || valor.isBlank()) return;
        sb.append("<tr><td style='padding:6px 12px;color:#666;'>").append(rotulo)
                .append("</td><td style='padding:6px 12px;'><b>").append(esc(valor)).append("</b></td></tr>");
    }

    private static String esc(String s) {
        return s == null ? "" : org.springframework.web.util.HtmlUtils.htmlEscape(s);
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
