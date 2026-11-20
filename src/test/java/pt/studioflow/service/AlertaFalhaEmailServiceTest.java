package pt.studioflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import pt.studioflow.model.RegistoEmailDestinatario.EstadoEntrega;

class AlertaFalhaEmailServiceTest {

    private static String causa(EstadoEntrega e, String motivo) {
        return AlertaFalhaEmailService.diagnosticar(e, motivo).causa();
    }

    @Test
    void diagnosticaMotivosComuns() {
        assertThat(causa(EstadoEntrega.DEVOLVIDO_TEMP, "452 4.2.2 The email account that you tried to reach is over quota"))
                .isEqualTo("Caixa de correio cheia");
        assertThat(causa(EstadoEntrega.DEVOLVIDO, "550 5.1.1 The email account that you tried to reach does not exist"))
                .isEqualTo("O endereço de email não existe");
        assertThat(causa(EstadoEntrega.DEVOLVIDO, "Domain not found: gmial.com"))
                .isEqualTo("O domínio do email não existe");
        assertThat(causa(EstadoEntrega.DEVOLVIDO, "550 5.7.1 Message rejected due to spam policy"))
                .isEqualTo("Rejeitado pelos filtros anti-spam do destinatário");
        assertThat(causa(EstadoEntrega.BLOQUEADO, null)).isEqualTo("Endereço bloqueado para envio");
        assertThat(causa(EstadoEntrega.INVALIDO, null)).isEqualTo("O endereço de email não existe");
    }

    @Test
    void soAlertaDevolucoesTemporariasComMotivoClaro() {
        assertThat(AlertaFalhaEmailService.deveAlertar(EstadoEntrega.DEVOLVIDO_TEMP, "mailbox full")).isTrue();
        assertThat(AlertaFalhaEmailService.deveAlertar(EstadoEntrega.DEVOLVIDO_TEMP, "421 try again later")).isFalse();
        assertThat(AlertaFalhaEmailService.deveAlertar(EstadoEntrega.DEVOLVIDO, null)).isTrue();
        assertThat(AlertaFalhaEmailService.deveAlertar(EstadoEntrega.ENTREGUE, null)).isFalse();
        assertThat(AlertaFalhaEmailService.deveAlertar(EstadoEntrega.SPAM, null)).isFalse();
    }

    @Test
    void cabecalhoDoBrevo() {
        assertThat(AlertaFalhaEmailService.studioDoCabecalho("cf-studio=7")).isEqualTo(7L);
        assertThat(AlertaFalhaEmailService.studioDoCabecalho(null)).isNull();
        assertThat(AlertaFalhaEmailService.eAlerta("cf-alerta")).isTrue();
    }
}
