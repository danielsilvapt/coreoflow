package pt.studioflow.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.CompraCredito;
import pt.studioflow.model.Studio;

@Repository
public interface CompraCreditoRepository extends JpaRepository<CompraCredito, Long> {

    List<CompraCredito> findByAluno(Aluno aluno);

    List<CompraCredito> findByAlunoOrderByDataCompraDesc(Aluno aluno);

    java.util.Optional<CompraCredito> findByMolliePaymentId(String molliePaymentId);

    @Query("SELECT c FROM CompraCredito c WHERE c.aluno = :aluno AND c.creditosRestantes > 0 " +
            "AND (c.validadeAte IS NULL OR c.validadeAte >= CURRENT_DATE)")
    List<CompraCredito> findCreditosValidosDoAluno(@Param("aluno") Aluno aluno);

    List<CompraCredito> findByStudioAndDataCompraAfterOrderByDataCompraDesc(Studio studio, LocalDateTime desde);

    List<CompraCredito> findByStudio(Studio studio);
}
