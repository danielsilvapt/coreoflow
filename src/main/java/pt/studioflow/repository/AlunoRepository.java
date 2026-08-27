package pt.studioflow.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

@Repository
public interface AlunoRepository extends JpaRepository<Aluno, Long> {

    // ===================== MULTI-TENANT =====================

    List<Aluno> findAllByStudio(Studio studio);

    @Query("SELECT a FROM Aluno a LEFT JOIN FETCH a.turmas WHERE a.studio = :studio ORDER BY a.nomeCompleto ASC")
    List<Aluno> findAllByStudioOrderByNomeCompletoAsc(@Param("studio") Studio studio);

    @Query("SELECT DISTINCT a FROM Aluno a LEFT JOIN FETCH a.turmas at LEFT JOIN FETCH at.turma " +
           "WHERE a.status IN :statuses AND a.studio = :studio")
    List<Aluno> findByStatusWithTurmasByStudio(@Param("statuses") Collection<AlunoStatus> statuses, @Param("studio") Studio studio);

    @Query(value = "SELECT * FROM aluno WHERE MONTH(data_nascimento) = MONTH(CURDATE()) AND DAY(data_nascimento) = DAY(CURDATE()) AND studio_id = :studioId", nativeQuery = true)
    List<Aluno> findAniversariantesDeHojeByStudio(@Param("studioId") Long studioId);

    List<Aluno> findByAtivoAndStudio(int ativo, Studio studio);

    @Query("SELECT DISTINCT a FROM Aluno a LEFT JOIN FETCH a.turmas WHERE a.email = :email AND a.studio = :studio")
    List<Aluno> findByEmailAndStudioWithTurmas(@Param("email") String email, @Param("studio") Studio studio);

    // ===================== LEGADO (sem filtro de studio) =====================

    List<Aluno> findByTurmas(Turma turma);
    List<Aluno> findByAtivo(int ativo);

    @Query(value = "SELECT * FROM aluno WHERE MONTH(data_nascimento) = MONTH(CURDATE()) AND DAY(data_nascimento) = DAY(CURDATE())", nativeQuery = true)
    List<Aluno> findAniversariantesDeHoje();

    @Query("SELECT DISTINCT a FROM Aluno a LEFT JOIN FETCH a.turmas at LEFT JOIN FETCH at.turma WHERE a.status IN :statuses")
    List<Aluno> findByStatusWithTurmas(@Param("statuses") Collection<AlunoStatus> statuses);

    @Query("SELECT a FROM Aluno a LEFT JOIN FETCH a.turmas WHERE a.email = :email")
    Optional<Aluno> findByEmailWithTurmas(@Param("email") String email);

    List<Aluno> findAllByOrderByNomeCompletoAsc();

    Aluno findByEmail(String email);

    @Query(value = "SELECT localidade FROM aluno WHERE codigo_postal = :cp LIMIT 1", nativeQuery = true)
    String findLocalidadeByCodPostal(@Param("cp") String cp);

    @EntityGraph(attributePaths = {"turmas", "turmas.turma"})
    @Query("SELECT a FROM Aluno a WHERE a.id = :id")
    Optional<Aluno> findByIdWithTurmas(@Param("id") Long id);

    @EntityGraph(attributePaths = {"turmas", "turmas.turma"})
    @Query("SELECT a FROM Aluno a WHERE a.status IN :statuses")
    List<Aluno> findByStatusWithTurmas(@Param("statuses") List<AlunoStatus> statuses);
}
