package pt.studioflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Studio;

class LembreteMensalidadeServiceTest {

    private static Aluno aluno(long id, String nome, String email, boolean ativo) {
        Aluno a = new Aluno();
        a.setId(id);
        a.setNomeCompleto(nome);
        a.setEmail(email);
        a.setAtivo(ativo);
        return a;
    }

    private static Mensalidade mens(Aluno a, int ano, Month mes, double valor, EstadoMensalidade estado) {
        Mensalidade m = new Mensalidade();
        m.setAluno(a);
        m.setAno(ano);
        m.setMes(mes);
        m.setValor(valor);
        m.setEstado(estado);
        return m;
    }

    @Test
    void diaDoLembreteE30OuUltimoDiaEmFevereiro() {
        assertThat(LembreteMensalidadeService.diaDoLembrete(YearMonth.of(2026, 11))).isEqualTo(30);
        assertThat(LembreteMensalidadeService.diaDoLembrete(YearMonth.of(2026, 12))).isEqualTo(30);
        assertThat(LembreteMensalidadeService.diaDoLembrete(YearMonth.of(2027, 2))).isEqualTo(28);
        assertThat(LembreteMensalidadeService.diaDoLembrete(YearMonth.of(2028, 2))).isEqualTo(29);
    }

    @Test
    void agrupaIrmaosPorEmailEIgnoraPagasInativasEOutrosMeses() {
        Aluno ana = aluno(1, "Ana Silva", "Familia@Mail.pt", true);
        Aluno rui = aluno(2, "Rui Silva", "familia@mail.pt", true);
        Aluno ines = aluno(3, "Inês Costa", "ines@mail.pt", true);
        Aluno inativo = aluno(4, "Zé Antigo", "ze@mail.pt", false);
        Aluno semEmail = aluno(5, "Sem Email", " ", true);

        Map<String, List<LembreteMensalidadeService.Linha>> r = LembreteMensalidadeService.agruparPorEmail(List.of(
                mens(ana, 2026, Month.DECEMBER, 40, EstadoMensalidade.POR_EMITIR),
                mens(rui, 2026, Month.DECEMBER, 36, EstadoMensalidade.FATURADO),
                mens(ines, 2026, Month.DECEMBER, 50, EstadoMensalidade.PAGO),
                mens(ines, 2026, Month.NOVEMBER, 50, EstadoMensalidade.FATURADO),
                mens(inativo, 2026, Month.DECEMBER, 40, EstadoMensalidade.FATURADO),
                mens(semEmail, 2026, Month.DECEMBER, 40, EstadoMensalidade.FATURADO),
                mens(ana, 2026, Month.DECEMBER, 0, EstadoMensalidade.POR_EMITIR)),
                YearMonth.of(2026, 12));

        assertThat(r).containsOnlyKeys("familia@mail.pt");
        assertThat(r.get("familia@mail.pt")).extracting(LembreteMensalidadeService.Linha::aluno)
                .containsExactly("Ana Silva", "Rui Silva");
    }

    @Test
    void portalDeixaPagarMesSeguinteSoComLembreteLigadoEAPartirDoDia30() {
        PagamentoOnlineService svc = new PagamentoOnlineService(null, null, null, null, null);
        Aluno a = aluno(1, "Ana", "a@a.pt", true);
        List<Mensalidade> ms = List.of(
                mens(a, 2026, Month.NOVEMBER, 40, EstadoMensalidade.FATURADO),
                mens(a, 2026, Month.DECEMBER, 40, EstadoMensalidade.POR_EMITIR));

        Studio com = new Studio();
        com.setLembreteMensalidade(true);
        Studio sem = new Studio();

        assertThat(svc.mensalidadesPagaveis(ms, com, LocalDate.of(2026, 11, 29))).hasSize(1);
        assertThat(svc.mensalidadesPagaveis(ms, com, LocalDate.of(2026, 11, 30))).hasSize(2);
        assertThat(svc.mensalidadesPagaveis(ms, sem, LocalDate.of(2026, 11, 30))).hasSize(1);
        assertThat(svc.mensalidadesPagaveis(ms, null, LocalDate.of(2026, 11, 30))).hasSize(1);
    }
}
