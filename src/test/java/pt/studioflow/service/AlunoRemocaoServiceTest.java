package pt.studioflow.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.ContaPortal;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AlunoRemocaoService.class)
class AlunoRemocaoServiceTest {

    @Autowired
    private AlunoRemocaoService service;
    @Autowired
    private TestEntityManager em;

    @MockBean
    private R2StorageService r2StorageService;
    @MockBean
    private AuditoriaService auditoriaService;

    private Studio studio;
    private Turma turma;

    private void base() {
        studio = new Studio();
        studio.setNome("Studio X");
        studio.setSlug("x");
        studio.setAtivo(true);
        em.persist(studio);
        Modalidade m = new Modalidade();
        m.setDescricao("Ballet");
        m.setStudio(studio);
        em.persist(m);
        turma = new Turma();
        turma.setCodigo("T1");
        turma.setDescricao("Turma 1");
        turma.setModalidade(m);
        turma.setStudio(studio);
        turma.setAtivo(true);
        em.persist(turma);
    }

    private Aluno aluno(String email, Aluno.AlunoStatus status) {
        Aluno a = new Aluno();
        a.setNomeCompleto("Aluno " + email);
        a.setEmail(email);
        a.setStudio(studio);
        a.setStatus(status);
        em.persist(a);
        AlunoTurma at = new AlunoTurma();
        at.setAluno(a);
        at.setTurma(turma);
        em.persist(at);
        Mensalidade ms = new Mensalidade();
        ms.setAluno(a);
        ms.setTurma(turma);
        ms.setStudio(studio);
        ms.setAno(2026);
        ms.setMes(Month.OCTOBER);
        ms.setValor(30);
        em.persist(ms);
        return a;
    }

    private long count(String entidade, Long alunoId) {
        return em.getEntityManager()
                .createQuery("SELECT COUNT(x) FROM " + entidade + " x WHERE x.aluno.id = :id", Long.class)
                .setParameter("id", alunoId).getSingleResult();
    }

    @Test
    void eliminaAlunoInativoETodosOsRegistos() {
        base();
        Aluno a = aluno("pai@teste.com", Aluno.AlunoStatus.INATIVO);
        ContaPortal c = new ContaPortal();
        c.setEmail("pai@teste.com");
        c.setStudio(studio);
        em.persist(c);
        em.flush();
        em.clear();

        service.eliminarDefinitivamente(a.getId());
        em.flush();
        em.clear();

        assertThat(em.find(Aluno.class, a.getId())).isNull();
        assertThat(count("AlunoTurma", a.getId())).isZero();
        assertThat(count("Mensalidade", a.getId())).isZero();
        assertThat(em.find(ContaPortal.class, c.getId())).isNull();
    }

    @Test
    void mantemContaPortalSeIrmaoUsaOMesmoEmail() {
        base();
        Aluno a = aluno("pai@teste.com", Aluno.AlunoStatus.INATIVO);
        Aluno irmao = aluno("pai@teste.com", Aluno.AlunoStatus.ATIVO);
        ContaPortal c = new ContaPortal();
        c.setEmail("pai@teste.com");
        c.setStudio(studio);
        em.persist(c);
        em.flush();
        em.clear();

        service.eliminarDefinitivamente(a.getId());
        em.flush();
        em.clear();

        assertThat(em.find(Aluno.class, a.getId())).isNull();
        assertThat(em.find(Aluno.class, irmao.getId())).isNotNull();
        assertThat(count("Mensalidade", irmao.getId())).isEqualTo(1);
        assertThat(em.find(ContaPortal.class, c.getId())).isNotNull();
    }

    @Test
    void recusaAlunoAtivo() {
        base();
        Aluno a = aluno("ativo@teste.com", Aluno.AlunoStatus.ATIVO);
        em.flush();
        em.clear();

        assertThatThrownBy(() -> service.eliminarDefinitivamente(a.getId()))
                .isInstanceOf(IllegalStateException.class);
    }
}
