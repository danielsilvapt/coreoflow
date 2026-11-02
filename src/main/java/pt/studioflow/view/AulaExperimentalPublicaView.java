package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import pt.studioflow.model.AulaExperimental;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.service.AulaExperimentalService;
import pt.studioflow.service.AulaExperimentalService.Sessao;

import java.util.List;

/**
 * Página pública para marcar uma aula experimental: o interessado escolhe a turma e a
 * sessão (dia + hora) e deixa o contacto. URL: /aula-experimental?studio=slug
 */
@Route("aula-experimental")
@PageTitle("Marcar aula experimental")
@AnonymousAllowed
public class AulaExperimentalPublicaView extends VerticalLayout implements BeforeEnterObserver {

    /** Mostra só as próximas N sessões da turma, para a escolha caber num telemóvel. */
    private static final int MAX_SESSOES = 8;

    private final StudioRepository studioRepository;
    private final AulaExperimentalService service;
    private final VerticalLayout card = new VerticalLayout();
    private Studio studio;

    public AulaExperimentalPublicaView(StudioRepository studioRepository, AulaExperimentalService service) {
        this.studioRepository = studioRepository;
        this.service = service;
        addClassName("cf-exp-publica");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String slug = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("studio", List.of()).stream().findFirst().orElse(null);
        studio = slug != null && !slug.isBlank() ? studioRepository.findBySlugAndAtivoTrue(slug).orElse(null) : null;

        removeAll();
        if (studio == null) {
            add(new H2("Estúdio não encontrado."), new Span("O link é inválido ou o estúdio já não está ativo."));
            return;
        }
        construirBase();
        mostrarFormulario();
    }

    private void construirBase() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setPadding(true);

        String logoSrc = studio.getLogoPath() != null && !studio.getLogoPath().isBlank()
                ? pt.studioflow.util.LogoUrl.comVersao(studio.getLogoPath()) : "images/logo-coreoflow.png";
        Image logo = new Image(logoSrc, studio.getNome());
        logo.setWidth("min(150px, 36vw)");
        H2 titulo = new H2("Marca a tua aula experimental");
        titulo.getStyle().set("text-align", "center").set("margin", "8px 0 0");
        Span sub = new Span("Escolhe a turma e o dia. Recebemos o teu contacto e esperamos por ti!");
        sub.addClassName("cf-exp-publica-sub");

        VerticalLayout header = new VerticalLayout(logo, titulo, sub);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(false);
        header.setPadding(false);

        card.addClassName("cf-exp-publica-card");
        card.setWidth("min(520px, 100%)");
        add(header, card);
    }

    private void mostrarFormulario() {
        card.removeAll();
        List<Turma> turmas = service.turmasComSessoes(studio);
        if (turmas.isEmpty()) {
            card.add(new Span("De momento não há aulas disponíveis para marcação online. Contacta o estúdio"
                    + (studio.getEmailContacto() != null && !studio.getEmailContacto().isBlank()
                            ? " (" + studio.getEmailContacto() + ")" : "") + "."));
            return;
        }

        ComboBox<Turma> turma = new ComboBox<>("Turma / modalidade");
        turma.setItems(turmas);
        turma.setItemLabelGenerator(Turma::getDescricao);
        turma.setWidthFull();
        turma.setRequiredIndicatorVisible(true);

        RadioButtonGroup<Sessao> sessao = new RadioButtonGroup<>("Dia e hora");
        sessao.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
        sessao.setItemLabelGenerator(Sessao::label);
        sessao.setVisible(false);

        turma.addValueChangeListener(e -> {
            sessao.clear();
            if (e.getValue() == null) {
                sessao.setVisible(false);
                return;
            }
            List<Sessao> lista = service.sessoesDisponiveis(e.getValue());
            sessao.setItems(lista.stream().limit(MAX_SESSOES).toList());
            sessao.setVisible(true);
        });

        TextField nome = new TextField("Nome");
        nome.setWidthFull();
        nome.setRequiredIndicatorVisible(true);
        nome.setHelperText("Se for para uma criança, indica o nome dela");
        TextField telemovel = new TextField("Telemóvel");
        telemovel.setWidthFull();
        telemovel.setRequiredIndicatorVisible(true);
        telemovel.setPrefixComponent(VaadinIcon.PHONE.create());
        telemovel.getElement().setAttribute("inputmode", "tel");
        telemovel.getElement().setAttribute("autocomplete", "tel");
        EmailField email = new EmailField("Email (opcional)");
        email.setWidthFull();
        TextArea obs = new TextArea("Algo que devamos saber? (opcional)");
        obs.setWidthFull();
        obs.setMaxLength(500);
        obs.setPlaceholder("Idade, experiência anterior…");

        Checkbox consentimento = new Checkbox("Aceito que o estúdio me contacte sobre esta aula experimental.");

        Button marcar = new Button("Marcar aula experimental", VaadinIcon.CHECK.create());
        marcar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        marcar.setWidthFull();
        marcar.setDisableOnClick(true);
        marcar.addClickListener(e -> {
            try {
                if (telemovel.getValue() == null || telemovel.getValue().replaceAll("\\D", "").length() < 9) {
                    throw new IllegalArgumentException("Indica um número de telemóvel válido.");
                }
                if (!consentimento.getValue()) {
                    throw new IllegalArgumentException("Confirma que aceitas ser contactado.");
                }
                if (sessao.getValue() == null) {
                    throw new IllegalArgumentException("Escolhe a turma e o dia da aula.");
                }
                AulaExperimental ae = service.marcar(studio, new AulaExperimentalService.Pedido(nome.getValue(),
                        telemovel.getValue(), email.getValue(), sessao.getValue(),
                        AulaExperimental.Origem.ONLINE, obs.getValue()));
                mostrarConfirmacao(ae, sessao.getValue());
            } catch (IllegalArgumentException ex) {
                marcar.setEnabled(true);
                Notification.show(ex.getMessage(), 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        card.add(turma, sessao, nome, telemovel, email, obs, consentimento, marcar);
    }

    private void mostrarConfirmacao(AulaExperimental ae, Sessao sessao) {
        card.removeAll();
        H4 titulo = new H4("Aula marcada! 🎉");
        titulo.getStyle().set("margin", "0");
        Span quando = new Span(ae.getTurma().getDescricao() + " — " + sessao.label());
        quando.getStyle().set("font-weight", "600");
        Span texto = new Span("Obrigado, " + ae.getNomeCompleto().split(" ")[0]
                + ". Vem com roupa confortável e chega uns minutos antes. Se não puderes vir, avisa o estúdio.");
        texto.addClassName("cf-exp-publica-sub");
        Button outra = new Button("Marcar outra aula", e -> mostrarFormulario());
        outra.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        card.add(titulo, quando, texto, outra);
    }
}
