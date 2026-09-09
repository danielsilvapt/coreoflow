package pt.studioflow.util;

import java.time.Duration;
import java.time.temporal.Temporal;

/**
 * Regra de contagem de horas para pagamento a professores: cada sessão (aula,
 * ensaio, registo de horas) é paga em <b>horas completas, arredondando para
 * cima</b> — uma aula de 45 min conta como 1 h, uma de 1h15 conta como 2 h.
 */
public final class HorasUtil {

    private HorasUtil() {
    }

    /** Horas faturáveis de uma sessão entre {@code inicio} e {@code fim}. */
    public static double faturaveis(Temporal inicio, Temporal fim) {
        if (inicio == null || fim == null) {
            return 0.0;
        }
        long minutos = Duration.between(inicio, fim).toMinutes();
        return minutos <= 0 ? 0.0 : Math.ceil(minutos / 60.0);
    }
}
