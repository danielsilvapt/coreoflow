package pt.studioflow.service;

import org.springframework.stereotype.Component;

import pt.studioflow.model.Studio;

/**
 * Ponto único de decisão sobre se a plataforma pode enviar emails para um
 * estúdio. O SUPERADMIN liga/desliga o envio por estúdio na StudioAdminView
 * ({@link Studio#isEnviarEmails()}).
 *
 * <p><b>Fail-open:</b> quando não é possível determinar o estúdio (nulo) ou a
 * sua leitura falha (ex.: proxy lazy fora de sessão), o envio é <i>permitido</i>.
 * É preferível enviar um email a mais do que silenciar toda a comunicação por
 * um erro de contexto.
 */
@Component
public class EmailGate {

    /** {@code true} se o estúdio permite envio de emails. */
    public boolean permite(Studio studio) {
        try {
            return studio == null || studio.isEnviarEmails();
        } catch (RuntimeException e) {
            System.err.println("EmailGate: não foi possível ler o estado de envio do estúdio ("
                    + e.getMessage() + ") — a permitir o envio.");
            return true;
        }
    }

    /** {@code true} se o envio deve ser suprimido para este estúdio. */
    public boolean bloqueado(Studio studio) {
        return !permite(studio);
    }
}
