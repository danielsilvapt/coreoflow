package pt.studioflow.repository;

import java.time.Month;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Turma;

@Repository
public interface MensalidadeRepository extends JpaRepository<Mensalidade, Long> {
    boolean existsByAlunoAndTurmaAndAnoAndMes(Aluno aluno, Turma turma, int ano, Month mes);

    // Busca todas as mensalidades de um aluno
    List<Mensalidade> findByAluno(Aluno aluno);

    // Busca todas as mensalidades de um estado específico
    List<Mensalidade> findByEstado(EstadoMensalidade estado);

    List<Mensalidade> findByAlunoAndEstado(Aluno aluno, EstadoMensalidade estado);

        @Query("SELECT m FROM Mensalidade m WHERE m.aluno = :aluno AND m.turma = :turma " + 
           "AND m.ano = :ano AND m.mes > :mesAtual")
    List<Mensalidade> findMensalidadesFuturas(
        @Param("aluno") Aluno aluno, 
        @Param("turma") Turma turma, 
        @Param("ano") int ano, 
        @Param("mesAtual") java.time.Month mesAtual 
    );

    @Transactional
    void deleteByAluno(Aluno aluno);

    @Transactional
    void deleteByTurma(Turma turma);

    // ===================== MULTI-TENANT =====================
    java.util.List<Mensalidade> findAllByStudio(Studio studio);
    java.util.List<Mensalidade> findByAlunoAndStudio(pt.studioflow.model.Aluno aluno, Studio studio);

}
