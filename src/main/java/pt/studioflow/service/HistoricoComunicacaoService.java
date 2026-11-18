package pt.studioflow.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import pt.studioflow.model.RegistoEmail;
import pt.studioflow.model.RegistoEmailDestinatario;
import pt.studioflow.model.RegistoEmailDestinatario.EstadoEntrega;
import pt.studioflow.repository.RegistoEmailDestinatarioRepository;
import pt.studioflow.repository.RegistoEmailRepository;

/**
 * Histórico do Centro de Comunicação: grava cada envio com os seus destinatários
 * e atualiza o estado de entrega a cada aluno a partir dos eventos do webhook
 * transacional do Brevo (entregue, aberto, devolvido, bloqueado, spam...).
 */
@Service
public class HistoricoComunicacaoService {

    private static final Set<EstadoEntrega> ENTREGUES = EnumSet.of(EstadoEntrega.ENTREGUE, EstadoEntrega.ABERTO);
    private static final Set<EstadoEntrega> FALHAS = Arrays.stream(EstadoEntrega.values())
            .filter(EstadoEntrega::isFalha).collect(Collectors.toCollection(() -> EnumSet.noneOf(EstadoEntrega.class)));

    private final RegistoEmailRepository registoRepo;
    private final RegistoEmailDestinatarioRepository destRepo;

    public HistoricoComunicacaoService(RegistoEmailRepository registoRepo,
            RegistoEmailDestinatarioRepository destRepo) {
        this.registoRepo = registoRepo;
        this.destRepo = destRepo;
    }

    /** Aluno destinatário de um envio. */
    public record Destinatario(String nome, String email) {
    }

    /** Grava o envio e os seus destinatários. Nunca lança exceções. */
    @Transactional
    public void gravar(RegistoEmail registo, List<Destinatario> destinatarios) {
        try {
            registo.setNumDestinatarios(destinatarios.size());
            RegistoEmail r = registoRepo.save(registo);
            for (Destinatario d : destinatarios) {
                RegistoEmailDestinatario rd = new RegistoEmailDestinatario();
                rd.setRegistoId(r.getId());
                rd.setNome(truncar(d.nome(), 200));
                rd.setEmail(truncar(d.email().trim().toLowerCase(), 254));
                rd.setAtualizadoEm(r.getData());
                destRepo.save(rd);
            }
        } catch (Exception e) {
            System.err.println("Falha ao gravar histórico de comunicação: " + e.getMessage());
        }
    }

    public List<RegistoEmailDestinatario> destinatarios(Long registoId) {
        return destRepo.findByRegistoIdOrderByNomeAsc(registoId);
    }

    // ------------------------------------------------------------ webhook Brevo

    /**
     * Aplica um evento do webhook do Brevo. Ignora eventos que não dizem respeito
     * a comunicações do Centro de Comunicação (emails automáticos, cópias ao
     * estúdio/professor, eventos de teste).
     *
     * @return {@code true} se atualizou algum destinatário
     */
    @Transactional
    public boolean processarEventoBrevo(JsonNode ev) {
        EstadoEntrega novo = mapearEvento(texto(ev, "event"));
        String email = texto(ev, "email");
        if (novo == null || email == null) {
            return false;
        }
        email = email.trim().toLowerCase();
        LocalDateTime quando = dataEvento(ev);

        Long registoId = localizarRegisto(texto(ev, "message-id"), email, texto(ev, "subject"), quando);
        if (registoId == null) {
            return false;
        }

        List<RegistoEmailDestinatario> linhas = destRepo.findByRegistoIdAndEmail(registoId, email);
        boolean alterou = false;
        for (RegistoEmailDestinatario d : linhas) {
            if (novo == EstadoEntrega.ABERTO && d.getAbertoEm() == null) {
                d.setAbertoEm(quando);
            }
            EstadoEntrega atual = d.getEstado() != null ? d.getEstado() : EstadoEntrega.PENDENTE;
            if (novo.getNivel() >= atual.getNivel() && novo != atual) {
                d.setEstado(novo);
                d.setAtualizadoEm(quando);
                String motivo = texto(ev, "reason");
                if (motivo != null) {
                    d.setDetalhe(truncar(motivo, 1000));
                }
                alterou = true;
            }
            destRepo.save(d);
        }
        if (alterou) {
            atualizarContadores(registoId);
        }
        return alterou;
    }

    private Long localizarRegisto(String messageId, String email, String assunto, LocalDateTime quando) {
        String id = RegistoEmail.normalizarMessageId(messageId);
        if (id != null) {
            Optional<RegistoEmail> r = registoRepo.findFirstByMessageId(id);
            if (r.isPresent()) {
                return r.get().getId();
            }
        }
        // Plano B: o mesmo email com o mesmo assunto, enviado nos 7 dias anteriores ao evento.
        if (assunto != null) {
            List<Long> ids = destRepo.registosRecentesPorEmailEAssunto(email, assunto,
                    quando.minusDays(7), PageRequest.of(0, 1));
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
        }
        return null;
    }

    private void atualizarContadores(Long registoId) {
        registoRepo.findById(registoId).ifPresent(r -> {
            r.setNumEntregues((int) destRepo.countByRegistoIdAndEstadoIn(registoId, ENTREGUES));
            r.setNumAbertos((int) destRepo.countByRegistoIdAndEstadoIn(registoId, EnumSet.of(EstadoEntrega.ABERTO)));
            r.setNumFalhados((int) destRepo.countByRegistoIdAndEstadoIn(registoId, FALHAS));
            registoRepo.save(r);
        });
    }

    /** Nome do evento do Brevo → estado de entrega (null = ignorar). */
    static EstadoEntrega mapearEvento(String evento) {
        if (evento == null) {
            return null;
        }
        return switch (evento.trim().toLowerCase().replace(' ', '_').replace('-', '_')) {
            case "delivered" -> EstadoEntrega.ENTREGUE;
            case "opened", "unique_opened", "first_opening", "proxy_open", "unique_proxy_open", "click", "clicks" ->
                    EstadoEntrega.ABERTO;
            case "deferred" -> EstadoEntrega.ADIADO;
            case "soft_bounce", "soft_bounces" -> EstadoEntrega.DEVOLVIDO_TEMP;
            case "hard_bounce", "hard_bounces" -> EstadoEntrega.DEVOLVIDO;
            case "blocked" -> EstadoEntrega.BLOQUEADO;
            case "invalid_email", "invalid" -> EstadoEntrega.INVALIDO;
            case "spam", "complaint" -> EstadoEntrega.SPAM;
            case "error" -> EstadoEntrega.ERRO;
            default -> null; // request (enviado), unsubscribed, ...
        };
    }

    private static LocalDateTime dataEvento(JsonNode ev) {
        for (String campo : new String[] { "ts_event", "ts_epoch", "ts" }) {
            JsonNode n = ev.get(campo);
            if (n != null && n.canConvertToLong() && n.asLong() > 0) {
                long v = n.asLong();
                Instant i = v > 100_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
                return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
            }
        }
        return LocalDateTime.now();
    }

    private static String texto(JsonNode ev, String campo) {
        JsonNode n = ev.get(campo);
        if (n == null || n.isNull()) {
            return null;
        }
        String s = n.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static String truncar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
