package pt.studioflow.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.PagamentoOnline;

@Repository
public interface PagamentoOnlineRepository extends JpaRepository<PagamentoOnline, Long> {

    Optional<PagamentoOnline> findByMolliePaymentId(String molliePaymentId);

    List<PagamentoOnline> findByAlunoOrderByDataCriacaoDesc(Aluno aluno);

    List<PagamentoOnline> findByAlunoAndTipoInOrderByDataCriacaoDesc(Aluno aluno, List<PagamentoOnline.Tipo> tipos);

    void deleteByAluno(Aluno aluno);
}
