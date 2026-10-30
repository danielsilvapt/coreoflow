package pt.studioflow.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.RegistoAuditoria;
import pt.studioflow.model.RegistoAuditoria.Tipo;

@Repository
public interface RegistoAuditoriaRepository extends JpaRepository<RegistoAuditoria, Long> {

    /** Uma linha por utilizador (e estúdio) com o resumo da atividade no período. */
    record ResumoUtilizador(Long studioId, String studioNome, String utilizador, String papel,
            LocalDateTime ultimoLogin, LocalDateTime ultimaAtividade, Long logins, Long alteracoes) {
    }

    /**
     * Pesquisa com filtros opcionais (null = ignorar). {@code plataforma = true}
     * restringe aos registos sem estúdio (superadmin / logins sem estúdio válido).
     */
    @Query("""
        SELECT r FROM RegistoAuditoria r
        WHERE (:tipo IS NULL OR r.tipo = :tipo)
          AND (:studioId IS NULL OR r.studioId = :studioId)
          AND (:plataforma = false OR r.studioId IS NULL)
          AND (:utilizador IS NULL OR LOWER(r.utilizador) LIKE LOWER(CONCAT('%', :utilizador, '%')))
          AND (:texto IS NULL OR LOWER(r.descricao) LIKE LOWER(CONCAT('%', :texto, '%'))
                              OR LOWER(r.entidade) LIKE LOWER(CONCAT('%', :texto, '%'))
                              OR r.ip LIKE CONCAT('%', :texto, '%'))
          AND (:desde IS NULL OR r.data >= :desde)
        ORDER BY r.data DESC, r.id DESC
        """)
    List<RegistoAuditoria> pesquisar(@Param("tipo") Tipo tipo,
                                     @Param("studioId") Long studioId,
                                     @Param("plataforma") boolean plataforma,
                                     @Param("utilizador") String utilizador,
                                     @Param("texto") String texto,
                                     @Param("desde") LocalDateTime desde,
                                     Pageable pageable);

    @Query("""
        SELECT new pt.studioflow.repository.RegistoAuditoriaRepository$ResumoUtilizador(
                 r.studioId, MAX(r.studioNome), r.utilizador, MAX(r.papel),
                 MAX(CASE WHEN r.tipo = :login THEN r.data ELSE NULL END),
                 MAX(r.data),
                 SUM(CASE WHEN r.tipo = :login THEN 1L ELSE 0L END),
                 SUM(CASE WHEN r.tipo IN :alteracoes THEN 1L ELSE 0L END))
        FROM RegistoAuditoria r
        WHERE r.data >= :desde
          AND r.tipo <> :falhado
          AND r.utilizador NOT IN ('sistema', 'público')
          AND (:studioId IS NULL OR r.studioId = :studioId)
          AND (:plataforma = false OR r.studioId IS NULL)
        GROUP BY r.studioId, r.utilizador
        ORDER BY MAX(r.data) DESC
        """)
    List<ResumoUtilizador> resumoPorUtilizador(@Param("desde") LocalDateTime desde,
                                               @Param("studioId") Long studioId,
                                               @Param("plataforma") boolean plataforma,
                                               @Param("login") Tipo login,
                                               @Param("falhado") Tipo falhado,
                                               @Param("alteracoes") Collection<Tipo> alteracoes);

    long countByTipoAndDataAfter(Tipo tipo, LocalDateTime data);

    long countByTipoInAndDataAfter(Collection<Tipo> tipos, LocalDateTime data);

    @Query("""
        SELECT COUNT(DISTINCT CONCAT(COALESCE(r.studioSlug, ''), ':', r.utilizador)) FROM RegistoAuditoria r
        WHERE r.tipo = :login AND r.data >= :desde
        """)
    long contarUtilizadoresComLogin(@Param("login") Tipo login, @Param("desde") LocalDateTime desde);

    /** Remoção em bloco (sem carregar as entidades) — pode haver muitos registos. */
    @Transactional
    @Modifying
    @Query("DELETE FROM RegistoAuditoria r WHERE r.data < :data")
    int apagarAnterioresA(@Param("data") LocalDateTime data);
}
