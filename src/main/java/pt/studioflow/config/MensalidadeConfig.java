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

    public double calcularTaxaInscricao(Studio studio) {
        return studio.getTaxaInscricao() != null ? studio.getTaxaInscricao() : 0.0;
    }

    public double calcularTaxaRenovacao(Studio studio) {
        return studio.getTaxaRenovacao() != null ? studio.getTaxaRenovacao() : 0.0;
    }

    /**
     * Resumo de preço estimado para o formulário público (inscrição ou renovação).
     * Recebe a soma das mensalidades base de todas as turmas selecionadas (cada
     * turma pode ter uma frequência diferente) e devolve o breakdown com taxa e
     * descontos aplicáveis. É uma estimativa para o candidato — a validação final
     * (ValidacaoInscricoesView) é sempre quem gera as mensalidades reais.
     */
    public ResumoInscricao calcularResumo(Studio studio, double mensalidadeBaseTotal, boolean renovacao,
                                           boolean temFamiliarInscrito, int numModalidadesInteresse) {
        double taxa = renovacao ? calcularTaxaRenovacao(studio) : calcularTaxaInscricao(studio);
        double descontoFamiliar = temFamiliarInscrito && studio.getDescontoFamiliaresEuros() != null
                ? studio.getDescontoFamiliaresEuros() : 0.0;
        double descontoMultiModalidade = numModalidadesInteresse > 1 && studio.getDescontoMaisModalidadesPercentagem() != null
                ? mensalidadeBaseTotal * (studio.getDescontoMaisModalidadesPercentagem() / 100.0) : 0.0;
        double total = Math.max(0.0, mensalidadeBaseTotal + taxa - descontoFamiliar - descontoMultiModalidade);
        return new ResumoInscricao(mensalidadeBaseTotal, taxa, descontoFamiliar, descontoMultiModalidade, total);
    }

    /** Breakdown do valor estimado mostrado ao candidato no formulário público. */
    public record ResumoInscricao(double mensalidadeBase, double taxa, double descontoFamiliar,
                                   double descontoMultiModalidade, double total) {}
}
