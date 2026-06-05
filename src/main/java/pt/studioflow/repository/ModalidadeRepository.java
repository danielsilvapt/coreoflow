package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.Studio;
import pt.studioflow.model.Modalidade;

@Repository
public interface ModalidadeRepository extends JpaRepository<Modalidade, Long> {
    // Métodos personalizados podem ser adicionados aqui
    Modalidade findByCodigo(String codigo);

    // ===================== MULTI-TENANT =====================
    java.util.List<Modalidade> findAllByStudio(Studio studio);

}
