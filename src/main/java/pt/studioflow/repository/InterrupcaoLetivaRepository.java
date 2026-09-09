package pt.studioflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.InterrupcaoLetiva;
import pt.studioflow.model.Studio;

@Repository
public interface InterrupcaoLetivaRepository extends JpaRepository<InterrupcaoLetiva, Long> {

    List<InterrupcaoLetiva> findByStudioOrderByDataInicioAsc(Studio studio);
}
