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
        return (s != null && s.getCorPrimaria() != null) ? s.getCorPrimaria() : "#4A90E2";
    }

    /**
     * Cria o botão "Novo …" com a cor primária do estúdio.
     * Usar para todos os botões de criação nas views.
     */
    public static Button botaoNovo(String label, ComponentEventListener<ClickEvent<Button>> onClick) {
        Button btn = new Button(label, new Icon(VaadinIcon.PLUS), onClick);
        btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btn.getStyle().set("background-color", corPrimaria()).set("color", "white");
        return btn;
    }

    /**
     * Toolbar padrão: pesquisa à esquerda, botão "Novo" à direita.
     * Fundo branco, borda em baixo, padding uniforme.
     */
    public static HorizontalLayout toolbar(Component esquerda, Component direita) {
        HorizontalLayout toolbar = new HorizontalLayout(esquerda, direita);
        toolbar.setWidthFull();
        toolbar.setPadding(true);
        toolbar.getStyle()
                .set("background", "white")
                .set("border-bottom", "1px solid #e0e0e0")
                .set("padding", "12px 20px");
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
}
