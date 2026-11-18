package pt.studioflow.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.RegistoEmailDestinatario;
import pt.studioflow.model.RegistoEmailDestinatario.EstadoEntrega;

@Repository
public interface RegistoEmailDestinatarioRepository extends JpaRepository<RegistoEmailDestinatario, Long> {

    List<RegistoEmailDestinatario> findByRegistoIdOrderByNomeAsc(Long registoId);

    List<RegistoEmailDestinatario> findByRegistoIdAndEmail(Long registoId, String email);

    long countByRegistoIdAndEstadoIn(Long registoId, Collection<EstadoEntrega> estados);

    /**
     * Plano B quando o Message-ID do evento não corresponde a nenhum envio:
     * destinatários com este email em envios recentes com o mesmo assunto.
     */
    @Query("""
        SELECT d.registoId FROM RegistoEmailDestinatario d, RegistoEmail r
        WHERE d.registoId = r.id
          AND d.email = :email
          AND r.assunto = :assunto
          AND r.data >= :desde
        ORDER BY r.data DESC
        """)
    List<Long> registosRecentesPorEmailEAssunto(@Param("email") String email,
                                                @Param("assunto") String assunto,
                                                @Param("desde") LocalDateTime desde,
                                                Pageable pageable);
}
