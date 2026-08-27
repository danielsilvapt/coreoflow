package pt.studioflow.service;

import org.junit.jupiter.api.Test;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes puros (sem contexto Spring/BD) da lógica de contagem de renovações
 * usada pelo card "Renovações" do dashboard.
 */
class DashboardServiceTest {

    private final DashboardService service = new DashboardService(null, null);
    private final LocalDate inicioAnoLetivo = LocalDate.of(2026, 9, 1);

    private Aluno aluno(AlunoStatus status, boolean pedidoRenovacao, LocalDate dataInscricaoRenovacao, LocalDate carimboDataHora) {
        Aluno a = new Aluno();
        a.setStatus(status);
        a.setPedidoRenovacao(pedidoRenovacao);
        a.setDataInscricaoRenovacao(dataInscricaoRenovacao);
        a.setCarimboDataHora(carimboDataHora);
        return a;
    }

    @Test
    void countRenovacoesPendentes_soContaPendenteComPedidoRenovacao() {
        List<Aluno> alunos = List.of(
                aluno(AlunoStatus.PENDENTE, true, null, null),   // conta
                aluno(AlunoStatus.PENDENTE, false, null, null),  // nova inscrição, não conta
                aluno(AlunoStatus.ATIVO, true, null, null));     // já validado, não conta

        assertThat(service.countRenovacoesPendentes(alunos)).isEqualTo(1);
    }

    @Test
    void countRenovacoesConcluidasAnoLetivo_soContaAtivosRenovadosDentroDoAnoLetivo() {
        List<Aluno> alunos = List.of(
                aluno(AlunoStatus.ATIVO, false, LocalDate.of(2026, 9, 15), null),   // conta
                aluno(AlunoStatus.ATIVO, false, LocalDate.of(2026, 6, 1), null),    // ano letivo anterior, não conta
                aluno(AlunoStatus.INATIVO, false, LocalDate.of(2026, 9, 15), null)); // não ativo, não conta

        assertThat(service.countRenovacoesConcluidasAnoLetivo(alunos, inicioAnoLetivo)).isEqualTo(1);
    }

    @Test
    void countElegiveisRenovacaoAnoLetivo_contaAtivosEInativosMatriculadosAntesDoAnoLetivo() {
        List<Aluno> alunos = List.of(
                aluno(AlunoStatus.ATIVO, false, null, LocalDate.of(2025, 10, 1)),   // conta
                aluno(AlunoStatus.INATIVO, false, null, LocalDate.of(2024, 3, 1)),  // conta
                aluno(AlunoStatus.ATIVO, false, null, LocalDate.of(2026, 9, 10)),   // matriculado já no novo ano, não conta
                aluno(AlunoStatus.PENDENTE, false, null, LocalDate.of(2025, 10, 1))); // não elegível, não conta

        assertThat(service.countElegiveisRenovacaoAnoLetivo(alunos, inicioAnoLetivo)).isEqualTo(2);
    }
}
