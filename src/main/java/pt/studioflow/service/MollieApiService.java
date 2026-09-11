package pt.studioflow.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import pt.studioflow.model.EstadoPagamentoCredito;
import pt.studioflow.model.Studio;

/**
 * Integração com a API de pagamentos Mollie (https://docs.mollie.com/reference/v2/payments-api).
 * Cada estúdio usa a sua própria chave ({@link Studio#getMollieApiKey()}) — passada
 * explicitamente em cada chamada (não via {@link pt.studioflow.config.TenantContext},
 * porque o webhook do Mollie corre fora de uma sessão Vaadin).
 */
@Service
public class MollieApiService {

    private static final String BASE_URL = "https://api.mollie.com/v2/payments";

    public static class PagamentoCriado {
        public final String paymentId;
        public final String checkoutUrl;
        public PagamentoCriado(String paymentId, String checkoutUrl) {
            this.paymentId = paymentId;
            this.checkoutUrl = checkoutUrl;
        }
    }

    /** Cria um pagamento Mollie e devolve o id + URL de checkout para redirecionar o aluno. */
    @SuppressWarnings("unchecked")
    public PagamentoCriado criarPagamento(Studio studio, BigDecimal valor, String descricao,
            String redirectUrl, String webhookUrl) {
        String apiKey = studio.getMollieApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Mollie não está configurado para este estúdio.");
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("currency", "EUR");
        amount.put("value", String.format(Locale.US, "%.2f", valor));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("description", descricao);
        body.put("redirectUrl", redirectUrl);
        body.put("webhookUrl", webhookUrl);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(BASE_URL, entity, Map.class);
        Map<String, Object> resBody = response.getBody();
        if (resBody == null || resBody.get("id") == null) {
            throw new IllegalStateException("Resposta inesperada do Mollie ao criar pagamento.");
        }

        Map<String, Object> links = (Map<String, Object>) resBody.get("_links");
        Map<String, Object> checkout = links != null ? (Map<String, Object>) links.get("checkout") : null;
        String checkoutUrl = checkout != null ? (String) checkout.get("href") : null;

        return new PagamentoCriado((String) resBody.get("id"), checkoutUrl);
    }

    /** Consulta o estado real de um pagamento no Mollie (nunca confiar no corpo do webhook). */
    @SuppressWarnings("unchecked")
    public EstadoPagamentoCredito verificarEstado(Studio studio, String paymentId) {
        String apiKey = studio.getMollieApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Mollie não está configurado para este estúdio.");
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                BASE_URL + "/" + paymentId, HttpMethod.GET, entity, Map.class);
        Map<String, Object> resBody = response.getBody();
        String status = resBody != null ? (String) resBody.get("status") : null;
        return mapEstado(status);
    }

    private EstadoPagamentoCredito mapEstado(String molliestatus) {
        if (molliestatus == null) return EstadoPagamentoCredito.PENDENTE;
        return switch (molliestatus) {
            case "paid" -> EstadoPagamentoCredito.PAGO;
            case "canceled", "failed", "expired" -> EstadoPagamentoCredito.CANCELADO;
            default -> EstadoPagamentoCredito.PENDENTE; // open, pending, authorized
        };
    }
}
