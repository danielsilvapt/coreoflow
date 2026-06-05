package pt.studioflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Professor;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {

    Optional<Professor> findByEmail(String email);

    Optional<Professor> findByNome(String nome);

    // ===================== MULTI-TENANT =====================
    java.util.List<Professor> findAllByStudio(Studio studio);

}
