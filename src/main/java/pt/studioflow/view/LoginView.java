package pt.studioflow.view;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
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
                // Configuração base do ecrã
                setSizeFull();
                setPadding(false);
                setSpacing(false);
                setAlignItems(Alignment.CENTER);
                setJustifyContentMode(JustifyContentMode.CENTER);

                // CSS XPTO: Fundo Animado + Glassmorphism + Efeitos de Carregamento Premium
                String styles = "body { " +
                                "  background: linear-gradient(-45deg, #0f1f4b, #1a3a6b, #0e4d6e, #0a2a4a); " +
                                "  background-size: 400% 400%; " +
                                "  animation: gradient 15s ease infinite; " +
                                "} " +
                                "@keyframes gradient { " +
                                "  0% { background-position: 0% 50%; } " +
                                "  50% { background-position: 100% 50%; } " +
                                "  100% { background-position: 0% 50%; } " +
                                "} " +
                                ".v-loading-indicator { " +
                                "  top: 0 !important; left: 0 !important; height: 4px !important; " +
                                "  background: #00D4AA !important; box-shadow: 0 0 10px #00D4AA; z-index: 99999; " +
                                "} " +
                                ".login-card { " +
                                "  background: rgba(255, 255, 255, 0.10); " +
                                "  backdrop-filter: blur(25px); " +
                                "  border-radius: 28px; " +
                                "  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5); " +
                                "  padding: 45px; " +
                                "  display: flex; flex-direction: column; align-items: center; " +
                                "  border: 1px solid rgba(255, 255, 255, 0.15); " +
                                "  animation: reveal 1.2s cubic-bezier(0.16, 1, 0.3, 1); " +
                                "  transition: transform 0.3s ease, opacity 0.3s ease; " +
                                "} " +
                                "@keyframes reveal { " +
                                "  0% { opacity: 0; transform: translateY(40px) scale(0.95); } " +
                                "  100% { opacity: 1; transform: translateY(0) scale(1); } " +
                                "} " +
                                ".login-card:active { transform: scale(0.99); }" +
                                "vaadin-login-form-wrapper { background: transparent !important; padding: 0 !important; } "
                                +
                                "vaadin-login-form-wrapper > form { background: transparent !important; } " +
                                "vaadin-text-field, vaadin-password-field { " +
                                "  --lumo-secondary-text-color: rgba(255, 255, 255, 0.9); " +
                                "  --lumo-body-text-color: white; " +
                                "  --lumo-contrast-10pct: rgba(255, 255, 255, 0.15); " +
                                "} " +
                                "vaadin-text-field [part='label'], vaadin-password-field [part='label'] { color: white !important; } "
                                +
                                "vaadin-login-form-wrapper vaadin-button[theme~='primary'] { " +
                                "  margin-top: 25px; " +
                                "  background: linear-gradient(135deg, #3B6CB7, #00D4AA); " +
                                "  font-weight: 700; letter-spacing: 1px; height: 45px; border: none; " +
                                "} ";

                UI.getCurrent().getElement().executeJs(
                                "const style = document.createElement('style');" +
                                                "style.textContent = $0;" +
                                                "document.head.appendChild(style);",
                                styles);

                Div loginCard = new Div();
                loginCard.addClassName("login-card");
                loginCard.setWidth("420px");
                loginCard.setMaxWidth("95vw");

                Image logo = new Image("/images/logo-coreoflow.png", "CoreoFlow");
                logo.setWidth("300px");
                logo.getStyle().set("margin-bottom", "5px");

                H1 title = new H1("CoreoFlow");
                title.getStyle()
                                .set("font-size", "28px")
                                .set("margin", "0")
                                .set("color", "white")
                                .set("font-weight", "900")
                                .set("display", "none");

                Span subtitle = new Span("Gestão de Estúdios de Dança");
                subtitle.getStyle()
                                .set("color", "rgba(255,255,255,0.7)")
                                .set("font-size", "11px")
                                .set("margin-bottom", "15px")
                                .set("text-transform", "uppercase")
                                .set("letter-spacing", "5px");

                login.setAction("login");
                login.setForgotPasswordButtonVisible(false);

                LoginI18n i18n = LoginI18n.createDefault();
                i18n.getForm().setTitle("");
                i18n.getForm().setUsername("Utilizador");
                i18n.getForm().setPassword("Palavra-passe");
                i18n.getForm().setSubmit("Aceder ao Portal");
                login.setI18n(i18n);

                // Evento XPTO: Esconde o cartão com fade-out ao submeter o formulário
                login.addLoginListener(e -> {
                        loginCard.getStyle().set("opacity", "0");
                        loginCard.getStyle().set("transform", "scale(0.9) translateY(-20px)");
                });

                loginCard.add(logo, title, subtitle, login);

                Span footer = new Span("© " + java.time.Year.now().getValue() + " CoreoFlow by Daniel Silva");
                footer.getStyle()
                                .set("color", "white")
                                .set("font-size", "10px")
                                .set("margin-top", "30px")
                                .set("opacity", "0.4");

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
                        event.forwardTo(isProf ? PresencasView.class : DashboardView.class);
                }
        }
}