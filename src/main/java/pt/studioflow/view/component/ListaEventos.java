package pt.studioflow.view.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import pt.studioflow.model.Convite;
import pt.studioflow.model.EstadoEvento;

import java.time.format.DateTimeFormatter;

/**
 * Cards de eventos ({@link Convite}) para as áreas do aluno e do professor —
 * lista cronológica simples, sem grelha de calendário.
 */
public final class ListaEventos {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private ListaEventos() {
    }

    /**
     * Um card de evento.
     *
     * @param c          o evento
     * @param infoTurmas texto opcional com as turmas participantes (ou {@code null})
     * @param trailing   componente opcional no fundo do card — o toggle "Tenho
     *                   interesse" (aluno) ou um badge com o nº de interessados
     *                   (professor); pode ser {@code null}
     */
    public static Component card(Convite c, String infoTurmas, Component trailing) {
        VerticalLayout card = new VerticalLayout();
        card.setSpacing(false);
        card.setPadding(true);
        card.getStyle()
                .set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.07)")
                .set("margin-bottom", "8px");

        Span nome = new Span(c.getEvento());
        nome.getStyle().set("font-weight", "700").set("font-size", "15px");

        EstadoEvento estado = c.getEstado() != null ? c.getEstado() : EstadoEvento.PLANEADO;
        Span badge = new Span(estado.getLabel());
        badge.getStyle().set("background", estado.getColor()).set("color", "white")
                .set("padding", "2px 8px").set("border-radius", "10px")
                .set("font-size", "11px").set("font-weight", "700").set("margin-left", "8px");

        HorizontalLayout cabecalho = new HorizontalLayout(nome, badge);
        cabecalho.setAlignItems(FlexComponent.Alignment.CENTER);
        cabecalho.getStyle().set("flex-wrap", "wrap");
        card.add(cabecalho);

        StringBuilder linha = new StringBuilder();
        if (c.getData() != null) {
            linha.append("📅 ").append(c.getData().format(DATA));
        }
        if (c.getHora() != null) {
            linha.append("  ·  🕒 ").append(c.getHora().format(HORA));
        }
        if (c.getLocal() != null && !c.getLocal().isBlank()) {
            linha.append("  ·  📍 ").append(c.getLocal());
        }
        if (linha.length() > 0) {
            Span detalhe = new Span(linha.toString());
            detalhe.getStyle().set("color", "#555").set("font-size", "13px").set("margin-top", "4px");
            card.add(detalhe);
        }

        if (infoTurmas != null && !infoTurmas.isBlank()) {
            Span turmas = new Span("👥 " + infoTurmas);
            turmas.getStyle().set("color", "#777").set("font-size", "12px").set("margin-top", "2px");
            card.add(turmas);
        }

        if (c.getObservacoes() != null && !c.getObservacoes().isBlank()) {
            Span obs = new Span(c.getObservacoes());
            obs.getStyle().set("color", "#666").set("font-size", "12px")
                    .set("font-style", "italic").set("margin-top", "4px");
            card.add(obs);
        }

        if (trailing != null) {
            Div wrap = new Div(trailing);
            wrap.getStyle().set("margin-top", "8px");
            card.add(wrap);
        }

        return card;
    }

    /** Texto de estado vazio. */
    public static Component vazio(String mensagem) {
        Span s = new Span(mensagem);
        s.getStyle().set("color", "#888");
        return s;
    }
}
