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
     * Dia do mês em que a mensalidade vence, conforme configurado no estúdio, ou
     * {@code null} se o estúdio não tem vencimento automático definido — nesse
     * caso a mensalidade nunca passa a {@code EM_DIVIDA} por si só (ver
     * {@link #estadoEfetivo}). Um valor fora de 1–28 (dado legado/inválido) é
     * tratado como "não definido", pelo mesmo motivo.
     */
    public Integer diaLimitePagamento(Studio studio) {
        Integer d = studio != null ? studio.getDiaLimitePagamento() : null;
        return (d != null && d >= 1 && d <= 28) ? d : null;
    }

    /**
     * Data-limite de pagamento da mensalidade de {@code mes}/{@code ano} para este
     * estúdio, ou {@code null} se o estúdio não tem vencimento automático definido.
     * O dia é limitado ao número de dias do mês (defensivo — a configuração já é 1–28).
     */
    public LocalDate dataLimite(int ano, Month mes, Studio studio) {
        Integer diaConfig = diaLimitePagamento(studio);
        if (diaConfig == null) return null;
        int dia = Math.min(diaConfig, mes.length(Year.of(ano).isLeap()));
        return LocalDate.of(ano, mes, dia);
    }

    /**
     * Estado "real" da mensalidade hoje: uma mensalidade {@code FATURADO} cuja
     * data-limite já passou conta como {@code EM_DIVIDA}. Se o estúdio não tem
     * vencimento automático definido ({@link #dataLimite} devolve {@code null}),
     * a mensalidade fica sempre no estado guardado — a transição para dívida
     * passa a ser sempre manual. Os restantes estados são devolvidos como estão.
     */
    public EstadoMensalidade estadoEfetivo(Mensalidade m, Studio studio) {
        if (m.getEstado() == EstadoMensalidade.FATURADO) {
            LocalDate limite = dataLimite(m.getAno(), m.getMes(), studio);
            if (limite != null && LocalDate.now().isAfter(limite)) {
                return EstadoMensalidade.EM_DIVIDA;
            }
        }
        return m.getEstado();
    }

    /**
     * Valor efetivo a cobrar por esta mensalidade: o valor base, com a multa por
     * atraso do estúdio somada em cima enquanto estiver {@code EM_DIVIDA}. Sem
     * multa configurada (0% por omissão) devolve sempre o valor base.
     */
    public double valorComMulta(Mensalidade m, Studio studio) {
        double base = m.getValor();
        if (estadoEfetivo(m, studio) != EstadoMensalidade.EM_DIVIDA) {
            return base;
        }
        double percentagem = studio != null && studio.getMultaAtrasoPercentagem() != null
                ? studio.getMultaAtrasoPercentagem() : 0.0;
        return base + base * (percentagem / 100.0);
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

    private static final java.util.Set<Month> MESES_MEIO_MENSALIDADE =
            java.util.EnumSet.of(Month.SEPTEMBER, Month.DECEMBER, Month.JULY);

    /**
     * Meses em que o modelo {@code HORAS_SEMANA} cobra meia mensalidade
     * (Setembro, Dezembro, Julho) em vez do valor completo da tabela.
     */
    public boolean isMesMeioMensalidade(Month mes) {
        return MESES_MEIO_MENSALIDADE.contains(mes);
    }

    /**
     * Valor da mensalidade no modelo {@code HORAS_SEMANA}, para um total de
     * {@code horasPorSemana} de aula somadas entre todas as turmas do aluno
     * (acima de 6h/semana usa sempre o escalão "6 ou mais", ex.: passe). O valor
     * de meio mês é sempre metade do valor completo do escalão.
     */
    public double valorTabelaHoras(Studio studio, int horasPorSemana, boolean meioMes) {
        Double cheio = switch (Math.max(1, horasPorSemana)) {
            case 1 -> studio.getTabelaHoras1();
            case 2 -> studio.getTabelaHoras2();
            case 3 -> studio.getTabelaHoras3();
            case 4 -> studio.getTabelaHoras4();
            case 5 -> studio.getTabelaHoras5();
            default -> studio.getTabelaHoras6Mais();
        };
        double valor = cheio != null ? cheio : 0.0;
        return meioMes ? Math.round(valor * 50.0) / 100.0 : valor;
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
        // Modelo HORAS_SEMANA: tabela fechada, sem descontos (nem família, nem +modalidades).
        boolean semDescontos = studio.isModeloHorasSemana();
        double descontoFamiliar = !semDescontos && temFamiliarInscrito && studio.getDescontoFamiliaresEuros() != null
                ? studio.getDescontoFamiliaresEuros() : 0.0;
        double descontoMultiModalidade = !semDescontos && numModalidadesInteresse > 1
                && studio.getDescontoMaisModalidadesPercentagem() != null
                ? mensalidadeBaseTotal * (studio.getDescontoMaisModalidadesPercentagem() / 100.0) : 0.0;
        double total = Math.max(0.0, mensalidadeBaseTotal + taxa - descontoFamiliar - descontoMultiModalidade);
        return new ResumoInscricao(mensalidadeBaseTotal, taxa, descontoFamiliar, descontoMultiModalidade, total);
    }

    /** Breakdown do valor estimado mostrado ao candidato no formulário público. */
    public record ResumoInscricao(double mensalidadeBase, double taxa, double descontoFamiliar,
                                   double descontoMultiModalidade, double total) {}
}
