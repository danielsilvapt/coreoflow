package pt.studioflow.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.LogEntry;

@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, Long> {

    @Query("""
        SELECT l FROM LogEntry l
        WHERE (:nivel IS NULL OR l.nivel = :nivel)
          AND (:studio IS NULL OR l.studioSlug = :studio)
          AND (:texto IS NULL OR LOWER(l.mensagem) LIKE LOWER(CONCAT('%', :texto, '%'))
                              OR LOWER(l.logger) LIKE LOWER(CONCAT('%', :texto, '%')))
          AND (:desde IS NULL OR l.data >= :desde)
        ORDER BY l.data DESC
        """)
    List<LogEntry> pesquisar(@Param("nivel") String nivel,
                             @Param("studio") String studio,
                             @Param("texto") String texto,
                             @Param("desde") LocalDateTime desde,
                             Pageable pageable);

    long countByNivelAndDataAfter(String nivel, LocalDateTime data);

    @Query("SELECT DISTINCT l.studioSlug FROM LogEntry l WHERE l.studioSlug IS NOT NULL ORDER BY l.studioSlug")
    List<String> studiosComLogs();

    @Transactional
    long deleteByDataBefore(LocalDateTime data);
}
