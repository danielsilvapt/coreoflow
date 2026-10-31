package pt.studioflow.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rede de segurança para o webhook do Mollie: se a notificação não chegar (falha de rede,
 * app em restart) e o cliente fechar o browser sem voltar do checkout, o pagamento ficaria
 * PENDENTE para sempre. De 10 em 10 minutos confirma-se junto do Mollie o estado dos
 * pagamentos pendentes dos últimos 14 dias (Multibanco/transferência podem demorar dias);
 * os que ficarem pagos marcam as mensalidades tal como o webhook faria.
 */
@Component
public class ReverificacaoPagamentosTask {

    private final PagamentoOnlineService pagamentoOnlineService;

    public ReverificacaoPagamentosTask(PagamentoOnlineService pagamentoOnlineService) {
        this.pagamentoOnlineService = pagamentoOnlineService;
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 2 * 60 * 1000)
    public void reverificarPendentes() {
        for (String molliePaymentId : pagamentoOnlineService.molliePaymentIdsPendentes(
                LocalDateTime.now().minusDays(14))) {
            try {
                // Cada pagamento na sua transação: uma falha não impede os restantes.
                pagamentoOnlineService.confirmarPagamentoMollie(molliePaymentId);
            } catch (Exception e) {
                System.err.println("Não foi possível confirmar o pagamento Mollie " + molliePaymentId
                        + ": " + e.getMessage());
            }
        }
    }
}
