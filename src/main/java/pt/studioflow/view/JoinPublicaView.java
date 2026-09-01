package pt.studioflow.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import pt.studioflow.model.Studio;
import pt.studioflow.repository.StudioRepository;

import java.util.Collections;
import java.util.List;

/**
 * Ponto de entrada público único (equivalente ao "/join" do gestao-obidos-dance):
 * o candidato escolhe entre nova inscrição ou renovação de matrícula e é
 * encaminhado para a view correspondente, já existente e testada
 * (InscricaoPublicaView / RenovacaoPublicaView), mantendo o slug do estúdio.
 */
@Route("join")
@AnonymousAllowed
public class JoinPublicaView extends VerticalLayout implements BeforeEnterObserver {

    private final StudioRepository studioRepository;
    private Studio studioAtual;
    private String slug;

    public JoinPublicaView(StudioRepository studioRepository) {
        this.studioRepository = studioRepository;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        slug = event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("studio", List.of()).stream()
                .findFirst().orElse(null);

        if (slug != null && !slug.isBlank()) {
            studioAtual = studioRepository.findBySlugAndAtivoTrue(slug).orElse(null);
        }

        if (studioAtual == null) {
            removeAll();
            add(new H2("Estúdio não encontrado."),
                    new Span("O link é inválido ou o estúdio já não está ativo."));
            return;
        }

        construirUI();
    }

    private void construirUI() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setPadding(true);
        getStyle().set("background-color", "#f8f9fa");

        String logoSrc = (studioAtual.getLogoPath() != null && !studioAtual.getLogoPath().isBlank())
                ? pt.studioflow.util.LogoUrl.comVersao(studioAtual.getLogoPath())
                : "images/logo-coreoflow.png";
        Image logo = new Image(logoSrc, studioAtual.getNome());
        logo.setWidth("min(180px, 40vw)");

        H2 titulo = new H2("Bem-vindo(a) a " + studioAtual.getNome());
        Span subtitulo = new Span("O que pretendes fazer?");
        subtitulo.getStyle().set("color", "#7f8c8d");

        VerticalLayout header = new VerticalLayout(logo, titulo, subtitulo);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(false);
        header.getStyle().set("margin-bottom", "16px");

        VerticalLayout opcaoNova = criarOpcao(VaadinIcon.PLUS_CIRCLE, "Nova Inscrição",
                "Ainda não sou aluno/sócio deste estúdio", "#4A90E2",
                () -> UI.getCurrent().navigate("inscricao", new QueryParameters(
                        Collections.singletonMap("studio", Collections.singletonList(slug)))));

        VerticalLayout opcaoRenovar = criarOpcao(VaadinIcon.REFRESH, "Renovar Matrícula",
                "Já sou aluno/sócio e quero renovar para o novo período", "#27AE60",
                () -> UI.getCurrent().navigate("renovacao", new QueryParameters(
                        Collections.singletonMap("studio", Collections.singletonList(slug)))));

        VerticalLayout opcoes = new VerticalLayout(opcaoNova, opcaoRenovar);
        opcoes.setPadding(false);
        opcoes.setSpacing(false);
        opcoes.setWidth("min(420px, 90vw)");

        VerticalLayout card = new VerticalLayout(header, opcoes);
        card.setAlignItems(Alignment.CENTER);
        card.setWidth("min(480px, 92vw)");
        card.getStyle()
                .set("background", "white")
                .set("border-radius", "16px")
                .set("padding", "32px 24px")
                .set("box-shadow", "0 4px 24px rgba(0,0,0,0.08)");

        add(card);
    }

    private VerticalLayout criarOpcao(VaadinIcon vaadinIcon, String titulo, String descricao, String cor,
            Runnable onClick) {
        Button botao = new Button(titulo, vaadinIcon.create(), e -> onClick.run());
        botao.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        botao.setWidthFull();
        botao.getStyle()
                .set("height", "56px")
                .set("background", cor)
                .set("font-weight", "700");

        Span desc = new Span(descricao);
        desc.getStyle().set("font-size", "12px").set("color", "#7f8c8d");

        VerticalLayout wrapper = new VerticalLayout(botao, desc);
        wrapper.setPadding(false);
        wrapper.setSpacing(false);
        wrapper.setAlignItems(Alignment.CENTER);
        wrapper.getStyle().set("margin-bottom", "20px");
        return wrapper;
    }
}
