package pt.studioflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;

import org.junit.jupiter.api.Test;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Studio;

/** Testes puros do prazo de pagamento por estúdio e do estado efetivo da mensalidade. */
class MensalidadeConfigEstadoEfetivoTest {

    private final MensalidadeConfig config = new MensalidadeConfig();

    private Studio studioComDia(Integer dia) {
        Studio s = new Studio();
        s.setDiaLimitePagamento(dia);
        return s;
    }

    private Mensalidade mensalidade(int ano, Month mes, EstadoMensalidade estado) {
        Mensalidade m = new Mensalidade();
        m.setAno(ano);
        m.setMes(mes);
        m.setEstado(estado);
        return m;
    }

    @Test
    void diaLimite_usaOmissao8_quandoNuloOuInvalido() {
        assertThat(config.diaLimitePagamento(studioComDia(null))).isEqualTo(8);
        assertThat(config.diaLimitePagamento(studioComDia(0))).isEqualTo(8);
        assertThat(config.diaLimitePagamento(studioComDia(31))).isEqualTo(8);
        assertThat(config.diaLimitePagamento(null)).isEqualTo(8);
        assertThat(config.diaLimitePagamento(studioComDia(15))).isEqualTo(15);
    }

    @Test
    void dataLimite_respeitaOConfiguradoENuncaExcedeOMes() {
        assertThat(config.dataLimite(2026, Month.MARCH, studioComDia(10)))
                .isEqualTo(LocalDate.of(2026, 3, 10));
        // fevereiro tem 28 dias em 2026 — o dia 28 é o máximo permitido pela config, ok
        assertThat(config.dataLimite(2026, Month.FEBRUARY, studioComDia(28)))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void estadoEfetivo_faturadoVencidoFicaEmDivida() {
        LocalDate ontem = LocalDate.now().minusDays(1);
        Studio studio = studioComDia(ontem.getDayOfMonth() <= 28 ? ontem.getDayOfMonth() : 28);
        Mensalidade m = mensalidade(ontem.getYear(), ontem.getMonth(), EstadoMensalidade.FATURADO);
        // limite = dia de "ontem" desse mês => já passou
        assertThat(config.estadoEfetivo(m, studio)).isEqualTo(EstadoMensalidade.EM_DIVIDA);
    }

    @Test
    void estadoEfetivo_faturadoDentroDoPrazoMantemSe() {
        LocalDate futuro = LocalDate.now().plusMonths(1);
        Mensalidade m = mensalidade(futuro.getYear(), futuro.getMonth(), EstadoMensalidade.FATURADO);
        assertThat(config.estadoEfetivo(m, studioComDia(1))).isEqualTo(EstadoMensalidade.FATURADO);
    }

    @Test
    void estadoEfetivo_naoMexeEmPorEmitirNemPago() {
        LocalDate passado = LocalDate.now().minusMonths(2);
        Mensalidade porEmitir = mensalidade(passado.getYear(), passado.getMonth(), EstadoMensalidade.POR_EMITIR);
        Mensalidade pago = mensalidade(passado.getYear(), passado.getMonth(), EstadoMensalidade.PAGO);
        assertThat(config.estadoEfetivo(porEmitir, studioComDia(1))).isEqualTo(EstadoMensalidade.POR_EMITIR);
        assertThat(config.estadoEfetivo(pago, studioComDia(1))).isEqualTo(EstadoMensalidade.PAGO);
    }
}
