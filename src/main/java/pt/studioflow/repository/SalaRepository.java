package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Sala;

@Repository
public interface SalaRepository extends JpaRepository<Sala, Long> {
    // Aqui você pode colocar queries customizadas se precisar

    // ===================== MULTI-TENANT =====================
    java.util.List<Sala> findAllByStudio(Studio studio);

}
