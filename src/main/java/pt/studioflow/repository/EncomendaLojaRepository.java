package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.EncomendaLoja;
import pt.studioflow.model.Studio;

import java.util.List;

public interface EncomendaLojaRepository extends JpaRepository<EncomendaLoja, Long> {
    List<EncomendaLoja> findByStudioOrderByDataEncomendaDesc(Studio studio);
    List<EncomendaLoja> findByStudioAndEstadoOrderByDataEncomendaDesc(Studio studio, EncomendaLoja.Estado estado);
    long countByStudioAndEstado(Studio studio, EncomendaLoja.Estado estado);
}
