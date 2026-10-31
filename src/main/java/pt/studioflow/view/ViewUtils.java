package pt.studioflow.view;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Studio;

/**
 * Utilitários de UI partilhados por todas as views.
 * Garante uniformidade visual (cor primária do estúdio, posição dos botões).
 */
public final class ViewUtils {

    private ViewUtils() {}

    /** Cor primária do estúdio atual, ou azul padrão se não houver estúdio. */
    public static String corPrimaria() {
        Studio s = TenantContext.getCurrentStudio();
        return (s != null && s.getCorPrimaria() != null) ? s.getCorPrimaria() : "#2F6FD6";
    }

    /**
     * Cria o botão "Novo …" com a cor primária do estúdio (via tema: --lumo-primary-color
     * é derivada da cor do estúdio). Usar para todos os botões de criação nas views.
     */
    public static Button botaoNovo(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        Button btn = new Button(label, new Icon(VaadinIcon.PLUS), onClick);
        btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return btn;
    }

    /**
     * Toolbar padrão: pesquisa à esquerda, botão "Novo" à direita.
     * Aspeto definido pela classe .cf-toolbar do tema.
     */
    public static HorizontalLayout toolbar(Component esquerda, Component direita) {
        HorizontalLayout toolbar = new HorizontalLayout(esquerda, direita);
        toolbar.setWidthFull();
        toolbar.addClassName("cf-toolbar");
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return toolbar;
    }

    /**
     * Toolbar padrão apenas com botão "Novo" à direita (sem pesquisa).
     */
    public static HorizontalLayout toolbar(Component direita) {
        HorizontalLayout spacer = new HorizontalLayout();
        spacer.setWidthFull();
        return toolbar(spacer, direita);
    }

    // =========================
    // BOTÕES DE AÇÃO (grelhas)
    // =========================
    // Estilo único de botão de ação (ícone, fundo suave) usado em todas as grelhas.
    // O CSS (.action-btn, .btn-edit, .btn-del, .btn-view, .btn-warn, .btn-neutral,
    // .btn-purple, .btn-vendus, .btn-obs) está no tema
    // src/main/frontend/themes/coreoflow/styles.css.

    /**
     * Mantido por compatibilidade: o CSS dos botões de ação passou para o tema
     * e é carregado sempre, por isso já não é preciso injetar nada.
     */
    public static void injetarEstiloBotoesAcao() {
        // sem efeito — ver tema coreoflow
    }

    /** Botão de ação genérico: ícone redondo com fundo suave (ver classes .btn-* acima). */
    public static Button botaoAcao(VaadinIcon icone, String classeCor, String titulo,
            ComponentEventListener<ClickEvent<Button>> onClick) {
        Button btn = new Button(new Icon(icone), onClick);
        btn.addClassNames("action-btn", classeCor);
        if (titulo != null) btn.getElement().setAttribute("title", titulo);
        return btn;
    }

    /** Botão de ação "Editar" — azul, ícone de lápis. Estilo padrão em toda a app. */
    public static Button botaoEditar(ComponentEventListener<ClickEvent<Button>> onClick) {
        return botaoAcao(VaadinIcon.EDIT, "btn-edit", "Editar", onClick);
    }

    /** Botão de ação "Eliminar" — vermelho, ícone de caixote. Estilo padrão em toda a app. */
    public static Button botaoEliminar(ComponentEventListener<ClickEvent<Button>> onClick) {
        return botaoAcao(VaadinIcon.TRASH, "btn-del", "Eliminar", onClick);
    }

    // =========================
    // CARTÕES DE ESTATÍSTICA
    // =========================

    /**
     * Converte as cores "históricas" usadas nas vistas no tom semântico do tema
     * (sucesso / erro / info / violeta / aviso / neutro).
     */
    public static String tomDaCor(String cor) {
        if (cor == null) return "neutral";
        return switch (cor.trim().toLowerCase()) {
            case "#1e8e3e", "#2e7d32", "#27ae60", "#2ecc71", "#16a085" -> "success";
            case "#d93025", "#c62828", "#d32f2f", "#e74c3c", "#c0392b" -> "error";
            case "#1967d2", "#1976d2", "#4a90e2", "#2980b9" -> "info";
            case "#8e24aa", "#8430ce", "#6d1b7b", "#8e44ad" -> "violet";
            case "#ff5d13", "#e65100", "#e67e22", "#f39c12", "#ffa000" -> "warning";
            default -> "neutral";
        };
    }

    /**
     * Cartão de estatística padrão (título em versalete, valor grande, ícone num quadrado
     * com o tom semântico). O {@code valor} é mantido pela view para atualizações.
     */
    public static com.vaadin.flow.component.html.Div statCard(String titulo,
            com.vaadin.flow.component.html.Span valor, String cor, VaadinIcon icone) {
        String tom = tomDaCor(cor);
        com.vaadin.flow.component.html.Div card = new com.vaadin.flow.component.html.Div();
        card.addClassName("stat-card");
        Icon i = icone.create();
        i.addClassNames("cf-stat-icon", "cf-tone-" + tom);
        com.vaadin.flow.component.html.Span t = new com.vaadin.flow.component.html.Span(titulo);
        t.addClassName("stat-title");
        valor.addClassName("stat-value");
        if (!"neutral".equals(tom)) {
            valor.addClassName("cf-tone-" + tom);
        }
        card.add(i, t, valor);
        return card;
    }

    /** Linha de cartões de estatística, responsiva (grelha auto-fit). */
    public static com.vaadin.flow.component.html.Div statsRow(Component... cards) {
        com.vaadin.flow.component.html.Div row = new com.vaadin.flow.component.html.Div(cards);
        row.addClassName("cf-stats");
        return row;
    }

    /** Botão de ação "Ver" — cinzento neutro, ícone de olho. */
    public static Button botaoVer(String titulo, ComponentEventListener<ClickEvent<Button>> onClick) {
        return botaoAcao(VaadinIcon.EYE, "btn-neutral", titulo != null ? titulo : "Ver", onClick);
    }
}
