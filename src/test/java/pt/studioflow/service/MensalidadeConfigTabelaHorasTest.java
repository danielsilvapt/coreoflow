package pt.studioflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Month;

import org.junit.jupiter.api.Test;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

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
    void valorTabelaHoras_meiaMensalidadeDesligada_cobraValorCheio() {
        Studio s = studioComTabelaPadrao();
        s.setMeiaMensalidadeAutomatica(false);
        assertThat(config.valorTabelaHoras(s, 2, true)).isEqualTo(43.0);
    }

    @Test
    void estimarMensalidadesPorTurma_duasModalidades1x_contamComo2xSemana() {
        Studio s = studioComTabelaPadrao();
        s.setModeloPrecario("HORAS_SEMANA");
        Turma ballet = new Turma();
        Turma hipHop = new Turma();
        java.util.Map<Turma, Integer> turmas = new java.util.LinkedHashMap<>();
        turmas.put(ballet, 1);
        turmas.put(hipHop, 1);

        java.util.Map<Turma, Double> valores = config.estimarMensalidadesPorTurma(s, turmas, true, true);

        assertThat(valores.get(ballet)).isEqualTo(21.5);
        assertThat(valores.get(hipHop)).isEqualTo(21.5);
        assertThat(valores.values().stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(43.0);
    }

    @Test
    void estimarMensalidadesPorTurma_modeloPadrao_valorPorTurma() {
        Studio s = studioComTabelaPadrao();
        s.setMensalidadeCrianca1x(28.0);
        s.setMensalidadeNaoSocioAdicional(10.0);
        Turma t = new Turma();

        java.util.Map<Turma, Double> valores = config.estimarMensalidadesPorTurma(
                s, java.util.Map.of(t, 1), true, true);

        assertThat(valores.get(t)).isEqualTo(28.0);
    }

    @Test
    void calcularResumo_primeiraMensalidadeGratis_alunoNovoPagaSoTaxa() {
        Studio s = studioComTabelaPadrao();
        s.setModeloPrecario("HORAS_SEMANA");
        s.setTaxaInscricao(25.0);
        s.setPrimeiraMensalidadeGratis(true);

        MensalidadeConfig.ResumoInscricao resumo = config.calcularResumo(s, 43.0, false, false, 2);

        assertThat(resumo.ofertaPrimeiraMensalidade()).isEqualTo(43.0);
        assertThat(resumo.total()).isEqualTo(25.0);
    }

    @Test
    void calcularResumo_primeiraMensalidadeGratis_naoAplicaARenovacoes() {
        Studio s = studioComTabelaPadrao();
        s.setTaxaRenovacao(10.0);
        s.setPrimeiraMensalidadeGratis(true);

        MensalidadeConfig.ResumoInscricao resumo = config.calcularResumo(s, 43.0, true, false, 1);

        assertThat(resumo.ofertaPrimeiraMensalidade()).isZero();
        assertThat(resumo.total()).isEqualTo(53.0);
    }

    @Test
    void valorTabelaHoras_semTabelaConfigurada_devolveZero() {
        Studio s = new Studio();
        s.setTabelaHoras1(null);
        assertThat(config.valorTabelaHoras(s, 1, false)).isEqualTo(0.0);
    }

    @Test
    void calcularResumo_modeloHorasSemana_semDescontoMaisModalidades_comDescontoIrmaos() {
        Studio s = studioComTabelaPadrao();
        s.setModeloPrecario("HORAS_SEMANA");
        s.setDescontoFamiliaresPercentagem(10.0);
        s.setDescontoMaisModalidadesPercentagem(25.0);
        s.setTaxaInscricao(25.0);

        MensalidadeConfig.ResumoInscricao resumo = config.calcularResumo(s, 43.0, false, true, 2);

        assertThat(resumo.descontoFamiliar()).isCloseTo(4.3, org.assertj.core.data.Offset.offset(0.001));
        assertThat(resumo.descontoMultiModalidade()).isZero();
        assertThat(resumo.total()).isCloseTo(38.7 + 25.0, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void aplicarDescontoFamiliar_percentagemSubstituiEuros() {
        Studio s = studioComTabelaPadrao();
        s.setDescontoFamiliaresEuros(5.0);
        assertThat(config.aplicarDescontoFamiliar(s, 43.0)).isEqualTo(38.0);

        s.setDescontoFamiliaresPercentagem(10.0);
        assertThat(config.aplicarDescontoFamiliar(s, 43.0)).isEqualTo(38.7);
    }
}
