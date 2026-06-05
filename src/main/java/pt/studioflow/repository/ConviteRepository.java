package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Convite;
import java.util.List;

@Repository
public interface ConviteRepository extends JpaRepository<Convite, Long> {
    // Procura convites ordenados por data (mais recentes primeiro)
    List<Convite> findAllByOrderByDataDesc();

    @Query("SELECT c FROM Convite c WHERE c.data >= CURRENT_DATE ORDER BY c.data ASC")
    List<Convite> findAllAtivos();

    // ===================== MULTI-TENANT =====================
    java.util.List<Convite> findAllByStudio(Studio studio);

}
