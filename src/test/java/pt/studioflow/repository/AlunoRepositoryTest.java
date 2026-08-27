package pt.studioflow.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de isolamento multi-tenant e do bug corrigido em findByEmailWithTurmas
 * (NonUniqueResultException com múltiplas turmas, mistura de alunos entre estúdios).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlunoRepositoryTest {

    @Autowired
    private StudioRepository studioRepository;
    @Autowired
    private ModalidadeRepository modalidadeRepository;
    @Autowired
    private TurmaRepository turmaRepository;
    @Autowired
    private AlunoRepository alunoRepository;
    @Autowired
    private AlunoTurmaRepository alunoTurmaRepository;
    @Autowired
    private TestEntityManager entityManager;

    private Studio criarStudio(String slug) {
        Studio s = new Studio();
        s.setNome("Studio " + slug);
        s.setSlug(slug);
        s.setAtivo(true);
        return studioRepository.save(s);
    }

    private Aluno criarAluno(Studio studio, String nome, String email) {
        Aluno a = new Aluno();
        a.setNomeCompleto(nome);
        a.setEmail(email);
        a.setStudio(studio);
        a.setStatus(AlunoStatus.ATIVO);
        return alunoRepository.save(a);
    }

    private Turma criarTurma(Studio studio, Modalidade modalidade, String codigo) {
        Turma t = new Turma();
        t.setCodigo(codigo);
        t.setDescricao("Turma " + codigo);
        t.setModalidade(modalidade);
        t.setStudio(studio);
        t.setAtivo(true);
        return turmaRepository.save(t);
    }

    @Test
    void findByEmailAndStudioWithTurmas_soDevolveOAlunoDoStudioCorreto() {
        Studio studioA = criarStudio("studio-a");
        Studio studioB = criarStudio("studio-b");

        Aluno alunoA = criarAluno(studioA, "Ana (Studio A)", "duplicado@test.com");
        Aluno alunoB = criarAluno(studioB, "Ana (Studio B)", "duplicado@test.com");

        List<Aluno> resultadoA = alunoRepository.findByEmailAndStudioWithTurmas("duplicado@test.com", studioA);
        List<Aluno> resultadoB = alunoRepository.findByEmailAndStudioWithTurmas("duplicado@test.com", studioB);

        assertThat(resultadoA).extracting(Aluno::getId).containsExactly(alunoA.getId());
        assertThat(resultadoB).extracting(Aluno::getId).containsExactly(alunoB.getId());
    }

    @Test
    void findByEmailAndStudioWithTurmas_naoRebentaComAlunoEmMultiplasTurmas() {
        Studio studio = criarStudio("studio-multi");
        Modalidade modalidade = new Modalidade();
        modalidade.setDescricao("Hip Hop");
        modalidade.setStudio(studio);
        modalidade = modalidadeRepository.save(modalidade);

        Aluno aluno = criarAluno(studio, "Bea", "bea@test.com");
        Turma turma1 = criarTurma(studio, modalidade, "T1");
        Turma turma2 = criarTurma(studio, modalidade, "T2");

        AlunoTurma at1 = new AlunoTurma();
        at1.setAluno(aluno);
        at1.setTurma(turma1);
        alunoTurmaRepository.save(at1);

        AlunoTurma at2 = new AlunoTurma();
        at2.setAluno(aluno);
        at2.setTurma(turma2);
        alunoTurmaRepository.save(at2);

        // Limpa o 1º-level cache para forçar um load real (como aconteceria num
        // pedido novo em produção) em vez de reaproveitar o objeto Aluno já
        // gerido nesta mesma sessão, cujo campo `turmas` nunca foi atribuído.
        entityManager.flush();
        entityManager.clear();

        // Antes da correção, o JOIN FETCH sem DISTINCT devolvia 2 linhas para o
        // mesmo aluno e Optional<Aluno> rebentava com NonUniqueResultException.
        List<Aluno> resultado = alunoRepository.findByEmailAndStudioWithTurmas("bea@test.com", studio);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getTurmas()).hasSize(2);
    }

    @Test
    void findAllByStudio_naoMisturaAlunosDeOutroStudio() {
        Studio studioA = criarStudio("studio-x");
        Studio studioB = criarStudio("studio-y");
        criarAluno(studioA, "Aluno X", "x@test.com");
        criarAluno(studioB, "Aluno Y", "y@test.com");

        List<Aluno> alunosA = alunoRepository.findAllByStudio(studioA);

        assertThat(alunosA).hasSize(1);
        assertThat(alunosA.get(0).getNomeCompleto()).isEqualTo("Aluno X");
    }
}
