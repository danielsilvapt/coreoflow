package pt.studioflow.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Presenca;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

public interface PresencaRepository extends JpaRepository<Presenca, Long> {

    Optional<Presenca> findByAlunoAndTurmaAndData(
            Aluno aluno, Turma turma, LocalDate data);

    List<Presenca> findByAlunoId(Long alunoId);

    List<Presenca> findByTurmaAndDataBetween(
            Turma turma,
            LocalDate dataInicio,
            LocalDate dataFim);

    /**
     * Verifica se existe pelo menos um registo de presença (presente=true)
     * para um aluno específico num intervalo de datas.
     */
    boolean existsByAlunoAndPresenteAndDataBetween(
            Aluno aluno, boolean presente, LocalDate dataInicio, LocalDate dataFim);
}
