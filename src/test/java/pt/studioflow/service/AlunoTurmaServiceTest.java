package pt.studioflow.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import pt.studioflow.model.*;
import pt.studioflow.repository.*;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão do bug em que remover um aluno de uma turma não apagava as
 * mensalidades futuras ainda por emitir (o aluno continuava a ser cobrado por
 * uma turma de que já tinha saído), preservando sempre o histórico já faturado.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AlunoTurmaService.class)
class AlunoTurmaServiceTest {

    @Autowired private StudioRepository studioRepository;
    @Autowired private ModalidadeRepository modalidadeRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private AlunoRepository alunoRepository;
    @Autowired private AlunoTurmaRepository alunoTurmaRepository;
    @Autowired private MensalidadeRepository mensalidadeRepository;
    @Autowired private AlunoTurmaService alunoTurmaService;

    @Test
    void remover_apagaMensalidadesFuturasPorEmitir_masPreservaPagas() {
        Studio studio = new Studio();
        studio.setNome("Studio Remocao");
        studio.setSlug("studio-remocao");
        studio.setAtivo(true);
        studio = studioRepository.save(studio);

        Modalidade modalidade = new Modalidade();
        modalidade.setDescricao("Jazz");
        modalidade.setStudio(studio);
        modalidade = modalidadeRepository.save(modalidade);

        Turma turma = new Turma();
        turma.setCodigo("REM-1");
        turma.setDescricao("Turma Remocao");
        turma.setModalidade(modalidade);
        turma.setStudio(studio);
        turma.setAtivo(true);
        turma = turmaRepository.save(turma);

        Aluno aluno = new Aluno();
        aluno.setNomeCompleto("Aluno Remocao");
        aluno.setEmail("remocao@test.com");
        aluno.setStudio(studio);
        aluno = alunoRepository.save(aluno);

        AlunoTurma at = new AlunoTurma();
        at.setAluno(aluno);
        at.setTurma(turma);
        alunoTurmaRepository.save(at);

        YearMonth mesAtual = YearMonth.now();
        Month mesFuturo = mesAtual.getMonth().plus(1);
        int anoFuturo = mesAtual.getMonthValue() == 12 ? mesAtual.getYear() + 1 : mesAtual.getYear();

        Mensalidade futuraPorEmitir = novaMensalidade(aluno, turma, studio, anoFuturo, mesFuturo, EstadoMensalidade.POR_EMITIR);
        mensalidadeRepository.save(futuraPorEmitir);

        Mensalidade futuraPaga = novaMensalidade(aluno, turma, studio, anoFuturo, mesFuturo.plus(1), EstadoMensalidade.PAGO);
        mensalidadeRepository.save(futuraPaga);

        Mensalidade mesAtualMensalidade = novaMensalidade(aluno, turma, studio, mesAtual.getYear(), mesAtual.getMonth(), EstadoMensalidade.FATURADO);
        mensalidadeRepository.save(mesAtualMensalidade);

        alunoTurmaService.remover(aluno, turma);

        assertThat(alunoTurmaRepository.existsByAlunoAndTurma(aluno, turma)).isFalse();

        java.util.List<Mensalidade> restantes = mensalidadeRepository.findByAluno(aluno);
        assertThat(restantes).extracting(Mensalidade::getEstado)
                .containsExactlyInAnyOrder(EstadoMensalidade.PAGO, EstadoMensalidade.FATURADO);
    }

    private Mensalidade novaMensalidade(Aluno aluno, Turma turma, Studio studio, int ano, Month mes, EstadoMensalidade estado) {
        Mensalidade m = new Mensalidade();
        m.setAluno(aluno);
        m.setTurma(turma);
        m.setStudio(studio);
        m.setAno(ano);
        m.setMes(mes);
        m.setEstado(estado);
        m.setValor(30.0);
        return m;
    }
}
