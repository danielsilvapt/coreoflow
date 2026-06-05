package pt.studioflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.transaction.Transactional;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Turma;

public interface AlunoTurmaRepository extends JpaRepository<AlunoTurma, Long> {

    boolean existsByAlunoAndTurma(Aluno aluno, Turma turma);

    List<AlunoTurma> findByTurma(Turma turma);

    void deleteByAlunoAndTurma(Aluno aluno, Turma turma);

    List<AlunoTurma> findByAluno(Aluno aluno);

    Optional<AlunoTurma> findByAlunoAndTurma(Aluno aluno, Turma turma);

    @Modifying
    @Transactional
    @Query("DELETE FROM AlunoTurma at WHERE at.aluno = :aluno")
    void deleteByAluno(@Param("aluno") Aluno aluno);

    @Modifying
    @Transactional
    @Query("DELETE FROM AlunoTurma at WHERE at.aluno.id = :alunoId")
    void deleteByAlunoId(@Param("alunoId") Long alunoId);
    

}