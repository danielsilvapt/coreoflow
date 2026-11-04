package pt.studioflow.view;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;
import pt.studioflow.service.ContaPortalService;

@Route("login")
@PermitAll
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

        private final LoginForm login = new LoginForm();

        private final ContaPortalService contaPortalService;

        public LoginView(ContaPortalService contaPortalService) {
                this.contaPortalService = contaPortalService;
                // Configuração base do ecrã. O visual (fundo da marca, cartão, campos e botão)
                // está no tema: classes .cf-login / .cf-login-card em themes/coreoflow/styles.css.
                setSizeFull();
                setPadding(false);
                setSpacing(false);
                setAlignItems(Alignment.CENTER);
                setJustifyContentMode(JustifyContentMode.CENTER);
                addClassName("cf-login");

                Div loginCard = new Div();
                loginCard.addClassName("login-card");
                loginCard.addClassName("cf-login-card");

                Image logo = new Image("/images/logo2-coreoflow.png", "CoreoFlow");
                logo.addClassName("cf-login-logo");

                Span subtitle = new Span("Gestão de Estúdios de Dança");
                subtitle.addClassName("cf-login-subtitle");

                login.setAction("login");
                // Alunos/encarregados: primeiro acesso ou reposição da palavra-passe por email.
                login.setForgotPasswordButtonVisible(true);
                login.addForgotPasswordListener(e -> abrirPedidoAcesso());

                LoginI18n i18n = LoginI18n.createDefault();
                i18n.getForm().setTitle("");
                i18n.getForm().setUsername("Email ou utilizador");
                i18n.getForm().setForgotPassword("Esqueci-me da palavra-passe");
                i18n.getForm().setPassword("Palavra-passe");
                i18n.getForm().setSubmit("Aceder ao Portal");
                login.setI18n(i18n);

                // Fade-out suave do cartão ao submeter o formulário
                login.addLoginListener(e -> loginCard.addClassName("cf-login-card--leaving"));

                loginCard.add(logo, subtitle, login);

                Span footer = new Span("© " + java.time.Year.now().getValue() + " CoreoFlow by Daniel Silva");
                footer.addClassName("cf-login-footer");

                add(loginCard, footer);
        }

        @Override
        public void beforeEnter(BeforeEnterEvent event) {
                if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
                        login.setError(true);
                }

                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                        boolean isProf = auth.getAuthorities().stream()
                                        .anyMatch(a -> a.getAuthority().equals("ROLE_PROF")
                                                        || a.getAuthority().equals("PROF"));
                        boolean isAluno = auth.getAuthorities().stream()
                                        .anyMatch(a -> a.getAuthority().equals("ROLE_ALUNO"));
                        if (isAluno) {
                                event.forwardTo(PortalAlunoView.class);
                        } else {
                                event.forwardTo(isProf ? PresencasView.class : DashboardView.class);
                        }
                }
        }

        /** Diálogo "Esqueci-me da palavra-passe": envia o link de definição de palavra-passe do portal. */
        private void abrirPedidoAcesso() {
                Dialog dialog = new Dialog();
                dialog.setHeaderTitle("Esqueci-me da palavra-passe");

                Span info = new Span("Indica o email registado na ficha de inscrição. Vais receber um link "
                                + "para definires a palavra-passe de acesso ao portal.");
                info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "14px");

                EmailField email = new EmailField("Email");
                email.setWidthFull();
                email.setErrorMessage("Email inválido");

                VerticalLayout corpo = new VerticalLayout(info, email);
                corpo.setPadding(false);
                corpo.setMaxWidth("360px");
                dialog.add(corpo);

                Button enviar = new Button("Enviar link", ev -> {
                        String valor = email.getValue() == null ? "" : email.getValue().trim();
                        if (valor.isEmpty() || email.isInvalid() || !valor.contains("@")) {
                                email.setInvalid(true);
                                return;
                        }
                        try {
                                contaPortalService.pedirAcesso(valor);
                        } catch (Exception ex) {
                                System.err.println("Erro no pedido de acesso ao portal: " + ex.getMessage());
                        }
                        dialog.close();
                        Notification n = Notification.show("Se este email estiver registado, vais receber em breve "
                                        + "um link para definires a palavra-passe. Verifica também a pasta de spam.",
                                        8000, Notification.Position.MIDDLE);
                        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
                enviar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                Button cancelar = new Button("Cancelar", ev -> dialog.close());
                dialog.getFooter().add(cancelar, enviar);
                dialog.open();
                email.focus();
        }
}
