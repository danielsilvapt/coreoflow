package pt.studioflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;

import pt.studioflow.model.Aluno;

/**
 * Ligação Mollie → Vendus: pedidos feitos à API Vendus (cliente procurado/criado antes da
 * Fatura-Recibo) e quem fica como cliente na fatura.
 */
class VendusReciboTest {

    private static final String WS = "https://www.vendus.pt/ws/v1.1/";

    private VendusApiService vendus;
    private MockRestServiceServer server;

    @BeforeEach
    void preparar() {
        vendus = new VendusApiService();
        server = MockRestServiceServer.bindTo(vendus.rest).build();
    }

    @Test
    void clienteInexistenteECriadoComEmailEEnvioDeFaturas() {
        server.expect(requestTo(WS + "clients/?fiscal_id=123456789"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic a2V5Og==")) // "key:" — chave como utilizador
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"errors\":[{\"message\":\"No data\"}]}")
                        .contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(WS + "clients/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.fiscal_id").value("123456789"))
                .andExpect(jsonPath("$.name").value("Maria Mãe"))
                .andExpect(jsonPath("$.email").value("mae@exemplo.pt"))
                .andExpect(jsonPath("$.send_email").value("yes"))
                .andExpect(jsonPath("$.external_reference").value("CF-EE7"))
                .andRespond(withSuccess("{\"id\":\"555\"}", MediaType.APPLICATION_JSON));

        String id = vendus.obterOuCriarCliente("key", new VendusApiService.DadosCliente("123 456 789",
                "Maria Mãe", "mae@exemplo.pt", "Rua A", "2450-000", "Nazaré", "CF-EE7"));

        assertThat(id).isEqualTo("555");
        server.verify();
    }

    @Test
    void testarLigacaoUsaWsEMostraErroDeChaveInvalida() {
        server.expect(requestTo(WS + "registers/?per_page=100"))
                .andExpect(header("Authorization", "Basic a2V5Og=="))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"errors\":[{\"code\":\"A001\",\"message\":\"AUTH\"}]}")
                        .contentType(MediaType.APPLICATION_JSON));

        String r = vendus.testarLigacao(" key ");

        assertThat(r).startsWith("Falha na API").contains("AUTH (A001)");
        server.verify();
    }

    @Test
    void clienteExistenteNaoEDuplicadoMasPassaATerEmail() {
        server.expect(requestTo(WS + "clients/?fiscal_id=123456789"))
                .andRespond(withSuccess("[{\"id\":\"77\",\"fiscal_id\":\"123456789\",\"email\":\"\",\"send_email\":\"no\"}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(WS + "clients/77"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.email").value("a@b.pt"))
                .andExpect(jsonPath("$.send_email").value("yes"))
                .andRespond(withSuccess("{\"id\":\"77\"}", MediaType.APPLICATION_JSON));

        assertThat(vendus.obterOuCriarCliente("key", new VendusApiService.DadosCliente("123456789", "Ana",
                "a@b.pt", null, null, null, "CF-A1"))).isEqualTo("77");
        server.verify();
    }

    @Test
    void semNifProcuraPelaReferenciaDoCoreoflow() {
        server.expect(requestTo(WS + "clients/?external_reference=CF-A9"))
                .andRespond(withSuccess("[{\"id\":\"9\",\"external_reference\":\"CF-A9\",\"email\":\"x@y.pt\",\"send_email\":\"yes\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(vendus.obterOuCriarCliente("key", new VendusApiService.DadosCliente("999999990", "Rui",
                "x@y.pt", null, null, null, "CF-A9"))).isEqualTo("9");
        server.verify();
    }

    @Test
    void faturaReciboLevaPagamentoTaxaEReferencia() {
        server.expect(requestTo(WS + "documents/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.type").value("FR"))
                .andExpect(jsonPath("$.mode").value("tests"))
                .andExpect(jsonPath("$.register_id").value("12"))
                .andExpect(jsonPath("$.client.id").value("555"))
                .andExpect(jsonPath("$.items[0].gross_price").value("40.00"))
                .andExpect(jsonPath("$.items[0].qty").value(1))
                .andExpect(jsonPath("$.items[0].tax_id").value("ISE"))
                .andExpect(jsonPath("$.items[0].tax_exemption").value("M07"))
                .andExpect(jsonPath("$.payments[0].id").value("31"))
                .andExpect(jsonPath("$.payments[0].amount").value("40.00"))
                .andExpect(jsonPath("$.external_reference").value("CF-PO-3"))
                .andRespond(withSuccess("{\"id\":\"900\",\"number\":\"FR 01T2026/1\"}", MediaType.APPLICATION_JSON));

        VendusApiService.Documento doc = vendus.emitirFaturaRecibo("key", "555", "Mensalidade 11/2026 - Ana",
                new BigDecimal("40"), "31", "12", "ISE", "M07", "CF-PO-3", true);

        assertThat(doc.numero()).isEqualTo("FR 01T2026/1");
        server.verify();
    }

    @Test
    void semTaxaConfiguradaUsaAPredefinidaDaConta() {
        server.expect(requestTo(WS + "documents/"))
                .andExpect(jsonPath("$.items[0].tax_id").doesNotExist())
                .andExpect(jsonPath("$.register_id").doesNotExist())
                .andExpect(jsonPath("$.payments[0].id").doesNotExist())
                .andExpect(jsonPath("$.mode").value("normal"))
                .andRespond(withSuccess("{\"id\":\"1\",\"number\":\"FR 1\"}", MediaType.APPLICATION_JSON));

        vendus.emitirFaturaRecibo("key", "1", "X", new BigDecimal("10.5"), null, null, null, null, "CF-CC-1", false);
        server.verify();
    }

    @Test
    void erroDoVendusFicaLegivel() {
        server.expect(ExpectedCount.once(), requestTo(WS + "documents/"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body("{\"errors\":[{\"code\":\"D010\",\"message\":\"Invalid payment method\"}]}")
                        .contentType(MediaType.APPLICATION_JSON));

        try {
            vendus.emitirFaturaRecibo("key", "1", "X", BigDecimal.TEN, "x", null, null, null, "CF-PO-1", false);
            org.junit.jupiter.api.Assertions.fail("devia falhar");
        } catch (VendusApiService.VendusException e) {
            assertThat(e.getMessage()).isEqualTo("Vendus 422: Invalid payment method (D010)");
        }
    }

    @Test
    void documentoJaEmitidoEReaproveitado() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(WS + "documents/?")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .queryParam("external_reference", "CF-PO-3"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .queryParam("mode", "normal"))
                .andRespond(withSuccess("[{\"id\":\"900\",\"number\":\"FR 01P2026/5\",\"external_reference\":\"CF-PO-3\",\"status\":\"N\"}]",
                        MediaType.APPLICATION_JSON));

        assertThat(vendus.documentoPorReferencia("key", "CF-PO-3", false))
                .hasValueSatisfying(d -> assertThat(d.numero()).isEqualTo("FR 01P2026/5"));
        server.verify();
    }

    @Test
    void menorComEncarregadoFaturaAoEncarregado() {
        Aluno a = aluno();
        a.setDataNascimento(LocalDate.now().minusYears(10));
        a.setEncarregadoNome("Maria Mãe");
        a.setEncarregadoContribuinte("123456789");
        a.setEncarregadoEmail("mae@exemplo.pt");

        VendusApiService.DadosCliente c = ReciboVendusService.dadosFaturacao(a);

        assertThat(c.nome()).isEqualTo("Maria Mãe");
        assertThat(c.nif()).isEqualTo("123456789");
        assertThat(c.email()).isEqualTo("mae@exemplo.pt");
        assertThat(c.morada()).isEqualTo("Rua A");
        assertThat(c.referenciaExterna()).isEqualTo("CF-EE7");
    }

    @Test
    void adultoFaturaAoProprio() {
        Aluno a = aluno();
        a.setDataNascimento(LocalDate.now().minusYears(30));
        a.setEncarregadoNome("Não usado");

        VendusApiService.DadosCliente c = ReciboVendusService.dadosFaturacao(a);

        assertThat(c.nome()).isEqualTo("Ana Aluna");
        assertThat(c.nif()).isEqualTo("987654321");
        assertThat(c.email()).isEqualTo("ana@exemplo.pt");
        assertThat(c.referenciaExterna()).isEqualTo("CF-A7");
    }

    @Test
    void nifValido() {
        assertThat(VendusApiService.nifValido(" 123 456 789 ")).isEqualTo("123456789");
        assertThat(VendusApiService.nifValido("999999990")).isNull();
        assertThat(VendusApiService.nifValido("1234")).isNull();
        assertThat(VendusApiService.nifValido(null)).isNull();
    }

    private static Aluno aluno() {
        Aluno a = new Aluno();
        a.setId(7L);
        a.setNomeCompleto("Ana Aluna");
        a.setEmail("ana@exemplo.pt");
        a.setNumeroContribuinte("987654321");
        a.setMorada("Rua A");
        return a;
    }
}
