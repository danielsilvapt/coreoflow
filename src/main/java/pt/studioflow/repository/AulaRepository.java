package pt.studioflow.repository;

import java.time.DayOfWeek;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.Aula;
import pt.studioflow.model.Sala;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

@Repository
public interface AulaRepository extends JpaRepository<Aula, Long> {
    List<Aula> findByTurma(Turma turma);

    List<Aula> findByDia(DayOfWeek dia);

    @Query("SELECT a FROM Aula a WHERE a.turma.sala = :sala")
    List<Aula> findBySala(@Param("sala") Sala sala);

    // ===================== MULTI-TENANT =====================
    @Query("SELECT a FROM Aula a WHERE a.turma.studio = :studio")
    List<Aula> findByTurmaStudio(@Param("studio") Studio studio);
}
