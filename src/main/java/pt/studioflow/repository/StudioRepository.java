package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.Studio;

import java.util.Optional;

public interface StudioRepository extends JpaRepository<Studio, Long> {
    Optional<Studio> findBySlug(String slug);
    Optional<Studio> findBySlugAndAtivoTrue(String slug);
}
