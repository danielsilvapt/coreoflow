package pt.studioflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.EstadoPagamentoCredito;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.PagamentoOnline;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.PagamentoOnlineRepository;
import pt.studioflow.repository.StudioRepository;

/**
 * Pagamentos online (Mollie) de inscrições, renovações e mensalidades.
 *
 * - Inscrição/renovação: o pedido é submetido normalmente (fica PENDENTE para
 *   validação) e o candidato é logo encaminhado para o checkout do 1º pagamento
 *   estimado. Quando o pedido é aceite, o valor pago marca como pagas as
 *   mensalidades do 1º mês gerado (ver {@link #aplicarPagamentosDoPedido}).
 * - Mensalidades: o aluno paga no portal as mensalidades em aberto até ao mês
 *   corrente; ficam PAGO assim que o Mollie confirma.
 *
 * O estado é sempre confirmado junto da API do Mollie (webhook ou regresso do
 * checkout), nunca a partir do que o browser/webhook envia.
 */
@Service
public class PagamentoOnlineService {

    public static final String OBS_PAGO_ONLINE = "Pago online (Mollie)";

    private final PagamentoOnlineRepository pagamentoOnlineRepository;
    private final MensalidadeRepository mensalidadeRepository;
    private final StudioRepository studioRepository;
    private final MollieApiService mollieApiService;
    private final MensalidadeConfig mensalidadeConfig;

    public PagamentoOnlineService(PagamentoOnlineRepository pagamentoOnlineRepository,
            MensalidadeRepository mensalidadeRepository, StudioRepository studioRepository,
            MollieApiService mollieApiService, MensalidadeConfig mensalidadeConfig) {
        this.pagamentoOnlineRepository = pagamentoOnlineRepository;
        this.mensalidadeRepository = mensalidadeRepository;
        this.studioRepository = studioRepository;
        this.mollieApiService = mollieApiService;
        this.mensalidadeConfig = mensalidadeConfig;
    }

    /** Pagamento criado no Mollie: o id local (para o regresso) e o URL de checkout para redirecionar. */
    public record Checkout(Long pagamentoId, String checkoutUrl) {}

    /**
     * Cria o pagamento do 1º pagamento estimado de uma inscrição/renovação.
     * {@code redirectUrlBase} recebe o id do pagamento no fim (ex.: "...&pagamento=").
     */
    @Transactional
    public Checkout criarPagamentoPedido(Aluno aluno, Studio studio, PagamentoOnline.Tipo tipo, double valor,
            String redirectUrlBase, String webhookUrl) {
        String rotulo = tipo == PagamentoOnline.Tipo.RENOVACAO ? "Renovação" : "Inscrição";
        return criar(aluno, studio, tipo, valor, rotulo + " - " + aluno.getNomeCompleto(), null,
                redirectUrlBase, webhookUrl);
    }

    /** Cria o pagamento de um conjunto de mensalidades em aberto do aluno (portal). */
    @Transactional
    public Checkout criarPagamentoMensalidades(Aluno aluno, Studio studio, List<Mensalidade> mensalidades,
            String redirectUrlBase, String webhookUrl) {
        if (mensalidades.isEmpty()) {
            throw new IllegalArgumentException("Não há mensalidades selecionadas.");
        }
        double total = mensalidades.stream().mapToDouble(m -> mensalidadeConfig.valorComMulta(m, studio)).sum();
        String periodos = mensalidades.stream()
                .map(m -> YearMonth.of(m.getAno(), m.getMes())).distinct().sorted()
                .map(ym -> String.format("%02d/%d", ym.getMonthValue(), ym.getYear()))
                .reduce((a, b) -> a + ", " + b).orElse("");
        return criar(aluno, studio, PagamentoOnline.Tipo.MENSALIDADES, total,
                "Mensalidade " + periodos + " - " + aluno.getNomeCompleto(),
                mensalidades.stream().map(Mensalidade::getId).toList(), redirectUrlBase, webhookUrl);
    }

    private Checkout criar(Aluno aluno, Studio studio, PagamentoOnline.Tipo tipo, double valor, String descricao,
            List<Long> mensalidadeIds, String redirectUrlBase, String webhookUrl) {
        // O studio pode vir de uma vista (proxy LAZY já desligado da sessão) — recarrega-se pelo id.
        Studio studioGerido = studioRepository.findById(studio.getId())
                .orElseThrow(() -> new IllegalStateException("Estúdio não encontrado."));
        if (!studioGerido.isMollieAtivo()) {
            throw new IllegalStateException("Mollie não está configurado para este estúdio.");
        }
        BigDecimal montante = BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP);
        if (montante.signum() <= 0) {
            throw new IllegalArgumentException("Não há valor a pagar.");
        }

        PagamentoOnline pagamento = new PagamentoOnline();
        pagamento.setStudio(studioGerido);
        pagamento.setAluno(aluno);
        pagamento.setTipo(tipo);
        pagamento.setValor(montante);
        pagamento.setDescricao(descricao);
        if (mensalidadeIds != null) {
            pagamento.setListaMensalidadeIds(mensalidadeIds);
        }
        pagamento.setDataCriacao(LocalDateTime.now());
        pagamento = pagamentoOnlineRepository.save(pagamento);

        MollieApiService.PagamentoCriado criado = mollieApiService.criarPagamento(
                studioGerido, montante, descricao, redirectUrlBase + pagamento.getId(), webhookUrl);
        pagamento.setMolliePaymentId(criado.paymentId);
        pagamentoOnlineRepository.save(pagamento);
        return new Checkout(pagamento.getId(), criado.checkoutUrl);
    }

    /**
     * Chamado pelo webhook do Mollie. Devolve false se o id não for de um
     * pagamento deste tipo (pode ser de uma compra de aulas avulso).
     */
    @Transactional
    public boolean confirmarPagamentoMollie(String molliePaymentId) {
        Optional<PagamentoOnline> encontrado = pagamentoOnlineRepository.findByMolliePaymentId(molliePaymentId);
        encontrado.ifPresent(this::atualizarEstado);
        return encontrado.isPresent();
    }

    /**
     * Regresso do checkout: confirma já o estado (não espera pelo webhook) e
     * devolve-o. Só aceita pagamentos do estúdio indicado.
     */
    @Transactional
    public Optional<EstadoPagamentoCredito> sincronizar(Long pagamentoId, Studio studio) {
        return pagamentoOnlineRepository.findById(pagamentoId)
                .filter(p -> studio != null && p.getStudio().getId().equals(studio.getId()))
                .map(p -> {
                    if (p.getEstado() == EstadoPagamentoCredito.PENDENTE && p.getMolliePaymentId() != null) {
                        atualizarEstado(p);
                    }
                    return p.getEstado();
                });
    }

    /** Texto a mostrar ao regressar do checkout ({@code pagamentoId} vem do URL, por isso pode ser inválido). */
    @Transactional
    public String mensagemRegresso(String pagamentoId, Studio studio) {
        Optional<EstadoPagamentoCredito> estado;
        try {
            estado = sincronizar(Long.valueOf(pagamentoId), studio);
        } catch (NumberFormatException e) {
            estado = Optional.empty();
        } catch (Exception e) {
            return "Estamos a confirmar o teu pagamento.";
        }
        return switch (estado.orElse(EstadoPagamentoCredito.CANCELADO)) {
            case PAGO -> "Pagamento recebido. Obrigado!";
            case PENDENTE -> "Estamos a confirmar o teu pagamento.";
            default -> "O pagamento não foi concluído. O estúdio indicará como pagar.";
        };
    }

    private void atualizarEstado(PagamentoOnline pagamento) {
        EstadoPagamentoCredito anterior = pagamento.getEstado();
        EstadoPagamentoCredito estado = mollieApiService.verificarEstado(pagamento.getStudio(),
                pagamento.getMolliePaymentId());
        pagamento.setEstado(estado);
        if (estado == EstadoPagamentoCredito.PAGO && anterior != EstadoPagamentoCredito.PAGO) {
            pagamento.setDataPagamento(LocalDateTime.now());
            if (pagamento.getTipo() == PagamentoOnline.Tipo.MENSALIDADES) {
                marcarPagas(mensalidadeRepository.findAllById(pagamento.getListaMensalidadeIds()));
                pagamento.setAplicado(true);
            } else if (pagamento.getAluno().getStatus() == Aluno.AlunoStatus.ATIVO) {
                // O pedido já foi aceite antes de o pagamento ficar confirmado.
                aplicarAoPrimeiroMes(pagamento);
            }
        }
        pagamentoOnlineRepository.save(pagamento);
    }

    /**
     * Ao aceitar uma inscrição/renovação (depois de geradas as mensalidades):
     * os pagamentos online já confirmados do pedido marcam como pagas as
     * mensalidades do 1º mês do período.
     */
    @Transactional
    public void aplicarPagamentosDoPedido(Aluno aluno) {
        List<PagamentoOnline> doPedido = pagamentoOnlineRepository.findByAlunoAndTipoInOrderByDataCriacaoDesc(aluno,
                List.of(PagamentoOnline.Tipo.INSCRICAO, PagamentoOnline.Tipo.RENOVACAO));
        // Webhook pode não ter chegado (e o candidato pode não ter voltado do checkout):
        // confirma junto do Mollie os que ainda estão pendentes antes de aplicar.
        doPedido.forEach(this::reverificarSePendente);
        doPedido.stream()
                .filter(p -> p.getEstado() == EstadoPagamentoCredito.PAGO && !p.isAplicado())
                .forEach(p -> {
                    aplicarAoPrimeiroMes(p);
                    pagamentoOnlineRepository.save(p);
                });
    }

    /**
     * 1º mês do período letivo em que o pedido foi feito; se todas as mensalidades
     * desse mês já estiverem pagas (ex.: 1ª mensalidade oferecida), o pagamento
     * cobriu só a taxa e não se marca mais nada.
     */
    private void aplicarAoPrimeiroMes(PagamentoOnline pagamento) {
        YearMonth inicio = MensalidadeService.inicioPeriodoLetivo(pagamento.getDataCriacao().toLocalDate());
        List<Mensalidade> doPeriodo = mensalidadeRepository.findByAluno(pagamento.getAluno()).stream()
                .filter(m -> !YearMonth.of(m.getAno(), m.getMes()).isBefore(inicio))
                .toList();
        if (doPeriodo.isEmpty()) {
            return; // mensalidades ainda não geradas: aplica-se quando o pedido for aceite
        }
        YearMonth primeiro = doPeriodo.stream().map(m -> YearMonth.of(m.getAno(), m.getMes()))
                .min(Comparator.naturalOrder()).get();
        marcarPagas(doPeriodo.stream()
                .filter(m -> YearMonth.of(m.getAno(), m.getMes()).equals(primeiro))
                .toList());
        pagamento.setAplicado(true);
    }

    private void marcarPagas(List<Mensalidade> mensalidades) {
        List<Mensalidade> porPagar = mensalidades.stream()
                .filter(m -> m.getEstado() != EstadoMensalidade.PAGO).toList();
        porPagar.forEach(m -> {
            m.setEstado(EstadoMensalidade.PAGO);
            m.setDiaPagamento(LocalDate.now());
            m.setObservacoes(m.getObservacoes() == null || m.getObservacoes().isBlank()
                    ? OBS_PAGO_ONLINE : m.getObservacoes() + " · " + OBS_PAGO_ONLINE);
        });
        mensalidadeRepository.saveAll(porPagar);
    }

    /** Mensalidades que o aluno pode pagar no portal: em aberto, com valor, até ao mês corrente. */
    public List<Mensalidade> mensalidadesPagaveis(List<Mensalidade> mensalidades) {
        YearMonth atual = YearMonth.now();
        return mensalidades.stream()
                .filter(m -> m.getEstado() != EstadoMensalidade.PAGO && m.getValor() > 0)
                .filter(m -> !YearMonth.of(m.getAno(), m.getMes()).isAfter(atual))
                .sorted(Comparator.comparing((Mensalidade m) -> YearMonth.of(m.getAno(), m.getMes())))
                .toList();
    }

    /**
     * Pagamento mais recente do pedido (inscrição/renovação) em curso deste aluno,
     * para mostrar na validação: ainda não aplicado e criado nos últimos 90 dias
     * (exclui tentativas de pedidos de anos anteriores).
     */
    @Transactional
    public Optional<PagamentoOnline> ultimoPagamentoDoPedido(Aluno aluno) {
        LocalDateTime desde = LocalDateTime.now().minusDays(90);
        Optional<PagamentoOnline> ultimo = pagamentoOnlineRepository.findByAlunoAndTipoInOrderByDataCriacaoDesc(aluno,
                        List.of(PagamentoOnline.Tipo.INSCRICAO, PagamentoOnline.Tipo.RENOVACAO)).stream()
                .filter(p -> !p.isAplicado() && p.getDataCriacao().isAfter(desde))
                .findFirst();
        ultimo.ifPresent(this::reverificarSePendente);
        return ultimo;
    }

    /** Pagamentos ainda pendentes criados desde {@code desde} — re-verificados por {@link ReverificacaoPagamentosTask}. */
    public List<String> molliePaymentIdsPendentes(LocalDateTime desde) {
        return pagamentoOnlineRepository
                .findByEstadoAndMolliePaymentIdIsNotNullAndDataCriacaoAfter(EstadoPagamentoCredito.PENDENTE, desde)
                .stream().map(PagamentoOnline::getMolliePaymentId).toList();
    }

    /** Consulta o Mollie só se ainda estiver pendente; uma falha da API deixa o estado como está. */
    private void reverificarSePendente(PagamentoOnline pagamento) {
        if (pagamento.getEstado() != EstadoPagamentoCredito.PENDENTE || pagamento.getMolliePaymentId() == null) {
            return;
        }
        try {
            atualizarEstado(pagamento);
        } catch (Exception e) {
            System.err.println("Não foi possível confirmar o pagamento Mollie " + pagamento.getMolliePaymentId()
                    + ": " + e.getMessage());
        }
    }
}
