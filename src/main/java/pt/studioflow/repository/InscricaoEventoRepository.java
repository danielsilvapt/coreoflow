package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Convite;
import pt.studioflow.model.Studio;
import pt.studioflow.model.InscricaoEvento;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface InscricaoEventoRepository extends JpaRepository<InscricaoEvento, Long> {

    // Verifica se o aluno já marcou interesse
    boolean existsByAlunoAndConviteAndInteressado(Aluno aluno, Convite convite, boolean interessado);

    // Procura a inscrição específica de um aluno num convite
    Optional<InscricaoEvento> findByAlunoAndConvite(Aluno aluno, Convite convite);

    // Retorna todos os alunos que o professor já confirmou para este convite
    @Query("SELECT i.aluno FROM InscricaoEvento i WHERE i.convite = ?1 AND i.confirmado = true")
    List<Aluno> findAlunosConfirmados(Convite convite);

    // Remove confirmações de alunos de uma turma específica para um convite (útil
    // ao atualizar)
    @Transactional
    @Modifying
    @Query("DELETE FROM InscricaoEvento i WHERE i.convite = ?1 AND i.confirmado = true AND i.aluno IN " +
            "(SELECT at.aluno FROM AlunoTurma at WHERE at.turma.id = ?2)")
    void limparConfirmacoesTurma(Convite convite, Long turmaId);

    @Query("SELECT i.aluno FROM InscricaoEvento i WHERE i.convite = ?1 AND i.confirmado = true")
    Set<Aluno> findConfirmadosByEvento(Convite convite);

    long countByConviteAndConfirmado(Convite convite, boolean confirmado);

    Optional<InscricaoEvento> findByAlunoIdAndConviteId(Long alunoId, Long conviteId);

    List<InscricaoEvento> findByConvite(Convite convite);

    // ===================== MULTI-TENANT =====================
    java.util.List<InscricaoEvento> findAllByStudio(Studio studio);

}
