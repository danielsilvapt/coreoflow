package pt.studioflow.config;

import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;
import pt.studioflow.model.Studio;

/**
 * Guarda o Studio (tenant) ativo para a sessão atual.
 * Armazenado na VaadinSession para ser seguro por utilizador.
 */
@Component
public class TenantContext {

    private static final String SESSION_KEY = "currentStudio";

    /**
     * Obtém o studio da sessão Vaadin atual.
     * Retorna null se for SUPERADMIN ou sessão ainda não inicializada.
     */
    public static Studio getCurrentStudio() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) return null;
        return (Studio) session.getAttribute(SESSION_KEY);
    }

    /**
     * Define o studio na sessão Vaadin atual.
     * Chamado no momento do login.
     */
    public static void setCurrentStudio(Studio studio) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(SESSION_KEY, studio);
        }
    }

    /**
     * Limpa o studio da sessão (logout).
     */
    public static void clear() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(SESSION_KEY, null);
        }
    }

    /**
     * Verifica se existe um studio ativo na sessão.
     */
    public static boolean hasStudio() {
        return getCurrentStudio() != null;
    }
}
