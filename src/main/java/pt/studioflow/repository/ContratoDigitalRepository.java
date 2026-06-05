package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.ContratoDigital;
import pt.studioflow.model.Studio;

import java.util.List;

@Repository
public interface ContratoDigitalRepository extends JpaRepository<ContratoDigital, Long> {
    List<ContratoDigital> findByStudioOrderByDataGeracaoDesc(Studio studio);
    List<ContratoDigital> findByAlunoOrderByDataGeracaoDesc(Aluno aluno);
    List<ContratoDigital> findByStudioAndEstadoOrderByDataGeracaoDesc(Studio studio, String estado);
}
