package pt.studioflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.PackAula;
import pt.studioflow.model.Studio;

@Repository
public interface PackAulaRepository extends JpaRepository<PackAula, Long> {

    List<PackAula> findAllByStudio(Studio studio);

    List<PackAula> findByStudioAndAtivoTrue(Studio studio);
}
