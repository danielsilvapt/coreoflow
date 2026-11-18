package pt.studioflow.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pt.studioflow.model.User;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Professor;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {

    Optional<Professor> findByEmail(String email);

    Optional<Professor> findByNome(String nome);

    /** Professor ligado explicitamente a este utilizador (FK professores.user_id). */
    Optional<Professor> findByUser(User user);

    long countByUserIsNotNull();

    /** Remove a ligação professor→utilizador (antes de apagar o utilizador). */
    @Modifying
    @Transactional
    @Query("update Professor p set p.user = null where p.user = :user")
    int desligarUtilizador(@Param("user") User user);

    // ===================== MULTI-TENANT =====================
    java.util.List<Professor> findAllByStudio(Studio studio);

}
