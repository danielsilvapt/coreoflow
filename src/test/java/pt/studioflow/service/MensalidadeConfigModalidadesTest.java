package pt.studioflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

/** Testes puros do modelo MODALIDADES e das turmas com preço por frequência. */
class MensalidadeConfigModalidadesTest {

    private final MensalidadeConfig config = new MensalidadeConfig();

    private Studio studioModalidades() {
        Studio s = new Studio();
        s.setModeloPrecario("MODALIDADES");
        s.setMeiaMensalidadeAutomatica(false);
        return s; // valores por omissão: 25 / 40 / 50 / 65
    }

    private Turma turma(long id, long modalidadeId) {
        Modalidade m = new Modalidade();
        m.setId(modalidadeId);
        Turma t = new Turma();
        t.setCodigo("T" + id);
        t.setModalidade(m);
        return t;
    }

    @Test
    void valorTabelaModalidades_aulaFixa_umaModalidade_duas_livreTransito() {
        Studio s = studioModalidades();
        assertThat(config.valorTabelaModalidades(s, 1, 1, false)).isEqualTo(25.0);
        assertThat(config.valorTabelaModalidades(s, 1, 2, false)).isEqualTo(40.0);
        assertThat(config.valorTabelaModalidades(s, 2, 2, false)).isEqualTo(50.0);
        assertThat(config.valorTabelaModalidades(s, 3, 3, false)).isEqualTo(65.0);
        assertThat(config.valorTabelaModalidades(s, 5, 8, false)).isEqualTo(65.0);
    }

    @Test
    void valorPacote_duasTurmasDaMesmaModalidade_contamComoUma() {
        Studio s = studioModalidades();
        Map<Turma, Integer> turmas = new LinkedHashMap<>();
        turmas.put(turma(1, 10), 1); // Ballet I
        turmas.put(turma(2, 10), 2); // Ballet II
        assertThat(config.valorPacote(s, turmas, false)).isEqualTo(40.0);
    }

    @Test
    void estimar_turmaComPrecoProprioFicaForaDoPacote() {
        Studio s = studioModalidades();
        Turma barre = turma(1, 10);
        Turma preBallet = turma(2, 11);
        preBallet.setMensalidadeSocio(30.0);
        preBallet.setMensalidadeNaoSocio(30.0);
        Map<Turma, Integer> turmas = new LinkedHashMap<>();
        turmas.put(barre, 1);
        turmas.put(preBallet, 2);

        Map<Turma, Double> valores = config.estimarMensalidadesPorTurma(s, turmas, true, false);

        assertThat(valores.get(barre)).isEqualTo(25.0);
        assertThat(valores.get(preBallet)).isEqualTo(30.0);
    }

    @Test
    void precoPorFrequencia_boxe1x35_3x45() {
        Turma boxe = turma(1, 12);
        boxe.setPrecosFrequencia("1:35;3:45");

        assertThat(boxe.temMensalidadePropria()).isTrue();
        assertThat(boxe.getFrequenciasDisponiveis()).isEqualTo(List.of(1, 3));
        assertThat(config.valorProprioDaTurma(boxe, false, 1)).isEqualTo(35.0);
        assertThat(config.valorProprioDaTurma(boxe, false, 3)).isEqualTo(45.0);
        // sem entrada exata: a maior frequência abaixo
        assertThat(config.valorProprioDaTurma(boxe, true, 2)).isEqualTo(35.0);
    }

    @Test
    void estimar_boxeEDancaNoMesmoAluno_somamSeparadamente() {
        Studio s = studioModalidades();
        Turma danca = turma(1, 10);
        Turma boxe = turma(2, 12);
        boxe.setPrecosFrequencia("1:35;3:45");
        Map<Turma, Integer> turmas = new LinkedHashMap<>();
        turmas.put(danca, 2);
        turmas.put(boxe, 3);

        Map<Turma, Double> valores = config.estimarMensalidadesPorTurma(s, turmas, false, true);

        assertThat(valores.get(danca)).isEqualTo(40.0);
        assertThat(valores.get(boxe)).isEqualTo(45.0);
    }

    @Test
    void rotulosDeFrequencia_idaEVolta() {
        assertThat(Turma.rotuloFrequencia(1)).isEqualTo("1 vez / semana");
        assertThat(Turma.rotuloFrequencia(3)).isEqualTo("3 vezes / semana");
        assertThat(Turma.frequenciaDoRotulo("3 vezes / semana")).isEqualTo(3);
        assertThat(Turma.frequenciaDoRotulo(null)).isEqualTo(2);
    }
}
