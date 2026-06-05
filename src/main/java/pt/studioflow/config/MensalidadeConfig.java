package pt.studioflow.config;

import org.springframework.stereotype.Component;
import pt.studioflow.model.Studio;

/**
 * Configuração de mensalidades por estúdio (multi-tenant).
 * Os valores são lidos do objeto Studio do tenant atual,
 * em vez de estarem hardcoded no application.properties.
 */
@Component
public class MensalidadeConfig {

    /**
     * Calcula o valor da mensalidade com base na configuração do studio.
     */
    public double calcularMensalidade(Studio studio, String tipo, int aulasPorSemana, boolean socio) {
        double valor;
        if (tipo.equalsIgnoreCase("crianca")) {
            valor = (aulasPorSemana == 1)
                    ? studio.getMensalidadeCrianca1x()
                    : studio.getMensalidadeCrianca2x();
        } else {
            valor = (aulasPorSemana == 1)
                    ? studio.getMensalidadeAdulto1x()
                    : studio.getMensalidadeAdulto2x();
        }
        if (!socio) {
            valor += studio.getMensalidadeNaoSocioAdicional();
        }
        return valor;
    }

    // Métodos de conveniência que lêem do Studio atual da sessão
    public double getValorCrianca1x(Studio studio) { return studio.getMensalidadeCrianca1x(); }
    public double getValorCrianca2x(Studio studio) { return studio.getMensalidadeCrianca2x(); }
    public double getValorAdulto1x(Studio studio)  { return studio.getMensalidadeAdulto1x(); }
    public double getValorAdulto2x(Studio studio)  { return studio.getMensalidadeAdulto2x(); }
}
