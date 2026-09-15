package pt.studioflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Month;

import org.junit.jupiter.api.Test;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.Studio;

/** Testes puros da tabela de preçário por horas/semana (modelo HORAS_SEMANA). */
class MensalidadeConfigTabelaHorasTest {

    private final MensalidadeConfig config = new MensalidadeConfig();

    private Studio studioComTabelaPadrao() {
        Studio s = new Studio();
        s.setTabelaHoras1(35.0);
        s.setTabelaHoras2(43.0);
        s.setTabelaHoras3(49.0);
        s.setTabelaHoras4(56.0);
        s.setTabelaHoras5(60.0);
        s.setTabelaHoras6Mais(65.0);
        return s;
    }

    @Test
    void isMesMeioMensalidade_apenasSetembroDezembroJulho() {
        assertThat(config.isMesMeioMensalidade(Month.SEPTEMBER)).isTrue();
        assertThat(config.isMesMeioMensalidade(Month.DECEMBER)).isTrue();
        assertThat(config.isMesMeioMensalidade(Month.JULY)).isTrue();
        assertThat(config.isMesMeioMensalidade(Month.JANUARY)).isFalse();
        assertThat(config.isMesMeioMensalidade(Month.JUNE)).isFalse();
    }

    @Test
    void valorTabelaHoras_escalao1a5_valorCheio() {
        Studio s = studioComTabelaPadrao();
        assertThat(config.valorTabelaHoras(s, 1, false)).isEqualTo(35.0);
        assertThat(config.valorTabelaHoras(s, 2, false)).isEqualTo(43.0);
        assertThat(config.valorTabelaHoras(s, 3, false)).isEqualTo(49.0);
        assertThat(config.valorTabelaHoras(s, 4, false)).isEqualTo(56.0);
        assertThat(config.valorTabelaHoras(s, 5, false)).isEqualTo(60.0);
    }

    @Test
    void valorTabelaHoras_seisOuMais_usaSempreEscalaoPasse() {
        Studio s = studioComTabelaPadrao();
        assertThat(config.valorTabelaHoras(s, 6, false)).isEqualTo(65.0);
        assertThat(config.valorTabelaHoras(s, 10, false)).isEqualTo(65.0);
    }

    @Test
    void valorTabelaHoras_menorQueUm_tratadoComoUm() {
        Studio s = studioComTabelaPadrao();
        assertThat(config.valorTabelaHoras(s, 0, false)).isEqualTo(35.0);
        assertThat(config.valorTabelaHoras(s, -1, false)).isEqualTo(35.0);
    }

    @Test
    void valorTabelaHoras_meioMes_metadeDoValorCheio() {
        Studio s = studioComTabelaPadrao();
        assertThat(config.valorTabelaHoras(s, 1, true)).isEqualTo(17.5);
        assertThat(config.valorTabelaHoras(s, 2, true)).isEqualTo(21.5);
        assertThat(config.valorTabelaHoras(s, 3, true)).isEqualTo(24.5);
        assertThat(config.valorTabelaHoras(s, 4, true)).isEqualTo(28.0);
        assertThat(config.valorTabelaHoras(s, 5, true)).isEqualTo(30.0);
        assertThat(config.valorTabelaHoras(s, 6, true)).isEqualTo(32.5);
    }

    @Test
    void valorTabelaHoras_semTabelaConfigurada_devolveZero() {
        Studio s = new Studio();
        s.setTabelaHoras1(null);
        assertThat(config.valorTabelaHoras(s, 1, false)).isEqualTo(0.0);
    }

    @Test
    void calcularResumo_modeloHorasSemana_ignoraDescontos() {
        Studio s = studioComTabelaPadrao();
        s.setModeloPrecario("HORAS_SEMANA");
        s.setDescontoFamiliaresEuros(5.0);
        s.setDescontoMaisModalidadesPercentagem(25.0);
        s.setTaxaInscricao(25.0);

        MensalidadeConfig.ResumoInscricao resumo = config.calcularResumo(s, 43.0, false, true, 2);

        assertThat(resumo.descontoFamiliar()).isZero();
        assertThat(resumo.descontoMultiModalidade()).isZero();
        assertThat(resumo.total()).isEqualTo(43.0 + 25.0);
    }
}
