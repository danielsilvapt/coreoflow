package pt.studioflow.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoPagamentoCredito;
import pt.studioflow.model.PagamentoOnline;

@Repository
public interface PagamentoOnlineRepository extends JpaRepository<PagamentoOnline, Long> {

    Optional<PagamentoOnline> findByMolliePaymentId(String molliePaymentId);

    List<PagamentoOnline> findByAlunoOrderByDataCriacaoDesc(Aluno aluno);

    List<PagamentoOnline> findByAlunoAndTipoInOrderByDataCriacaoDesc(Aluno aluno, List<PagamentoOnline.Tipo> tipos);

    List<PagamentoOnline> findByEstadoAndMolliePaymentIdIsNotNullAndDataCriacaoAfter(
            EstadoPagamentoCredito estado, LocalDateTime desde);

    @Transactional
    void deleteByAluno(Aluno aluno);
}
