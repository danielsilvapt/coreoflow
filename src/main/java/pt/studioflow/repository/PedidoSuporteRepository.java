package pt.studioflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.PedidoSuporte;

@Repository
public interface PedidoSuporteRepository extends JpaRepository<PedidoSuporte, Long> {
    List<PedidoSuporte> findAllByOrderByDataCriacaoDesc();
}
