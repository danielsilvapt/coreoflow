package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.Inquerito;
import pt.studioflow.model.Studio;

import java.util.List;

public interface InqueritoRepository extends JpaRepository<Inquerito, Long> {
    List<Inquerito> findByStudioOrderByDataCriacaoDesc(Studio studio);
}
