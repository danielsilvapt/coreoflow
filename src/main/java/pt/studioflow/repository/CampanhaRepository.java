package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.Campanha;
import pt.studioflow.model.Studio;

import java.util.List;

@Repository
public interface CampanhaRepository extends JpaRepository<Campanha, Long> {
    List<Campanha> findByStudioOrderByDataCriacaoDesc(Studio studio);
}
