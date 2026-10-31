package pt.studioflow.view;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

@Route("login")
@PermitAll
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

        private final LoginForm login = new LoginForm();

        public LoginView() {
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
                login.setForgotPasswordButtonVisible(false);

                LoginI18n i18n = LoginI18n.createDefault();
                i18n.getForm().setTitle("");
                i18n.getForm().setUsername("Utilizador");
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
}