package pt.studioflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Presenca;
import pt.studioflow.model.Turma;

/**
 * "Alunos em risco" = alunos que frequentam o estúdio e deixaram de vir, não
 * ex-alunos que ficaram com a flag ativo ligada.
 */
class AlunosEmRiscoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 18);
    private static final Turma TURMA = turma(true);

    private final List<AlunoTurma> matriculas = new ArrayList<>();
    private final List<Presenca> presencas = new ArrayList<>();

    private static Turma turma(boolean ativa) {
        Turma t = new Turma();
        t.setAtivo(ativa);
        return t;
    }

    private static Aluno aluno(long id, Aluno.AlunoStatus status, boolean ativo) {
        Aluno a = new Aluno();
        a.setId(id);
        a.setNomeCompleto("Aluno " + id);
        a.setStatus(status);
        a.setAtivo(ativo);
        return a;
    }

    private void matricular(Aluno a, Turma t, boolean pendente) {
        AlunoTurma at = new AlunoTurma();
        at.setAluno(a);
        at.setTurma(t);
        at.setPendente(pendente);
        matriculas.add(at);
    }

    private void presenca(Aluno a, LocalDate data, boolean presente) {
        Presenca p = new Presenca();
        p.setAluno(a);
        p.setData(data);
        p.setPresente(presente);
        presencas.add(p);
    }

    private List<Aluno> calcular(Aluno... alunos) {
        return AlunosEmRisco.calcular(List.of(alunos), matriculas, presencas, HOJE,
                AlunosEmRisco.DIAS_SEM_PRESENCA);
    }

    @Test
    void alunoAtivoSemPresencasRecentes_estaEmRisco() {
        Aluno a = aluno(1, Aluno.AlunoStatus.ATIVO, true);
        matricular(a, TURMA, false);
        presenca(a, HOJE.minusDays(20), true);
        assertThat(calcular(a)).containsExactly(a);
    }

    @Test
    void alunoQueVeioRecentemente_naoEstaEmRisco() {
        Aluno a = aluno(1, Aluno.AlunoStatus.ATIVO, true);
        matricular(a, TURMA, false);
        presenca(a, HOJE.minusDays(3), true);
        assertThat(calcular(a)).isEmpty();
    }

    @Test
    void faltasRecentesNaoContamComoPresenca() {
        Aluno a = aluno(1, Aluno.AlunoStatus.ATIVO, true);
        matricular(a, TURMA, false);
        presenca(a, HOJE.minusDays(30), true);
        presenca(a, HOJE.minusDays(2), false);
        assertThat(calcular(a)).containsExactly(a);
    }

    @Test
    void exAlunoComEstadoInativoMasFlagLigada_naoConta() {
        Aluno a = aluno(1, Aluno.AlunoStatus.INATIVO, true);
        matricular(a, TURMA, false);
        assertThat(calcular(a)).isEmpty();
    }

    @Test
    void flagDesligada_naoConta() {
        Aluno a = aluno(1, Aluno.AlunoStatus.ATIVO, false);
        matricular(a, TURMA, false);
        assertThat(calcular(a)).isEmpty();
    }

    @Test
    void semTurma_ouSoMatriculaPendente_ouTurmaInativa_naoConta() {
        Aluno semTurma = aluno(1, Aluno.AlunoStatus.ATIVO, true);
        Aluno pendente = aluno(2, Aluno.AlunoStatus.ATIVO, true);
        matricular(pendente, TURMA, true);
        Aluno turmaFechada = aluno(3, Aluno.AlunoStatus.ATIVO, true);
        matricular(turmaFechada, turma(false), false);
        assertThat(calcular(semTurma, pendente, turmaFechada)).isEmpty();
    }

    @Test
    void experimentaisEPendentes_naoContam() {
        Aluno exp = aluno(1, Aluno.AlunoStatus.EXPERIMENTAL, true);
        Aluno pen = aluno(2, Aluno.AlunoStatus.PENDENTE, true);
        matricular(exp, TURMA, false);
        matricular(pen, TURMA, false);
        assertThat(calcular(exp, pen)).isEmpty();
    }

    @Test
    void nuncaVeio_soContaSeInscritoHaMaisDeNDias() {
        Aluno novo = aluno(1, Aluno.AlunoStatus.ATIVO, true);
        novo.setCarimboDataHora(HOJE.minusDays(5));
        Aluno antigo = aluno(2, Aluno.AlunoStatus.ATIVO, true);
        antigo.setCarimboDataHora(HOJE.minusDays(40));
        matricular(novo, TURMA, false);
        matricular(antigo, TURMA, false);
        assertThat(calcular(novo, antigo)).containsExactly(antigo);
    }
}
