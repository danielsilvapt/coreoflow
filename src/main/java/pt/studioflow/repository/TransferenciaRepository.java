package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Transferencia;

@Repository
public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {
    // Aqui herdas automaticamente todos os métodos CRUD básicos (findAll, save,
    // etc.)

    // ===================== MULTI-TENANT =====================
    java.util.List<Transferencia> findAllByStudio(Studio studio);

}
