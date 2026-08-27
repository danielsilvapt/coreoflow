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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regressão do bug em que eliminar uma Turma com histórico (mensalidades,
 * presenças, avaliações, lista de espera, ocorrências) rebentava com violação
 * de FK, porque só AlunoTurma/Aula tinham cascade a partir de Turma.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TurmaService.class)
class TurmaServiceDeleteTest {

    @Autowired private StudioRepository studioRepository;
    @Autowired private ModalidadeRepository modalidadeRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private AlunoRepository alunoRepository;
    @Autowired private MensalidadeRepository mensalidadeRepository;
    @Autowired private PresencaRepository presencaRepository;
    @Autowired private AvaliacaoAlunoRepository avaliacaoAlunoRepository;
    @Autowired private ListaEsperaRepository listaEsperaRepository;
    @Autowired private OcorrenciaAulaRepository ocorrenciaAulaRepository;
    @Autowired private TurmaService turmaService;

    @Test
    void deleteTurmaComHistoricoCompleto_naoRebentaELimpaTudo() {
        Studio studio = new Studio();
        studio.setNome("Studio Delete");
        studio.setSlug("studio-delete");
        studio.setAtivo(true);
        studio = studioRepository.save(studio);

        Modalidade modalidade = new Modalidade();
        modalidade.setDescricao("Ballet");
        modalidade.setStudio(studio);
        modalidade = modalidadeRepository.save(modalidade);

        Turma turma = new Turma();
        turma.setCodigo("DEL-1");
        turma.setDescricao("Turma a eliminar");
        turma.setModalidade(modalidade);
        turma.setStudio(studio);
        turma.setAtivo(true);
        turma = turmaRepository.save(turma);

        Aluno aluno = new Aluno();
        aluno.setNomeCompleto("Aluno Delete");
        aluno.setEmail("delete@test.com");
        aluno.setStudio(studio);
        aluno = alunoRepository.save(aluno);

        Mensalidade mensalidade = new Mensalidade();
        mensalidade.setAluno(aluno);
        mensalidade.setTurma(turma);
        mensalidade.setStudio(studio);
        mensalidade.setAno(2026);
        mensalidade.setMes(Month.SEPTEMBER);
        mensalidade.setEstado(EstadoMensalidade.POR_EMITIR);
        mensalidadeRepository.save(mensalidade);

        Presenca presenca = new Presenca();
        presenca.setAluno(aluno);
        presenca.setTurma(turma);
        presenca.setData(LocalDate.now());
        presenca.setPresente(true);
        presencaRepository.save(presenca);

        AvaliacaoAluno avaliacao = new AvaliacaoAluno();
        avaliacao.setAluno(aluno);
        avaliacao.setTurma(turma);
        avaliacao.setStudio(studio);
        avaliacao.setPeriodo("2026/2027 · 1º Período");
        avaliacaoAlunoRepository.save(avaliacao);

        ListaEspera espera = new ListaEspera();
        espera.setTurma(turma);
        espera.setNomeCompleto("Candidato Espera");
        espera.setStudio(studio);
        listaEsperaRepository.save(espera);

        OcorrenciaAula ocorrencia = new OcorrenciaAula();
        ocorrencia.setTurma(turma);
        ocorrencia.setData(LocalDate.now());
        ocorrencia.setTipo(OcorrenciaAula.Tipo.CANCELAMENTO);
        ocorrencia.setStudio(studio);
        ocorrenciaAulaRepository.save(ocorrencia);

        Long turmaId = turma.getId();
        Turma turmaParaApagar = turma;

        assertThatCode(() -> turmaService.delete(turmaParaApagar)).doesNotThrowAnyException();

        assertThat(turmaRepository.findById(turmaId)).isEmpty();
        assertThat(mensalidadeRepository.findByAluno(aluno)).isEmpty();
        assertThat(presencaRepository.findByAlunoId(aluno.getId())).isEmpty();
        assertThat(avaliacaoAlunoRepository.findByAlunoOrderByDataAvaliacaoDesc(aluno)).isEmpty();
        assertThat(listaEsperaRepository.findByStudioOrderByDataInscricaoAsc(studio)).isEmpty();
    }
}
