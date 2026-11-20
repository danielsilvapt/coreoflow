package pt.studioflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.studioflow.model.RegistoEmail;
import pt.studioflow.model.RegistoEmailDestinatario;
import pt.studioflow.model.RegistoEmailDestinatario.EstadoEntrega;
import pt.studioflow.repository.RegistoEmailRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(HistoricoComunicacaoService.class)
class HistoricoComunicacaoServiceTest {

    @Autowired
    private HistoricoComunicacaoService service;
    @Autowired
    private RegistoEmailRepository registoRepo;
    @MockBean
    private AlertaFalhaEmailService alertaFalha;

    private final ObjectMapper om = new ObjectMapper();

    private RegistoEmail enviar(String assunto, String messageId, HistoricoComunicacaoService.Destinatario... dest) {
        RegistoEmail r = new RegistoEmail();
        r.setData(LocalDateTime.now());
        r.setStudioId(7L);
        r.setUtilizador("ana");
        r.setAssunto(assunto);
        r.setEstado(RegistoEmail.Estado.ENVIADO);
        r.setMessageId(RegistoEmail.normalizarMessageId(messageId));
        service.gravar(r, List.of(dest));
        return r;
    }

    private JsonNode evento(String event, String email, String messageId, String subject, String reason) throws Exception {
        var n = om.createObjectNode();
        n.put("event", event);
        n.put("email", email);
        if (messageId != null) n.put("message-id", messageId);
        if (subject != null) n.put("subject", subject);
        if (reason != null) n.put("reason", reason);
        n.put("ts_event", System.currentTimeMillis() / 1000);
        return n;
    }

    private EstadoEntrega estadoDe(RegistoEmail r, String email) {
        return service.destinatarios(r.getId()).stream()
                .filter(d -> d.getEmail().equals(email)).findFirst()
                .map(RegistoEmailDestinatario::getEstado).orElseThrow();
    }

    @Test
    void eventosAtualizamEstadoPorAlunoEContadores() throws Exception {
        RegistoEmail r = enviar("Ensaio sábado", "<abc-123@coreoflow.me>",
                new HistoricoComunicacaoService.Destinatario("Maria", "Maria@Mail.com"),
                new HistoricoComunicacaoService.Destinatario("João", "joao@mail.com"),
                new HistoricoComunicacaoService.Destinatario("Rui", "rui@mail.com"));

        service.processarEventoBrevo(evento("delivered", "maria@mail.com", "<ABC-123@coreoflow.me>", null, null));
        service.processarEventoBrevo(evento("opened", "maria@mail.com", "<abc-123@coreoflow.me>", null, null));
        // "delivered" que chega atrasado não faz recuar o estado
        service.processarEventoBrevo(evento("delivered", "maria@mail.com", "<abc-123@coreoflow.me>", null, null));
        service.processarEventoBrevo(evento("hard_bounce", "joao@mail.com", "<abc-123@coreoflow.me>", null,
                "550 mailbox does not exist"));

        assertThat(estadoDe(r, "maria@mail.com")).isEqualTo(EstadoEntrega.ABERTO);
        assertThat(estadoDe(r, "joao@mail.com")).isEqualTo(EstadoEntrega.DEVOLVIDO);
        assertThat(estadoDe(r, "rui@mail.com")).isEqualTo(EstadoEntrega.PENDENTE);
        // Só a falha do João gera alerta ao estúdio (uma vez)
        org.mockito.Mockito.verify(alertaFalha).alertarFalhaEntrega(7L, "joao@mail.com", null, EstadoEntrega.DEVOLVIDO,
                "550 mailbox does not exist");
        org.mockito.Mockito.verifyNoMoreInteractions(alertaFalha);

        RegistoEmail atualizado = registoRepo.findById(r.getId()).orElseThrow();
        assertThat(atualizado.getNumEntregues()).isEqualTo(1);
        assertThat(atualizado.getNumAbertos()).isEqualTo(1);
        assertThat(atualizado.getNumFalhados()).isEqualTo(1);

        assertThat(registoRepo.pesquisar(7L, null, null, true, null, null, PageRequest.of(0, 10))).hasSize(1);
        assertThat(registoRepo.pesquisar(7L, null, null, false, "joão", null, PageRequest.of(0, 10))).hasSize(1);
    }

    @Test
    void semMessageIdCorrespondenteUsaEmailEAssunto() throws Exception {
        RegistoEmail r = enviar("Aviso feriado", "<x@coreoflow.me>",
                new HistoricoComunicacaoService.Destinatario("Maria", "maria@mail.com"));

        boolean alterou = service.processarEventoBrevo(
                evento("delivered", "maria@mail.com", "<outro-id@smtp-relay.brevo.com>", "Aviso feriado", null));

        assertThat(alterou).isTrue();
        assertThat(estadoDe(r, "maria@mail.com")).isEqualTo(EstadoEntrega.ENTREGUE);
    }

    @Test
    void aceitaNomesDeEventosEmSnakeECamelCase() {
        assertThat(HistoricoComunicacaoService.mapearEvento("soft_bounce")).isEqualTo(EstadoEntrega.DEVOLVIDO_TEMP);
        assertThat(HistoricoComunicacaoService.mapearEvento("softBounces")).isEqualTo(EstadoEntrega.DEVOLVIDO_TEMP);
        assertThat(HistoricoComunicacaoService.mapearEvento("hardBounces")).isEqualTo(EstadoEntrega.DEVOLVIDO);
        assertThat(HistoricoComunicacaoService.mapearEvento("loadedByProxy")).isEqualTo(EstadoEntrega.ABERTO);
        assertThat(HistoricoComunicacaoService.mapearEvento("invalid_email")).isEqualTo(EstadoEntrega.INVALIDO);
        assertThat(HistoricoComunicacaoService.mapearEvento("requests")).isNull();
    }

    @Test
    void ignoraEventosDeEmailsQueNaoSaoComunicacoes() throws Exception {
        assertThat(service.processarEventoBrevo(
                evento("delivered", "admin@estudio.pt", "<desconhecido@x>", "Nova inscrição", null))).isFalse();
        assertThat(service.processarEventoBrevo(evento("request", "a@b.c", null, null, null))).isFalse();
    }
}
