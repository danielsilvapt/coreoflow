package pt.studioflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.PlanoDanca;
import pt.studioflow.model.Studio;

@Repository
public interface PlanoDancaRepository extends JpaRepository<PlanoDanca, Long> {

    List<PlanoDanca> findByStudioOrderByDataCriacaoDesc(Studio studio);

    List<PlanoDanca> findByStudioAndCriadoPorEmailOrderByDataCriacaoDesc(Studio studio, String criadoPorEmail);

    long countByStudio(Studio studio);
}
