package pt.studioflow.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Aula;
import pt.studioflow.model.CompraCredito;
import pt.studioflow.model.MetodoRegistoPresenca;
import pt.studioflow.model.OrigemPresenca;
import pt.studioflow.model.Presenca;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.CompraCreditoRepository;
import pt.studioflow.repository.PresencaRepository;
import pt.studioflow.repository.TurmaRepository;

/**
 * Checkin automático (aluno no portal, ou perfil PRESENÇA no kiosk): calcula
 * quais turmas estão "a acontecer agora" dentro da janela configurada por
 * estúdio, e regista a presença — consumindo um crédito de {@link CompraCredito}
 * quando o aluno não é matriculado na turma.
 */
@Service
public class CheckinService {

    private final TurmaRepository turmaRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final PresencaRepository presencaRepository;
    private final CompraCreditoRepository compraCreditoRepository;
    private final EmailService emailService;

    public CheckinService(TurmaRepository turmaRepository, AlunoTurmaRepository alunoTurmaRepository,
            PresencaRepository presencaRepository, CompraCreditoRepository compraCreditoRepository,
            EmailService emailService) {
        this.turmaRepository = turmaRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.presencaRepository = presencaRepository;
        this.compraCreditoRepository = compraCreditoRepository;
        this.emailService = emailService;
    }

    public static class TurmaCheckin {
        public final Turma turma;
        public final boolean matriculado;
        public final CompraCredito creditoElegivel;
        public final boolean jaFezCheckin;

        public TurmaCheckin(Turma turma, boolean matriculado, CompraCredito creditoElegivel, boolean jaFezCheckin) {
            this.turma = turma;
            this.matriculado = matriculado;
            this.creditoElegivel = creditoElegivel;
            this.jaFezCheckin = jaFezCheckin;
        }
    }

    /**
     * Turmas do estúdio do aluno cuja aula de hoje está dentro da janela de checkin e para
     * as quais o aluno é elegível. Recebe o {@code studio} explícito em vez de o ir buscar a
     * {@code aluno.getStudio()} — essa relação é LAZY e o aluno chega aqui vindo de uma
     * consulta já fora da sessão Hibernate original (ex: portal do aluno), o que rebentava
     * com LazyInitializationException.
     */
    public List<TurmaCheckin> listarTurmasParaCheckin(Aluno aluno, Studio studio, LocalDateTime agora) {
        int antes = studio.getCheckinJanelaAntesMin();
        int depois = studio.getCheckinJanelaDepoisMin();
        DayOfWeek hoje = agora.getDayOfWeek();
        LocalDate data = agora.toLocalDate();

        List<AlunoTurma> matriculas = alunoTurmaRepository.findByAluno(aluno);
        Set<Long> turmaIdsMatriculado = matriculas.stream()
                .map(at -> at.getTurma().getId()).collect(Collectors.toSet());
        List<CompraCredito> creditos = compraCreditoRepository.findCreditosValidosDoAluno(aluno);

        List<TurmaCheckin> resultado = new ArrayList<>();
        for (Turma turma : turmaRepository.findByStudioAndAtivoTrue(studio)) {
            if (turma.getAulas() == null) continue;
            boolean dentroDaJanelaHoje = turma.getAulas().stream().anyMatch(aula -> dentroDaJanela(aula, hoje, data, agora, antes, depois));
            if (!dentroDaJanelaHoje) continue;

            boolean matriculado = turmaIdsMatriculado.contains(turma.getId());
            CompraCredito creditoElegivel = matriculado ? null
                    : creditos.stream().filter(c -> c.isElegivelParaTurma(turma)).findFirst().orElse(null);
            if (!matriculado && creditoElegivel == null) continue; // sem matrícula nem crédito válido: não elegível

            boolean jaFez = presencaRepository.findByAlunoAndTurmaAndData(aluno, turma, data)
                    .map(Presenca::isPresente).orElse(false);
            resultado.add(new TurmaCheckin(turma, matriculado, creditoElegivel, jaFez));
        }
        return resultado;
    }

    /** Turmas ativas do estúdio cuja aula de hoje está dentro da janela de checkin agora (uso no kiosk PRESENÇA). */
    public List<Turma> listarTurmasNaJanelaAgora(Studio studio, LocalDateTime agora) {
        int antes = studio.getCheckinJanelaAntesMin();
        int depois = studio.getCheckinJanelaDepoisMin();
        DayOfWeek hoje = agora.getDayOfWeek();
        LocalDate data = agora.toLocalDate();
        return turmaRepository.findByStudioAndAtivoTrue(studio).stream()
                .filter(turma -> turma.getAulas() != null && turma.getAulas().stream()
                        .anyMatch(aula -> dentroDaJanela(aula, hoje, data, agora, antes, depois)))
                .collect(Collectors.toList());
    }

    /** Alunos não matriculados na turma mas com crédito avulso/pack válido e elegível para ela (uso no kiosk PRESENÇA). */
    public List<Aluno> listarAvulsosElegiveisParaTurma(Turma turma) {
        return compraCreditoRepository.findByStudio(turma.getStudio()).stream()
                .filter(c -> c.isElegivelParaTurma(turma))
                .map(CompraCredito::getAluno)
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean dentroDaJanela(Aula aula, DayOfWeek hoje, LocalDate data, LocalDateTime agora, int antesMin, int depoisMin) {
        if (aula.getDia() != hoje || aula.getHoraInicio() == null) return false;
        if (aula.getDataInicio() != null && data.isBefore(aula.getDataInicio())) return false;
        if (aula.getDataFim() != null && data.isAfter(aula.getDataFim())) return false;
        LocalDateTime inicio = LocalDateTime.of(data, aula.getHoraInicio());
        return !agora.isBefore(inicio.minusMinutes(antesMin)) && !agora.isAfter(inicio.plusMinutes(depoisMin));
    }

    /** Regista o checkin, revalidando a janela/elegibilidade no servidor. Idempotente por (aluno, turma, data). */
    @Transactional
    public Presenca registarCheckin(Aluno aluno, Turma turma, Studio studio, MetodoRegistoPresenca metodo) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDate data = agora.toLocalDate();

        TurmaCheckin alvo = listarTurmasParaCheckin(aluno, studio, agora).stream()
                .filter(tc -> tc.turma.getId().equals(turma.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Fora da janela de checkin ou sem elegibilidade (matrícula/crédito) para esta turma."));
        if (alvo.jaFezCheckin) {
            throw new IllegalStateException("Já foi registada presença para esta aula hoje.");
        }

        Presenca presenca = presencaRepository.findByAlunoAndTurmaAndData(aluno, turma, data).orElseGet(Presenca::new);
        presenca.setAluno(aluno);
        presenca.setTurma(turma);
        presenca.setData(data);
        presenca.setPresente(true);
        presenca.setHoraRegisto(agora);
        presenca.setMetodoRegisto(metodo);

        if (alvo.matriculado) {
            presenca.setOrigem(OrigemPresenca.MATRICULA);
        } else {
            CompraCredito credito = alvo.creditoElegivel;
            credito.setCreditosRestantes(credito.getCreditosRestantes() - 1);
            compraCreditoRepository.save(credito);
            presenca.setOrigem(OrigemPresenca.CREDITO_AVULSO);
            presenca.setCompraCredito(credito);

            if (turma.getProfessor() != null) {
                emailService.enviarEmailNotificacaoCheckinAvulso(turma.getProfessor().getEmail(), aluno, turma);
            }
        }

        return presencaRepository.save(presenca);
    }

    /** Desfaz um checkin (kiosk/portal, correção de engano) — devolve o crédito consumido, se algum. */
    @Transactional
    public void cancelarCheckin(Presenca presenca) {
        if (presenca.getCompraCredito() != null) {
            CompraCredito credito = presenca.getCompraCredito();
            credito.setCreditosRestantes(credito.getCreditosRestantes() + 1);
            compraCreditoRepository.save(credito);
            presenca.setCompraCredito(null);
        }
        presenca.setPresente(false);
        presencaRepository.save(presenca);
    }
}
