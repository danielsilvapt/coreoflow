package pt.studioflow.service;

import org.junit.jupiter.api.Test;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes puros das regras de elegibilidade para renovação de matrícula. */
class RenovacaoServiceEligibilityTest {

    private final RenovacaoService service = new RenovacaoService(null, null);

    private Aluno alunoComStatus(AlunoStatus status) {
        Aluno a = new Aluno();
        a.setStatus(status);
        return a;
    }

    @Test
    void ativoEInativoSaoElegiveis() {
        assertThat(service.elegivel(alunoComStatus(AlunoStatus.ATIVO))).isTrue();
        assertThat(service.elegivel(alunoComStatus(AlunoStatus.INATIVO))).isTrue();
    }

    @Test
    void pendenteNaoEElegivel_jaTemPedidoEmCurso() {
        assertThat(service.elegivel(alunoComStatus(AlunoStatus.PENDENTE))).isFalse();
    }

    @Test
    void experimentalNaoEElegivel() {
        assertThat(service.elegivel(alunoComStatus(AlunoStatus.EXPERIMENTAL))).isFalse();
    }
}
