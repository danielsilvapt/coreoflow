package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.ProdutoLoja;
import pt.studioflow.model.Studio;

import java.util.List;

public interface ProdutoLojaRepository extends JpaRepository<ProdutoLoja, Long> {
    List<ProdutoLoja> findByStudioAndAtivoTrueOrderByNomeAsc(Studio studio);
    List<ProdutoLoja> findByStudioOrderByNomeAsc(Studio studio);
}
