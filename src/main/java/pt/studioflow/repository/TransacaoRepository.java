package pt.studioflow.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import pt.studioflow.model.TipoTransacao;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Transacao;

public interface TransacaoRepository extends JpaRepository<Transacao, Long> {
    List<Transacao> findByTipoAndDataBetween(TipoTransacao tipo, LocalDate inicio, LocalDate fim);

    // ===================== MULTI-TENANT =====================
    java.util.List<Transacao> findAllByStudio(Studio studio);

}
