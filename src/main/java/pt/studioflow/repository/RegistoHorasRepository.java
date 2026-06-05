package pt.studioflow.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.Studio;
import pt.studioflow.model.RegistoHoras;

@Repository
public interface RegistoHorasRepository extends JpaRepository<RegistoHoras, Long> {
    long countByProfessorAndTipoAtividadeAndInicioBetween(String professor, String tipoAtividade, LocalDateTime inicio, LocalDateTime fim);

    java.util.List<RegistoHoras> findByProfessorAndAnoAndMesNumero(String professor, int ano, int mesNumero);

    // ==============// ===================== MULTI-TENANT =====================
    java.util.List<RegistoHoras> findAllByStudio(Studio studio);
}
