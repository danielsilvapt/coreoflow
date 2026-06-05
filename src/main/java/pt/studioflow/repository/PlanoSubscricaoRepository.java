package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.PlanoSubscricao;

import java.util.List;

public interface PlanoSubscricaoRepository extends JpaRepository<PlanoSubscricao, Long> {
    List<PlanoSubscricao> findByAtivoTrueOrderByPrecoMensalAsc();
}
