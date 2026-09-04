package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import pt.studioflow.model.Inquerito;
import pt.studioflow.model.RespostaInquerito;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.InqueritoRepository;
import pt.studioflow.repository.RespostaInqueritoRepository;
import pt.studioflow.util.InqueritoPerguntas;
import pt.studioflow.util.InqueritoPerguntas.Pergunta;
import pt.studioflow.util.InqueritoPerguntas.Tipo;
import pt.studioflow.util.LogoUrl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Página pública onde um aluno/encarregado de educação responde a um
 * {@link Inquerito} através do link enviado por email (ver InqueritosView).
 * Resposta anónima: não fica associada a nenhum {@link pt.studioflow.model.Aluno}.
 */
@Route("inquerito")
@AnonymousAllowed
public class ResponderInqueritoView extends VerticalLayout implements BeforeEnterObserver {

    private final InqueritoRepository inqueritoRepo;
    private final RespostaInqueritoRepository respostaRepo;

    private final VerticalLayout card = new VerticalLayout();

    public ResponderInqueritoView(InqueritoRepository inqueritoRepo, RespostaInqueritoRepository respostaRepo) {
        this.inqueritoRepo = inqueritoRepo;
        this.respostaRepo = respostaRepo;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setPadding(true);
        getStyle().set("background-color", "#f8f9fa");

        card.setMaxWidth("650px");
        card.setWidth("100%");
        card.getStyle()
                .set("background", "white")
                .set("padding", "min(2.5em, 5vw)")
                .set("border-radius", "15px")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.1)");
        add(card);

        String idParam = event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("id", List.of()).stream().findFirst().orElse(null);

        Inquerito inquerito = null;
        if (idParam != null) {
            try {
                inquerito = inqueritoRepo.findById(Long.parseLong(idParam)).orElse(null);
            } catch (NumberFormatException ignored) {
                // link inválido - inquerito continua null
            }
        }

        if (inquerito == null) {
            mostrarErro("Inquérito não encontrado.", "Verifica se o link está completo e correto.");
            return;
        }
        if (inquerito.getEstado() != Inquerito.Estado.ENVIADO) {
            mostrarErro("Inquérito indisponível.",
                    inquerito.getEstado() == Inquerito.Estado.FECHADO
                            ? "Este inquérito já foi encerrado. Obrigado pelo teu interesse!"
                            : "Este inquérito ainda não está disponível para resposta.");
            return;
        }

        mostrarFormulario(inquerito);
    }

    private void mostrarErro(String titulo, String mensagem) {
        card.add(new H2(titulo), new Span(mensagem));
    }

    private void mostrarFormulario(Inquerito inquerito) {
        Studio studio = inquerito.getStudio();

        if (studio != null) {
            String logoSrc = (studio.getLogoPath() != null && !studio.getLogoPath().isBlank())
                    ? LogoUrl.comVersao(studio.getLogoPath())
                    : "images/logo-coreoflow.png";
            Image logo = new Image(logoSrc, studio.getNome());
            logo.setWidth("min(180px, 40vw)");
            card.add(logo);
        }

        H2 titulo = new H2(inquerito.getTitulo());
        Span subtitulo = new Span("A tua opinião é importante para " +
                (studio != null ? studio.getNome() : "nós") + ".");
        VerticalLayout header = new VerticalLayout(titulo, subtitulo);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(false);
        header.setPadding(false);
        card.add(header);

        List<Pergunta> perguntas = InqueritoPerguntas.parse(inquerito.getPerguntas());

        Map<String, RadioButtonGroup<Integer>> camposEscala = new LinkedHashMap<>();
        Map<String, TextArea> camposTexto = new LinkedHashMap<>();

        for (Pergunta p : perguntas) {
            Span pergLabel = new Span(p.texto());
            pergLabel.getStyle().set("font-weight", "600").set("display", "block").set("margin-top", "8px");

            if (p.tipo() == Tipo.ESCALA) {
                RadioButtonGroup<Integer> grupo = new RadioButtonGroup<>();
                grupo.setItems(1, 2, 3, 4, 5);
                grupo.getStyle().set("flex-wrap", "wrap");
                camposEscala.put(p.id(), grupo);
                card.add(pergLabel, grupo);
            } else {
                TextArea area = new TextArea();
                area.setWidthFull();
                area.setMaxLength(1000);
                camposTexto.put(p.id(), area);
                card.add(pergLabel, area);
            }
        }

        Button submeter = new Button("Enviar Respostas");
        submeter.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        submeter.setWidthFull();
        submeter.getStyle().set("margin-top", "16px");
        submeter.addClickListener(e -> submeter(inquerito, camposEscala, camposTexto));

        card.add(submeter);
    }

    private void submeter(Inquerito inquerito, Map<String, RadioButtonGroup<Integer>> camposEscala,
            Map<String, TextArea> camposTexto) {
        for (RadioButtonGroup<Integer> grupo : camposEscala.values()) {
            if (grupo.isEmpty()) {
                Notification.show("Por favor responde a todas as perguntas de avaliação.", 4000,
                        Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
        }

        Map<String, String> respostas = new LinkedHashMap<>();
        camposEscala.forEach((id, grupo) -> respostas.put(id, String.valueOf(grupo.getValue())));
        camposTexto.forEach((id, area) -> {
            String valor = area.getValue();
            if (valor != null && !valor.isBlank()) respostas.put(id, valor.trim());
        });

        RespostaInquerito resposta = new RespostaInquerito();
        resposta.setInquerito(inquerito);
        resposta.setRespostas(InqueritoPerguntas.toRespostasJson(respostas));
        respostaRepo.save(resposta);

        inquerito.setRespostasCount(inquerito.getRespostasCount() + 1);
        inqueritoRepo.save(inquerito);

        mostrarSucesso();
    }

    private void mostrarSucesso() {
        card.removeAll();
        VerticalLayout layout = new VerticalLayout(
                new Icon(VaadinIcon.CHECK_CIRCLE),
                new H2("Obrigado pela tua resposta!"),
                new Span("A tua opinião foi registada com sucesso."));
        layout.setAlignItems(Alignment.CENTER);
        card.add(layout);
    }
}
