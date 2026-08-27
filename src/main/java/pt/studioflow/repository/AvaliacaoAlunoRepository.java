package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.AvaliacaoAluno;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

import jakarta.transaction.Transactional;
import java.util.List;

@Repository
public interface AvaliacaoAlunoRepository extends JpaRepository<AvaliacaoAluno, Long> {
    List<AvaliacaoAluno> findByAlunoOrderByDataAvaliacaoDesc(Aluno aluno);
    List<AvaliacaoAluno> findByStudioOrderByDataAvaliacaoDesc(Studio studio);
    List<AvaliacaoAluno> findByTurmaAndStudioOrderByDataAvaliacaoDesc(Turma turma, Studio studio);

    @Transactional
    void deleteByTurma(Turma turma);
}
