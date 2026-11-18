package pt.studioflow.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.RegistoEmail;
import pt.studioflow.model.RegistoEmail.Estado;

@Repository
public interface RegistoEmailRepository extends JpaRepository<RegistoEmail, Long> {

    /** Pesquisa com filtros opcionais (null = ignorar), mais recentes primeiro. */
    @Query("""
        SELECT r FROM RegistoEmail r
        WHERE r.studioId = :studioId
          AND (:estado IS NULL OR r.estado = :estado)
          AND (:utilizador IS NULL OR r.utilizador = :utilizador)
          AND (:comFalhas = false OR r.numFalhados > 0)
          AND (:texto IS NULL OR LOWER(r.assunto) LIKE LOWER(CONCAT('%', :texto, '%'))
                              OR LOWER(r.turma) LIKE LOWER(CONCAT('%', :texto, '%'))
                              OR LOWER(r.utilizadorNome) LIKE LOWER(CONCAT('%', :texto, '%'))
                              OR EXISTS (SELECT 1 FROM RegistoEmailDestinatario d
                                         WHERE d.registoId = r.id
                                           AND (LOWER(d.nome) LIKE LOWER(CONCAT('%', :texto, '%'))
                                                OR d.email LIKE LOWER(CONCAT('%', :texto, '%')))))
          AND (:desde IS NULL OR r.data >= :desde)
        ORDER BY r.data DESC, r.id DESC
        """)
    List<RegistoEmail> pesquisar(@Param("studioId") Long studioId,
                                 @Param("estado") Estado estado,
                                 @Param("utilizador") String utilizador,
                                 @Param("comFalhas") boolean comFalhas,
                                 @Param("texto") String texto,
                                 @Param("desde") LocalDateTime desde,
                                 Pageable pageable);

    Optional<RegistoEmail> findFirstByMessageId(String messageId);
}
