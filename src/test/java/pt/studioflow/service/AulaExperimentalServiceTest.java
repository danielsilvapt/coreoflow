package pt.studioflow.service;

import org.junit.jupiter.api.Test;
import pt.studioflow.model.Aula;
import pt.studioflow.model.InterrupcaoLetiva;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AulaExperimentalServiceTest {

    private final AulaRepository aulaRepository = mock(AulaRepository.class);
    private final InterrupcaoLetivaRepository interrupcaoRepository = mock(InterrupcaoLetivaRepository.class);
    private final AulaExperimentalService service = new AulaExperimentalService(
            mock(AulaExperimentalRepository.class), aulaRepository, mock(TurmaRepository.class),
            mock(AlunoRepository.class), mock(AlunoTurmaRepository.class), interrupcaoRepository,
            mock(EmailService.class));

    private Aula aula(Turma t, java.time.DayOfWeek dia, String tipo) {
        Aula a = new Aula();
        a.setTurma(t);
        a.setDia(dia);
        a.setHoraInicio(LocalTime.of(23, 59));
        a.setTipo(tipo);
        return a;
    }

    @Test
    void sessoesSemanaisSemEnsaiosNemInterrupcoes() {
        Studio studio = new Studio();
        Turma turma = new Turma();
        turma.setStudio(studio);
        LocalDate amanha = LocalDate.now().plusDays(1);
        when(aulaRepository.findByTurma(turma)).thenReturn(List.of(
                aula(turma, amanha.getDayOfWeek(), "NORMAL"),
                aula(turma, amanha.plusDays(1).getDayOfWeek(), "ENSAIO")));
        // a primeira semana fica em interrupção letiva
        when(interrupcaoRepository.findByStudioOrderByDataInicioAsc(any())).thenReturn(List.of(
                new InterrupcaoLetiva(studio, "Férias", amanha, amanha)));

        List<AulaExperimentalService.Sessao> sessoes = service.sessoesDisponiveis(turma);

        assertThat(sessoes).isNotEmpty();
        assertThat(sessoes).allSatisfy(s -> assertThat(s.data().getDayOfWeek()).isEqualTo(amanha.getDayOfWeek()));
        assertThat(sessoes).noneSatisfy(s -> assertThat(s.data()).isEqualTo(amanha));
        assertThat(sessoes.get(0).data()).isEqualTo(amanha.plusWeeks(1));
        assertThat(sessoes).hasSizeLessThanOrEqualTo(AulaExperimentalService.DIAS_A_FRENTE / 7 + 1);
    }

    @Test
    void turmaSemHorarioNaoTemSessoes() {
        Turma turma = new Turma();
        when(aulaRepository.findByTurma(turma)).thenReturn(List.of());
        assertThat(service.sessoesDisponiveis(turma)).isEmpty();
    }
}
