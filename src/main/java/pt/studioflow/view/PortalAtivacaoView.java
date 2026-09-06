package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.studioflow.model.ContaPortal;
import pt.studioflow.repository.ContaPortalRepository;

import java.util.List;
import java.util.Optional;

/**
 * Página pública onde o aluno/encarregado define a sua palavra-passe a partir
 * do link de convite enviado por email ({@link pt.studioflow.service.EmailService#enviarConvitePortal}).
 */
@Route("portal-ativar")
@AnonymousAllowed
@PageTitle("Ativar acesso | CoreoFlow")
public class PortalAtivacaoView extends VerticalLayout implements BeforeEnterObserver {

    private final ContaPortalRepository contaRepo;
    private final PasswordEncoder passwordEncoder;

    private String token;

    public PortalAtivacaoView(ContaPortalRepository contaRepo, PasswordEncoder passwordEncoder) {
        this.contaRepo = contaRepo;
        this.passwordEncoder = passwordEncoder;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        getStyle().set("background", "linear-gradient(135deg, #0f1f4b, #0e4d6e)");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> valores = event.getLocation().getQueryParameters().getParameters()
                .getOrDefault("token", List.of());
        this.token = valores.isEmpty() ? null : valores.get(0);
        render();
    }

    private void render() {
        removeAll();

        VerticalLayout card = new VerticalLayout();
        card.setWidth("400px");
        card.setMaxWidth("95vw");
        card.setAlignItems(Alignment.STRETCH);
        card.getStyle().set("background", "white").set("border-radius", "20px")
                .set("padding", "32px").set("box-shadow", "0 25px 50px -12px rgba(0,0,0,0.4)");

        Optional<ContaPortal> contaOpt = token == null ? Optional.empty() : contaRepo.findByTokenAtivacao(token);
        if (contaOpt.isEmpty() || !contaOpt.get().tokenValido(token)) {
            card.add(new H2("Link inválido ou expirado"));
            Span msg = new Span("Pede um novo convite à secretaria do teu estúdio.");
            msg.getStyle().set("color", "#666");
            Anchor login = new Anchor("/login", "Ir para o login");
            card.add(msg, login);
            add(card);
            return;
        }

        ContaPortal conta = contaOpt.get();

        H2 titulo = new H2("Definir palavra-passe");
        titulo.getStyle().set("margin", "0");
        Span sub = new Span(conta.getEmail());
        sub.getStyle().set("color", "#666").set("font-size", "14px");

        PasswordField nova = new PasswordField("Nova palavra-passe");
        nova.setWidthFull();
        PasswordField confirmar = new PasswordField("Confirmar palavra-passe");
        confirmar.setWidthFull();

        Button ativar = new Button("Ativar acesso", e -> {
            if (nova.getValue() == null || nova.getValue().length() < 6) {
                Notification.show("A palavra-passe tem de ter pelo menos 6 caracteres.")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (!nova.getValue().equals(confirmar.getValue())) {
                Notification.show("As palavras-passe não coincidem.")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            conta.setPasswordHash(passwordEncoder.encode(nova.getValue()));
            conta.setAtivo(true);
            conta.setTokenAtivacao(null);
            conta.setTokenExpiraEm(null);
            contaRepo.save(conta);

            removeAll();
            VerticalLayout ok = new VerticalLayout();
            ok.setWidth("400px");
            ok.setMaxWidth("95vw");
            ok.getStyle().set("background", "white").set("border-radius", "20px").set("padding", "32px")
                    .set("box-shadow", "0 25px 50px -12px rgba(0,0,0,0.4)");
            ok.add(new H2("Acesso ativado ✅"));
            Span m = new Span("Já podes entrar com o email " + conta.getEmail() + " e a palavra-passe que definiste.");
            m.getStyle().set("color", "#666");
            Button irLogin = new Button("Ir para o login",
                    ev -> getUI().ifPresent(ui -> ui.getPage().setLocation("/login")));
            irLogin.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            ok.add(m, irLogin);
            add(ok);
        });
        ativar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(titulo, sub, nova, confirmar, ativar);
        add(card);
    }
}
