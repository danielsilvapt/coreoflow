package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.Referencia;
import pt.studioflow.model.Studio;

import java.util.List;

public interface ReferenciaRepository extends JpaRepository<Referencia, Long> {
    List<Referencia> findByStudioOrderByDataReferenciaDesc(Studio studio);
    long countByStudioAndEstado(Studio studio, Referencia.Estado estado);
}
