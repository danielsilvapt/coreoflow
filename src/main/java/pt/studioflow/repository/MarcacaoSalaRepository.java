package pt.studioflow.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.transaction.Transactional;
import pt.studioflow.model.Studio;
import pt.studioflow.model.MarcacaoSala;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Sala;
import pt.studioflow.model.Turma;

public interface MarcacaoSalaRepository extends JpaRepository<MarcacaoSala, Long> {
        List<MarcacaoSala> findBySalaAndDataBetween(
                        Sala sala, LocalDate inicio, LocalDate fim);

        List<MarcacaoSala> findByDataBetween(
                        LocalDate inicio, LocalDate fim);

        @Query("SELECT m FROM MarcacaoSala m " +
                        "LEFT JOIN FETCH m.alunos " +
                        "LEFT JOIN FETCH m.turma " + // Aproveitamos e trazemos a turma também
                        "WHERE m.id = :id")
        Optional<MarcacaoSala> findByIdComAlunosETurma(@Param("id") Long id);

        List<MarcacaoSala> findByProfessorAndData(String professor, LocalDate data);

        // Esta consulta resolve o erro de LazyInitializationException
        @Query("SELECT DISTINCT m FROM MarcacaoSala m LEFT JOIN FETCH m.alunos WHERE m.data = :data")
        List<MarcacaoSala> findAllWithAlunosByData(@Param("data") LocalDate data);


    List<MarcacaoSala> findByProfessorAndStatusAndDataBetween(String professor, String status, LocalDate inicio, LocalDate fim);

    List<MarcacaoSala> findByStudioAndStatus(Studio studio, String status);

    java.util.List<MarcacaoSala> findAllByStudio(Studio studio);

    List<MarcacaoSala> findByStudioAndDataBetween(Studio studio, LocalDate inicio, LocalDate fim);

    @Query("SELECT DISTINCT m FROM MarcacaoSala m LEFT JOIN FETCH m.alunos WHERE m.studio = :studio AND m.data = :data")
    List<MarcacaoSala> findAllWithAlunosByDataAndStudio(@Param("studio") Studio studio, @Param("data") LocalDate data);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE MarcacaoSala m SET m.turma = null WHERE m.turma = :turma")
    void desvincularTurma(@Param("turma") Turma turma);
}
