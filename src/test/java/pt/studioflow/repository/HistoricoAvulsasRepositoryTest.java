package pt.studioflow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import pt.studioflow.model.Studio;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HistoricoAvulsasRepositoryTest {

    @Autowired
    private StudioRepository studioRepository;
    @Autowired
    private CompraCreditoRepository compraRepo;
    @Autowired
    private PresencaRepository presencaRepo;

    @Test
    void queriesDoHistoricoCorremComESemPeriodo() {
        Studio s = new Studio();
        s.setNome("Studio teste");
        s.setSlug("teste-avulsas");
        s.setAtivo(true);
        s = studioRepository.save(s);

        assertThat(compraRepo.historicoDoStudio(s, null)).isEmpty();
        assertThat(compraRepo.historicoDoStudio(s, LocalDateTime.now().minusDays(30))).isEmpty();
        assertThat(presencaRepo.avulsasUtilizadas(s, null)).isEmpty();
        assertThat(presencaRepo.avulsasUtilizadas(s, LocalDate.now().minusDays(30))).isEmpty();
    }
}
