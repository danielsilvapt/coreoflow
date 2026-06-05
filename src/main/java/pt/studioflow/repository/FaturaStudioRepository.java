package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.FaturaStudio;
import pt.studioflow.model.Studio;

import java.util.List;
import java.util.Optional;

public interface FaturaStudioRepository extends JpaRepository<FaturaStudio, Long> {
    List<FaturaStudio> findByStudioOrderByAnoDescMesDesc(Studio studio);
    List<FaturaStudio> findAllByOrderByAnoDescMesDesc();
    Optional<FaturaStudio> findByStudioAndAnoAndMes(Studio studio, int ano, int mes);
    List<FaturaStudio> findByEstadoOrderByDataVencimentoAsc(FaturaStudio.Estado estado);
}
