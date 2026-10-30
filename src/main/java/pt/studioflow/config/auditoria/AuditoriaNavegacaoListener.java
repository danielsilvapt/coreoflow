package pt.studioflow.config.auditoria;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import pt.studioflow.service.AuditoriaService;

/**
 * Regista na auditoria cada página aberta por um utilizador autenticado. Ignora
 * navegações repetidas para a mesma rota na mesma UI (ex.: refresh de parâmetros).
 */
@Component
public class AuditoriaNavegacaoListener implements VaadinServiceInitListener {

    private static final String ULTIMA_ROTA = "auditoria.ultimaRota";

    private final AuditoriaService auditoriaService;

    public AuditoriaNavegacaoListener(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit -> {
            UI ui = uiInit.getUI();
            ui.addAfterNavigationListener(nav -> {
                try {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                        return;
                    }
                    String rota = nav.getLocation().getPath();
                    if (rota.equals(ComponentUtil.getData(ui, ULTIMA_ROTA))) {
                        return;
                    }
                    ComponentUtil.setData(ui, ULTIMA_ROTA, rota);

                    String pagina = null;
                    if (!nav.getActiveChain().isEmpty()) {
                        Class<?> view = nav.getActiveChain().get(0).getClass();
                        PageTitle titulo = view.getAnnotation(PageTitle.class);
                        pagina = titulo != null ? titulo.value().replace(" | CoreoFlow", "") : view.getSimpleName();
                    }
                    auditoriaService.registarNavegacao(rota, pagina);
                } catch (Throwable ignore) {
                    // auditoria nunca parte a navegação
                }
            });
        });
    }
}
