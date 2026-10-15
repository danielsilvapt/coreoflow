package pt.studioflow.service;

import org.junit.jupiter.api.Test;

import pt.studioflow.model.Aula;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A frequência por omissão na alocação de alunos vem do mapa de salas da turma,
 * em vez de ser sempre 2x por semana.
 */
class AlunoTurmaFrequenciaTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 16);

    private static Aula aula(DayOfWeek dia, String tipo, LocalDate dataFim) {
        Aula a = new Aula();
        a.setDia(dia);
        a.setTipo(tipo);
        a.setDataFim(dataFim);
        return a;
    }

    @Test
    void umDiaNoMapa_e1x() {
        assertThat(AlunoTurmaService.aulasPorSemana(
                List.of(aula(DayOfWeek.MONDAY, "NORMAL", null)), HOJE)).isEqualTo(1);
    }

    @Test
    void doisDiasNoMapa_e2x() {
        assertThat(AlunoTurmaService.aulasPorSemana(List.of(
                aula(DayOfWeek.MONDAY, "NORMAL", null),
                aula(DayOfWeek.THURSDAY, null, null)), HOJE)).isEqualTo(2);
    }

    @Test
    void duasAulasNoMesmoDia_contamComoUmDia() {
        assertThat(AlunoTurmaService.aulasPorSemana(List.of(
                aula(DayOfWeek.MONDAY, "NORMAL", null),
                aula(DayOfWeek.MONDAY, "NORMAL", null)), HOJE)).isEqualTo(1);
    }

    @Test
    void ensaiosEAulasTerminadas_naoContam() {
        assertThat(AlunoTurmaService.aulasPorSemana(List.of(
                aula(DayOfWeek.MONDAY, "NORMAL", null),
                aula(DayOfWeek.SATURDAY, "ENSAIO", null),
                aula(DayOfWeek.WEDNESDAY, "NORMAL", HOJE.minusDays(1))), HOJE)).isEqualTo(1);
    }

    @Test
    void tresDias_ficaNo2x() {
        assertThat(AlunoTurmaService.aulasPorSemana(List.of(
                aula(DayOfWeek.MONDAY, "NORMAL", null),
                aula(DayOfWeek.WEDNESDAY, "NORMAL", null),
                aula(DayOfWeek.FRIDAY, "NORMAL", null)), HOJE)).isEqualTo(2);
    }

    @Test
    void semHorario_mantem2x() {
        assertThat(AlunoTurmaService.aulasPorSemana(List.of(), HOJE)).isEqualTo(2);
    }
}
