package pt.studioflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.Professor;
import pt.studioflow.model.Sala;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

@Repository
public interface TurmaRepository extends JpaRepository<Turma, Long> {

    @Query("""
                select distinct t
                from Turma t
                left join fetch t.aulas
                where t.sala = :sala
                order by t.descricao asc
            """)
    List<Turma> findBySalaWithAulas(@Param("sala") Sala sala);

    // REMOVIDO O @Override (O Spring cria a implementação sozinho)
    @EntityGraph(attributePaths = { "aulas", "professor" })
    List<Turma> findAllByOrderByDescricaoAsc();

    // Se quiseres que o findAll padrão também venha ordenado,
    // usamos a Query explicitamente:
    @EntityGraph(attributePaths = { "aulas", "professor" })
    @Query("SELECT t FROM Turma t ORDER BY t.descricao ASC")
    List<Turma> findAll();

    @Query("SELECT t FROM Turma t JOIN FETCH t.modalidade ORDER BY t.descricao ASC")
    List<Turma> findAllWithModalidade();

    @Query("SELECT t FROM Turma t LEFT JOIN FETCH t.professor LEFT JOIN FETCH t.modalidade LEFT JOIN FETCH t.aulas ORDER BY t.descricao ASC")
    List<Turma> findAllComplete();

    @Query("SELECT DISTINCT t FROM Turma t " +
            "LEFT JOIN FETCH t.professor " +
            "LEFT JOIN FETCH t.aulas") // Carrega as aulas primeiro
    List<Turma> findAllWithAulas();

    @Query("SELECT DISTINCT t FROM Turma t " +
            "LEFT JOIN FETCH t.alunosTurma at " +
            "LEFT JOIN FETCH at.aluno") // Carrega os alunos depois
    List<Turma> findAllWithAlunos();

    // No seu TurmaRepository
    @Query("SELECT t FROM Turma t " +
            "LEFT JOIN FETCH t.professor " +
            "LEFT JOIN FETCH t.modalidade " +
            "LEFT JOIN FETCH t.aulas " + // ADICIONE ESTA LINHA
            "WHERE t.id = :id")
    Turma findByIdCompleto(@Param("id") Long id);

    @Query("SELECT t FROM Turma t LEFT JOIN FETCH t.aulas WHERE t.id = :id ORDER BY t.descricao ASC")
    Optional<Turma> findByIdWithAulas(@Param("id") Long id);

    @Query("SELECT t FROM Turma t JOIN t.alunosTurma at JOIN at.aluno a WHERE a.email = :email")
    List<Turma> findByAlunosEmail(@Param("email") String email);

    @Query("SELECT t.id, size(t.alunosTurma) FROM Turma t")
    List<Object[]> getContagemAlunosPorTurma();

    List<Turma> findByProfessor(Professor professor);

    @Query("SELECT t FROM Turma t LEFT JOIN FETCH t.alunosTurma WHERE t.id = :id")
    Optional<Turma> findByIdWithAlunos(@Param("id") Long id);

    // ===================== MULTI-TENANT =====================
    @Query("SELECT t FROM Turma t LEFT JOIN FETCH t.professor LEFT JOIN FETCH t.modalidade LEFT JOIN FETCH t.aulas WHERE t.studio = :studio ORDER BY t.descricao ASC")
    List<Turma> findAllByStudio(@Param("studio") Studio studio);

    @Query("SELECT t FROM Turma t LEFT JOIN FETCH t.aulas WHERE t.studio = :studio AND t.sala = :sala ORDER BY t.descricao ASC")
    List<Turma> findBySalaAndStudioWithAulas(@Param("sala") Sala sala, @Param("studio") Studio studio);

    @Query("SELECT t FROM Turma t JOIN t.alunosTurma at JOIN at.aluno a WHERE a.email = :email AND t.studio = :studio")
    List<Turma> findByAlunosEmailAndStudio(@Param("email") String email, @Param("studio") Studio studio);

    List<Turma> findByProfessorAndStudio(Professor professor, Studio studio);

    List<Turma> findByStudioAndAtivo(Studio studio, boolean ativo);

    default List<Turma> findByStudioAndAtivoTrue(Studio studio) {
        return findByStudioAndAtivo(studio, true);
    }
}
