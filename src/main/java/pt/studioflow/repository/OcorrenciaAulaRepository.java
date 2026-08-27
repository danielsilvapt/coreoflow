package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.OcorrenciaAula;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface OcorrenciaAulaRepository extends JpaRepository<OcorrenciaAula, Long> {
    List<OcorrenciaAula> findByStudioAndDataBetweenOrderByDataAsc(Studio studio, LocalDate inicio, LocalDate fim);
    List<OcorrenciaAula> findByTurmaAndDataBetweenOrderByDataAsc(Turma turma, LocalDate inicio, LocalDate fim);

    @Transactional
    void deleteByTurma(Turma turma);
}
