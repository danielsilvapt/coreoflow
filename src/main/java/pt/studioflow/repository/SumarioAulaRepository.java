package pt.studioflow.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.Studio;
import pt.studioflow.model.SumarioAula;
import pt.studioflow.model.Turma;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SumarioAulaRepository extends JpaRepository<SumarioAula, Long> {

    Optional<SumarioAula> findByTurmaAndDataAndHoraInicio(Turma turma, LocalDate data, LocalTime horaInicio);

    List<SumarioAula> findByDataBetween(LocalDate inicio, LocalDate fim);

    List<SumarioAula> findByStudioAndDataBetween(Studio studio, LocalDate inicio, LocalDate fim);

    @Transactional
    void deleteByTurma(Turma turma);
}
