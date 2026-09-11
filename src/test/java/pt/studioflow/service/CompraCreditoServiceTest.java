package pt.studioflow.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import pt.studioflow.model.*;
import pt.studioflow.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Compra de packs/aulas avulso: snapshot do pack, cálculo de validade e o
 * fallback para "pagar no estúdio" quando o Mollie não está configurado
 * (a compra nunca fica bloqueada por causa do pagamento).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CompraCreditoService.class)
class CompraCreditoServiceTest {

    @Autowired private StudioRepository studioRepository;
    @Autowired private AlunoRepository alunoRepository;
    @Autowired private PackAulaRepository packAulaRepository;
    @Autowired private CompraCreditoRepository compraCreditoRepository;
    @Autowired private CompraCreditoService compraCreditoService;

    @MockBean private EmailService emailService;
    @MockBean private MollieApiService mollieApiService;

    private Studio novoStudio(String mollieKey) {
        Studio s = new Studio();
        s.setNome("Studio Compra");
        s.setSlug("studio-compra-" + System.nanoTime());
        s.setAtivo(true);
        s.setMollieApiKey(mollieKey);
        return studioRepository.save(s);
    }

    private Aluno novoAluno(Studio studio) {
        Aluno a = new Aluno();
        a.setNomeCompleto("Aluno Compra");
        a.setEmail("compra" + System.nanoTime() + "@test.com");
        a.setStudio(studio);
        a.setStatus(Aluno.AlunoStatus.EXPERIMENTAL);
        a.setAtivo(true);
        return alunoRepository.save(a);
    }

    private PackAula novoPack(Studio studio, int numAulas, Integer validadeDias) {
        PackAula p = new PackAula();
        p.setStudio(studio);
        p.setNome("Pack " + numAulas);
        p.setNumAulas(numAulas);
        p.setPreco(BigDecimal.valueOf(50));
        p.setValidadeDias(validadeDias);
        p.setAtivo(true);
        return packAulaRepository.save(p);
    }

    @Test
    void criarCompra_semMollieConfigurado_caiParaPagarNoEstudioComAcessoImediato() {
        Studio studio = novoStudio(null); // sem chave Mollie
        Aluno aluno = novoAluno(studio);
        PackAula pack = novoPack(studio, 10, 90);

        doNothing().when(emailService).enviarEmailNotificacaoCompraAvulso(any());

        CompraCreditoService.ResultadoCompra resultado = compraCreditoService.criarCompra(
                aluno, pack, null, true, OrigemCompraCredito.JOIN_PUBLICO, "http://x/redirect", "http://x/webhook");

        assertThat(resultado.mollieCheckoutUrl).isNull();
        assertThat(resultado.compra.getMetodoPagamento()).isEqualTo(MetodoPagamentoCredito.MANUAL);
        assertThat(resultado.compra.getEstadoPagamento()).isEqualTo(EstadoPagamentoCredito.PENDENTE);
        // O crédito já está ativo mesmo com o pagamento pendente ("pagar no estúdio"):
        assertThat(resultado.compra.getCreditosRestantes()).isEqualTo(10);
        assertThat(resultado.compra.getValidadeAte()).isEqualTo(LocalDate.now().plusDays(90));
    }

    @Test
    void criarCompra_comMollieConfigurado_devolveCheckoutUrlDoGateway() {
        Studio studio = novoStudio("test_chave_mollie");
        Aluno aluno = novoAluno(studio);
        PackAula pack = novoPack(studio, 1, null);

        doNothing().when(emailService).enviarEmailNotificacaoCompraAvulso(any());
        when(mollieApiService.criarPagamento(any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new MollieApiService.PagamentoCriado("tr_123", "https://mollie.test/checkout/tr_123"));

        CompraCreditoService.ResultadoCompra resultado = compraCreditoService.criarCompra(
                aluno, pack, null, true, OrigemCompraCredito.JOIN_PUBLICO, "http://x/redirect", "http://x/webhook");

        assertThat(resultado.mollieCheckoutUrl).isEqualTo("https://mollie.test/checkout/tr_123");
        assertThat(resultado.compra.getMetodoPagamento()).isEqualTo(MetodoPagamentoCredito.MOLLIE);
        assertThat(resultado.compra.getMolliePaymentId()).isEqualTo("tr_123");
        assertThat(resultado.compra.getValidadeAte()).isNull(); // sem validadeDias no pack
    }

    @Test
    void confirmarPagamentoMollie_atualizaEstadoComABaseNaApi() {
        Studio studio = novoStudio("test_chave_mollie");
        Aluno aluno = novoAluno(studio);
        PackAula pack = novoPack(studio, 5, 30);

        CompraCredito compra = new CompraCredito();
        compra.setStudio(studio);
        compra.setAluno(aluno);
        compra.setPackAula(pack);
        compra.setNomePack(pack.getNome());
        compra.setNumAulasComprado(5);
        compra.setCreditosRestantes(5);
        compra.setPrecoPago(pack.getPreco());
        compra.setDataCompra(LocalDateTime.now());
        compra.setMetodoPagamento(MetodoPagamentoCredito.MOLLIE);
        compra.setEstadoPagamento(EstadoPagamentoCredito.PENDENTE);
        compra.setMolliePaymentId("tr_abc");
        compra.setOrigem(OrigemCompraCredito.JOIN_PUBLICO);
        compra = compraCreditoRepository.save(compra);

        when(mollieApiService.verificarEstado(any(), anyString())).thenReturn(EstadoPagamentoCredito.PAGO);

        compraCreditoService.confirmarPagamentoMollie("tr_abc");

        CompraCredito atualizado = compraCreditoRepository.findById(compra.getId()).orElseThrow();
        assertThat(atualizado.getEstadoPagamento()).isEqualTo(EstadoPagamentoCredito.PAGO);
    }
}
