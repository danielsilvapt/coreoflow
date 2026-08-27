package pt.studioflow.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.ModalidadeRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.TurmaRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RenovacaoService.class)
class RenovacaoServiceTest {

    @Autowired private StudioRepository studioRepository;
    @Autowired private ModalidadeRepository modalidadeRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private AlunoRepository alunoRepository;
    @Autowired private RenovacaoService renovacaoService;

    @MockBean
    private EmailService emailService;

    private Studio criarStudio(String slug) {
        Studio s = new Studio();
        s.setNome("Studio " + slug);
        s.setSlug(slug);
        s.setAtivo(true);
        return studioRepository.save(s);
    }

    @Test
    void procurarPorEmail_naoDevolveAlunosDeOutroStudio() {
        Studio studioA = criarStudio("renov-a");
        Studio studioB = criarStudio("renov-b");

        Aluno alunoA = new Aluno();
        alunoA.setNomeCompleto("Aluno A");
        alunoA.setEmail("mesmo@test.com");
        alunoA.setStudio(studioA);
        alunoA.setStatus(AlunoStatus.ATIVO);
        alunoRepository.save(alunoA);

        Aluno alunoB = new Aluno();
        alunoB.setNomeCompleto("Aluno B");
        alunoB.setEmail("mesmo@test.com");
        alunoB.setStudio(studioB);
        alunoB.setStatus(AlunoStatus.ATIVO);
        alunoRepository.save(alunoB);

        List<Aluno> encontradosA = renovacaoService.procurarPorEmail("mesmo@test.com", studioA);

        assertThat(encontradosA).hasSize(1);
        assertThat(encontradosA.get(0).getNomeCompleto()).isEqualTo("Aluno A");
    }

    @Test
    void submeterRenovacao_alunoElegivel_ficaPendenteComFlagRenovacao() {
        doNothing().when(emailService).enviarEmailNotificacaoRenovacao(any());
        doNothing().when(emailService).enviarEmailConfirmacaoCandidato(any(), any());

        Studio studio = criarStudio("renov-submit");
        Modalidade modalidade = new Modalidade();
        modalidade.setDescricao("Contemporâneo");
        modalidade.setStudio(studio);
        modalidade = modalidadeRepository.save(modalidade);

        Turma turma = new Turma();
        turma.setCodigo("SUB-1");
        turma.setDescricao("Turma Submissao");
        turma.setModalidade(modalidade);
        turma.setStudio(studio);
        turma.setAtivo(true);
        turma = turmaRepository.save(turma);

        Aluno aluno = new Aluno();
        aluno.setNomeCompleto("Aluno Elegivel");
        aluno.setEmail("elegivel@test.com");
        aluno.setStudio(studio);
        aluno.setStatus(AlunoStatus.INATIVO);
        aluno = alunoRepository.save(aluno);

        boolean submetido = renovacaoService.submeterRenovacao(aluno, Map.of(turma, 2), studio.getNome());

        assertThat(submetido).isTrue();
        assertThat(aluno.getStatus()).isEqualTo(AlunoStatus.PENDENTE);
        assertThat(aluno.isPedidoRenovacao()).isTrue();
        assertThat(aluno.getMorada()).contains("Turma Submissao");
    }

    @Test
    void submeterRenovacao_alunoJaPendente_naoDuplicaPedido() {
        Studio studio = criarStudio("renov-duplicado");
        Aluno aluno = new Aluno();
        aluno.setNomeCompleto("Aluno Pendente");
        aluno.setEmail("pendente@test.com");
        aluno.setStudio(studio);
        aluno.setStatus(AlunoStatus.PENDENTE);
        aluno.setPedidoRenovacao(true);
        alunoRepository.save(aluno);

        Modalidade modalidade = new Modalidade();
        modalidade.setDescricao("Sapateado");
        modalidade.setStudio(studio);
        modalidade = modalidadeRepository.save(modalidade);

        Turma turma = new Turma();
        turma.setCodigo("SUB-2");
        turma.setDescricao("Outra Turma");
        turma.setModalidade(modalidade);
        turma.setStudio(studio);
        turma.setAtivo(true);
        turma = turmaRepository.save(turma);

        boolean submetido = renovacaoService.submeterRenovacao(aluno, Map.of(turma, 1), studio.getNome());

        assertThat(submetido).isFalse();
    }
}
