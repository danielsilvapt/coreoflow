package pt.studioflow.config;

import org.springframework.stereotype.Component;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

import java.time.LocalDate;
import java.time.Month;
import java.time.Year;

/**
 * Configuração de mensalidades por estúdio (multi-tenant).
 * Os valores são lidos do objeto Studio do tenant atual,
 * em vez de estarem hardcoded no application.properties.
 */
@Component
public class MensalidadeConfig {

    /**
     * Dia do mês em que a mensalidade vence, conforme configurado no estúdio.
     * Valor por omissão (ou fora de 1–28): dia 8.
     */
    public int diaLimitePagamento(Studio studio) {
        Integer d = studio != null ? studio.getDiaLimitePagamento() : null;
        return (d != null && d >= 1 && d <= 28) ? d : 8;
    }

    /**
     * Data-limite de pagamento da mensalidade de {@code mes}/{@code ano} para este estúdio.
     * O dia é limitado ao número de dias do mês (defensivo — a configuração já é 1–28).
     */
    public LocalDate dataLimite(int ano, Month mes, Studio studio) {
        int dia = Math.min(diaLimitePagamento(studio), mes.length(Year.of(ano).isLeap()));
        return LocalDate.of(ano, mes, dia);
    }

    /**
     * Estado "real" da mensalidade hoje: uma mensalidade {@code FATURADO} cuja
     * data-limite já passou conta como {@code EM_DIVIDA}. Os restantes estados
     * são devolvidos como estão.
     */
    public EstadoMensalidade estadoEfetivo(Mensalidade m, Studio studio) {
        if (m.getEstado() == EstadoMensalidade.FATURADO
                && LocalDate.now().isAfter(dataLimite(m.getAno(), m.getMes(), studio))) {
            return EstadoMensalidade.EM_DIVIDA;
        }
        return m.getEstado();
    }

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

    /**
     * Valor da mensalidade de uma turma concreta. Se a turma tiver mensalidade
     * própria configurada ({@link Turma#getMensalidadeSocio()} /
     * {@link Turma#getMensalidadeNaoSocio()}), esse valor substitui a tabela do
     * estúdio — é o caso das turmas de competição, workshops e níveis avançados.
     * Caso contrário aplica-se a tabela normal (criança/adulto x frequência).
     */
    public double calcularMensalidade(Studio studio, Turma turma, String tipo, int aulasPorSemana, boolean socio) {
        Double proprio = valorProprioDaTurma(turma, socio);
        if (proprio != null) {
            return proprio;
        }
        return calcularMensalidade(studio, tipo, aulasPorSemana, socio);
    }

    /**
     * Mensalidade própria da turma para este aluno, ou null se a turma segue a
     * tabela do estúdio. Um não-sócio sem valor próprio definido paga o valor de
     * sócio mais o acréscimo de não-sócio do estúdio.
     */
    public Double valorProprioDaTurma(Turma turma, boolean socio) {
        if (turma == null || !turma.temMensalidadePropria()) {
            return null;
        }
        if (socio) {
            return turma.getMensalidadeSocio() != null
                    ? turma.getMensalidadeSocio()
                    : turma.getMensalidadeNaoSocio();
        }
        if (turma.getMensalidadeNaoSocio() != null) {
            return turma.getMensalidadeNaoSocio();
        }
        double adicional = turma.getStudio() != null && turma.getStudio().getMensalidadeNaoSocioAdicional() != null
                ? turma.getStudio().getMensalidadeNaoSocioAdicional() : 0.0;
        return turma.getMensalidadeSocio() + adicional;
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
