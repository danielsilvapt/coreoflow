package pt.studioflow.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pt.studioflow.service.CompraCreditoService;
import pt.studioflow.service.PagamentoOnlineService;

/**
 * Webhook do Mollie para confirmar pagamentos de aulas avulso/packs e de
 * inscrições, renovações e mensalidades ({@link PagamentoOnlineService}). O Mollie
 * chama este endpoint só com o "id" do pagamento — o estado real é sempre
 * confirmado por uma chamada de volta à API do Mollie (nunca se confia no
 * corpo do webhook em si), ver {@link CompraCreditoService#confirmarPagamentoMollie}.
 */
@RestController
@RequestMapping("/api/mollie")
public class MollieWebhookController {

    private final CompraCreditoService compraCreditoService;
    private final PagamentoOnlineService pagamentoOnlineService;

    public MollieWebhookController(CompraCreditoService compraCreditoService,
            PagamentoOnlineService pagamentoOnlineService) {
        this.compraCreditoService = compraCreditoService;
        this.pagamentoOnlineService = pagamentoOnlineService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestParam("id") String paymentId) {
        if (!pagamentoOnlineService.confirmarPagamentoMollie(paymentId)) {
            compraCreditoService.confirmarPagamentoMollie(paymentId);
        }
        return ResponseEntity.ok().build();
    }
}
