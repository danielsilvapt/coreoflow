package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.Inquerito;
import pt.studioflow.model.RespostaInquerito;

import java.util.List;

public interface RespostaInqueritoRepository extends JpaRepository<RespostaInquerito, Long> {
    List<RespostaInquerito> findByInquerito(Inquerito inquerito);
    long countByInquerito(Inquerito inquerito);
    boolean existsByInqueritoAndAlunoId(Inquerito inquerito, Long alunoId);
}
