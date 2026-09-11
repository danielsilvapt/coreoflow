package pt.studioflow.view;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
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

    // =========================
    // BOTÕES DE AÇÃO (grelhas)
    // =========================
    // Estilo único de botão de ação (ícone redondo, fundo suave) usado em todas
    // as grelhas da app — nasceu na view de Alunos e foi generalizado aqui para
    // não haver botões "Editar"/"Eliminar" com aparências diferentes consoante a view.

    private static final String ACOES_CSS_ID = "cf-action-btn-styles";

    private static final String ACOES_CSS =
            ".action-btn { border-radius: 10px; padding: 8px; min-width: 40px; height: 40px; "
                    + "transition: all 0.2s ease; border: 1px solid rgba(0,0,0,0.05); "
                    + "box-shadow: 0 2px 4px rgba(0,0,0,0.05); display: flex; align-items: center; justify-content: center; }"
                    + ".action-btn:hover { transform: translateY(-2px); box-shadow: 0 4px 8px rgba(0,0,0,0.1); }"
                    + ".btn-edit { background: #E8F0FE; color: #1967D2; }"
                    + ".btn-del { background: #FCE8E6; color: #D93025; }"
                    + ".btn-view { background: #E6F4EA; color: #1E8E3E; }"
                    + ".btn-warn { background: #FFF4E5; color: #FF5D13; }"
                    + ".btn-neutral { background: #F1F3F4; color: #5F6368; }"
                    + ".btn-purple { background: #F3E8FD; color: #8430CE; }"
                    // aliases históricos (Mensalidades/Financeiro), mesmas cores que btn-view/btn-warn
                    + ".btn-vendus { background: #E6F4EA; color: #1E8E3E; }"
                    + ".btn-obs { background: #FFF4E5; color: #FF5D13; }";

    /**
     * Garante que o CSS partilhado dos botões de ação está na página (idempotente:
     * verifica no próprio JS se a tag já existe, por isso é seguro chamar em toda view).
     */
    public static void injetarEstiloBotoesAcao() {
        UI ui = UI.getCurrent();
        if (ui == null) return;
        ui.getElement().executeJs(
                "if(!document.getElementById($1)){"
                        + "const s=document.createElement('style');s.id=$1;s.textContent=$0;"
                        + "document.head.appendChild(s);}",
                ACOES_CSS, ACOES_CSS_ID);
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
