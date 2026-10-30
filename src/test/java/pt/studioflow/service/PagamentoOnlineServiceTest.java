package pt.studioflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pagamentos online de mensalidades e do 1º pagamento de inscrições/renovações:
 * o estado vem sempre do Mollie e só um pagamento confirmado marca mensalidades
 * como pagas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ PagamentoOnlineService.class, MensalidadeConfig.class })
class PagamentoOnlineServiceTest {

    @Autowired private StudioRepository studioRepository;
    @Autowired private ModalidadeRepository modalidadeRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private AlunoRepository alunoRepository;
    @Autowired private MensalidadeRepository mensalidadeRepository;
    @Autowired private PagamentoOnlineRepository pagamentoOnlineRepository;
    @Autowired private PagamentoOnlineService pagamentoOnlineService;

    @MockBean private MollieApiService mollieApiService;

    private Studio studio;
    private Turma turma;
    private Aluno aluno;

    @BeforeEach
    void preparar() {
        studio = new Studio();
        studio.setNome("Studio Online");
        studio.setSlug("studio-online-" + System.nanoTime());
        studio.setAtivo(true);
        studio.setMollieApiKey("test_abc");
        studio.setDiaLimitePagamento(null); // sem multa/dívida automática a baralhar os valores
        studio = studioRepository.save(studio);

        Modalidade modalidade = new Modalidade();
        modalidade.setDescricao("Ballet");
        modalidade.setStudio(studio);
        modalidade = modalidadeRepository.save(modalidade);

        turma = new Turma();
        turma.setCodigo("ON1");
        turma.setDescricao("Ballet I");
        turma.setModalidade(modalidade);
        turma.setStudio(studio);
        turma.setAtivo(true);
        turma = turmaRepository.save(turma);

        aluno = new Aluno();
        aluno.setNomeCompleto("Aluno Online");
        aluno.setStudio(studio);
        aluno.setStatus(Aluno.AlunoStatus.ATIVO);
        aluno = alunoRepository.save(aluno);
    }

    private Mensalidade mensalidade(YearMonth ym, EstadoMensalidade estado, double valor) {
        Mensalidade m = new Mensalidade();
        m.setAluno(aluno);
        m.setTurma(turma);
        m.setAno(ym.getYear());
        m.setMes(ym.getMonth());
        m.setEstado(estado);
        m.setValor(valor);
        m.setStudio(studio);
        return mensalidadeRepository.save(m);
    }

    private void mollieCria(String paymentId) {
        when(mollieApiService.criarPagamento(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new MollieApiService.PagamentoCriado(paymentId, "https://mollie/checkout/" + paymentId));
    }

    @Test
    void mensalidadesPagaveis_soEmAbertoComValorAteAoMesCorrente() {
        YearMonth agora = YearMonth.now();
        Mensalidade atrasada = mensalidade(agora.minusMonths(1), EstadoMensalidade.FATURADO, 40);
        Mensalidade doMes = mensalidade(agora, EstadoMensalidade.POR_EMITIR, 40);
        mensalidade(agora.minusMonths(2), EstadoMensalidade.PAGO, 40);
        mensalidade(agora.plusMonths(1), EstadoMensalidade.POR_EMITIR, 40);
        mensalidade(agora.minusMonths(3), EstadoMensalidade.POR_EMITIR, 0);

        List<Mensalidade> pagaveis = pagamentoOnlineService.mensalidadesPagaveis(
                mensalidadeRepository.findByAluno(aluno));

        assertThat(pagaveis).extracting(Mensalidade::getId).containsExactly(atrasada.getId(), doMes.getId());
    }

    @Test
    void pagamentoDeMensalidades_soMarcaPagoQuandoOMollieConfirma() {
        Mensalidade m = mensalidade(YearMonth.now(), EstadoMensalidade.FATURADO, 40);
        mollieCria("tr_mens");

        PagamentoOnlineService.Checkout checkout = pagamentoOnlineService.criarPagamentoMensalidades(
                aluno, studio, List.of(m), "http://x/portal?pagamento=", "http://x/webhook");
        assertThat(checkout.checkoutUrl()).isEqualTo("https://mollie/checkout/tr_mens");
        assertThat(pagamentoOnlineRepository.findById(checkout.pagamentoId()).get().getValor())
                .isEqualByComparingTo(BigDecimal.valueOf(40));

        // Webhook com o pagamento ainda em aberto: nada muda.
        when(mollieApiService.verificarEstado(any(), eq("tr_mens"))).thenReturn(EstadoPagamentoCredito.PENDENTE);
        assertThat(pagamentoOnlineService.confirmarPagamentoMollie("tr_mens")).isTrue();
        assertThat(mensalidadeRepository.findById(m.getId()).get().getEstado()).isEqualTo(EstadoMensalidade.FATURADO);

        when(mollieApiService.verificarEstado(any(), eq("tr_mens"))).thenReturn(EstadoPagamentoCredito.PAGO);
        pagamentoOnlineService.confirmarPagamentoMollie("tr_mens");

        Mensalidade paga = mensalidadeRepository.findById(m.getId()).get();
        assertThat(paga.getEstado()).isEqualTo(EstadoMensalidade.PAGO);
        assertThat(paga.getDiaPagamento()).isEqualTo(LocalDate.now());
        assertThat(paga.getObservacoes()).isEqualTo(PagamentoOnlineService.OBS_PAGO_ONLINE);
    }

    @Test
    void webhookDeOutroPagamento_naoEDesteServico() {
        assertThat(pagamentoOnlineService.confirmarPagamentoMollie("tr_desconhecido")).isFalse();
    }

    @Test
    void pagamentoDaInscricao_aoAceitarMarcaSoOPrimeiroMesDoPeriodo() {
        aluno.setStatus(Aluno.AlunoStatus.PENDENTE);
        aluno = alunoRepository.save(aluno);
        mollieCria("tr_insc");
        pagamentoOnlineService.criarPagamentoPedido(aluno, studio, PagamentoOnline.Tipo.INSCRICAO, 55.0,
                "http://x/inscricao?pagamento=", "http://x/webhook");
        when(mollieApiService.verificarEstado(any(), eq("tr_insc"))).thenReturn(EstadoPagamentoCredito.PAGO);
        pagamentoOnlineService.confirmarPagamentoMollie("tr_insc"); // pago antes de o pedido ser aceite

        // Aceitação: mensalidades geradas a partir do início do período letivo.
        YearMonth inicio = MensalidadeService.inicioPeriodoLetivo(LocalDate.now());
        Mensalidade primeira = mensalidade(inicio, EstadoMensalidade.POR_EMITIR, 40);
        Mensalidade segunda = mensalidade(inicio.plusMonths(1), EstadoMensalidade.POR_EMITIR, 40);
        pagamentoOnlineService.aplicarPagamentosDoPedido(aluno);

        assertThat(mensalidadeRepository.findById(primeira.getId()).get().getEstado()).isEqualTo(EstadoMensalidade.PAGO);
        assertThat(mensalidadeRepository.findById(segunda.getId()).get().getEstado()).isEqualTo(EstadoMensalidade.POR_EMITIR);
        assertThat(pagamentoOnlineRepository.findByMolliePaymentId("tr_insc").get().isAplicado()).isTrue();

        // Aplicar de novo não marca o mês seguinte.
        pagamentoOnlineService.aplicarPagamentosDoPedido(aluno);
        assertThat(mensalidadeRepository.findById(segunda.getId()).get().getEstado()).isEqualTo(EstadoMensalidade.POR_EMITIR);
    }

    @Test
    void pagamentoDaInscricao_comPrimeiraMensalidadeOferecida_naoMarcaOMesSeguinte() {
        mollieCria("tr_taxa");
        pagamentoOnlineService.criarPagamentoPedido(aluno, studio, PagamentoOnline.Tipo.INSCRICAO, 15.0,
                "http://x/inscricao?pagamento=", "http://x/webhook");
        YearMonth inicio = MensalidadeService.inicioPeriodoLetivo(LocalDate.now());
        mensalidade(inicio, EstadoMensalidade.PAGO, 0); // 1ª mensalidade oferecida
        Mensalidade segunda = mensalidade(inicio.plusMonths(1), EstadoMensalidade.POR_EMITIR, 40);

        when(mollieApiService.verificarEstado(any(), eq("tr_taxa"))).thenReturn(EstadoPagamentoCredito.PAGO);
        pagamentoOnlineService.confirmarPagamentoMollie("tr_taxa");

        assertThat(mensalidadeRepository.findById(segunda.getId()).get().getEstado()).isEqualTo(EstadoMensalidade.POR_EMITIR);
    }

    @Test
    void semMollieConfigurado_naoCriaPagamento() {
        studio.setMollieApiKey(null);
        studioRepository.save(studio);
        Mensalidade m = mensalidade(YearMonth.now(), EstadoMensalidade.FATURADO, 40);

        assertThatThrownBy(() -> pagamentoOnlineService.criarPagamentoMensalidades(
                aluno, studio, List.of(m), "http://x/portal?pagamento=", "http://x/webhook"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(pagamentoOnlineRepository.findAll()).isEmpty();
    }
}
