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

    /** Botão de ação "Ver" — cinzento neutro, ícone de olho. */
    public static Button botaoVer(String titulo, ComponentEventListener<ClickEvent<Button>> onClick) {
        return botaoAcao(VaadinIcon.EYE, "btn-neutral", titulo != null ? titulo : "Ver", onClick);
    }
}
