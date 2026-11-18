package pt.studioflow.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.studioflow.service.HistoricoComunicacaoService;

/**
 * Webhook transacional do Brevo: recebe os eventos de entrega dos emails
 * (entregue, aberto, devolvido, bloqueado, spam...) e atualiza o histórico do
 * Centro de Comunicação.
 *
 * <p>Configurar no Brevo (Transactional → Settings → Webhook) com o URL
 * {@code https://app.coreoflow.me/api/brevo/webhook?token=<app.brevo.webhook-token>}.
 * O token também é aceite no cabeçalho {@code Authorization: Bearer <token>}.
 * Sem token configurado o endpoint recusa tudo.
 */
@RestController
@RequestMapping("/api/brevo")
public class BrevoWebhookController {

    private final HistoricoComunicacaoService historico;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.brevo.webhook-token:}")
    private String tokenEsperado;

    public BrevoWebhookController(HistoricoComunicacaoService historico) {
        this.historico = historico;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) String corpo) {
        String recebido = token;
        if ((recebido == null || recebido.isBlank()) && authorization != null
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            recebido = authorization.substring(7).trim();
        }
        if (!tokenValido(recebido)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (corpo == null || corpo.isBlank()) {
            return ResponseEntity.ok().build();
        }
        try {
            JsonNode raiz = objectMapper.readTree(corpo);
            // O Brevo envia um evento por pedido, ou uma lista quando os webhooks agrupados estão ativos.
            if (raiz.isArray()) {
                raiz.forEach(this::processar);
            } else {
                processar(raiz);
            }
        } catch (Exception e) {
            // Responde 200 na mesma: um corpo que não percebemos não fica melhor com novas tentativas.
            System.err.println("Webhook Brevo: corpo inválido - " + e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    private void processar(JsonNode ev) {
        try {
            historico.processarEventoBrevo(ev);
        } catch (Exception e) {
            System.err.println("Webhook Brevo: falha a processar evento - " + e.getMessage());
        }
    }

    private boolean tokenValido(String recebido) {
        if (tokenEsperado == null || tokenEsperado.isBlank() || recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(tokenEsperado.getBytes(StandardCharsets.UTF_8),
                recebido.getBytes(StandardCharsets.UTF_8));
    }
}
