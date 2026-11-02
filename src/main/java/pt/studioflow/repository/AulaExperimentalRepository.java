package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.AulaExperimental;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AulaExperimentalRepository extends JpaRepository<AulaExperimental, Long> {

    List<AulaExperimental> findByStudioOrderByDataDescHoraInicioDesc(Studio studio);

    List<AulaExperimental> findByStudioAndDataOrderByHoraInicioAsc(Studio studio, LocalDate data);

    List<AulaExperimental> findByStudioAndDataBetweenOrderByDataAscHoraInicioAsc(Studio studio, LocalDate de,
            LocalDate ate);

    long countByStudioAndEstadoAndDataGreaterThanEqual(Studio studio, AulaExperimental.Estado estado, LocalDate data);

    boolean existsByTurmaAndDataAndTelemovelAndEstado(Turma turma, LocalDate data, String telemovel,
            AulaExperimental.Estado estado);

    @org.springframework.transaction.annotation.Transactional
    void deleteByTurma(Turma turma);

    /** Mantém o histórico da marcação quando o aluno é apagado. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("update AulaExperimental a set a.aluno = null where a.aluno.id = :alunoId")
    void desvincularAluno(@org.springframework.data.repository.query.Param("alunoId") Long alunoId);
}
