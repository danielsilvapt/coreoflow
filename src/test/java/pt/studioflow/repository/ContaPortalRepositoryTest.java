package pt.studioflow.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import pt.studioflow.model.ContaPortal;
import pt.studioflow.model.Studio;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ContaPortalRepositoryTest {

    @Autowired
    private StudioRepository studioRepository;
    @Autowired
    private ContaPortalRepository contaPortalRepository;
    @Autowired
    private TestEntityManager entityManager;

    private Studio criarStudio(String slug) {
        Studio s = new Studio();
        s.setNome("Studio " + slug);
        s.setSlug(slug);
        s.setAtivo(true);
        return studioRepository.save(s);
    }

    private ContaPortal criarConta(Studio studio, String email) {
        ContaPortal c = new ContaPortal();
        c.setEmail(email);
        c.setStudio(studio);
        c.setNome("Pai Teste");
        return contaPortalRepository.save(c);
    }

    @Test
    void findByEmailIgnoreCase_encontraIndependentementeDaCaixa() {
        Studio studio = criarStudio("obidos");
        criarConta(studio, "pai@teste.com");
        entityManager.flush();
        entityManager.clear();

        assertThat(contaPortalRepository.findByEmailIgnoreCase("PAI@TESTE.COM")).isPresent();
        assertThat(contaPortalRepository.findByEmailIgnoreCase("pai@teste.com")).isPresent();
        assertThat(contaPortalRepository.findByEmailIgnoreCase("outro@teste.com")).isEmpty();
    }

    @Test
    void email_eUnico() {
        Studio studio = criarStudio("caldas");
        criarConta(studio, "dup@teste.com");
        entityManager.flush();

        assertThatThrownBy(() -> {
            criarConta(studio, "dup@teste.com");
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByTokenAtivacao_eTokenValido() {
        Studio studio = criarStudio("peniche");
        ContaPortal c = criarConta(studio, "token@teste.com");
        c.setTokenAtivacao("abc123");
        c.setTokenExpiraEm(LocalDateTime.now().plusDays(1));
        contaPortalRepository.save(c);
        entityManager.flush();
        entityManager.clear();

        ContaPortal encontrada = contaPortalRepository.findByTokenAtivacao("abc123").orElseThrow();
        assertThat(encontrada.tokenValido("abc123")).isTrue();
        assertThat(encontrada.tokenValido("errado")).isFalse();

        encontrada.setTokenExpiraEm(LocalDateTime.now().minusMinutes(1));
        assertThat(encontrada.tokenValido("abc123")).isFalse();
    }
}
