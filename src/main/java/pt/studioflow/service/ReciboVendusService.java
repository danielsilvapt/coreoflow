package pt.studioflow.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.CompraCredito;
import pt.studioflow.model.EstadoPagamentoCredito;
import pt.studioflow.model.PagamentoOnline;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.CompraCreditoRepository;
import pt.studioflow.repository.PagamentoOnlineRepository;

/**
 * Ligação Mollie → Vendus (configurável por estúdio, {@link Studio#isVendusReciboMollie()}).
 *
 * Assim que um pagamento online fica confirmado pelo Mollie (inscrição, renovação,
 * mensalidades no portal ou aulas avulso), emite-se no Vendus uma Fatura-Recibo já paga.
 * Antes, o cliente é procurado no Vendus (por NIF, ou pela referência do CoreoFlow) e criado
 * se não existir, com o email e o "enviar faturas" ligado — é o Vendus que envia o recibo.
 *
 * - Emissão imediata: depois do commit da confirmação ({@link PagamentoConfirmado}), em
 *   segundo plano, para nunca atrasar o webhook do Mollie nem o regresso do checkout.
 * - Rede de segurança: de 10 em 10 minutos repete os que falharam (Vendus em baixo, etc.),
 *   até {@link #MAX_TENTATIVAS}; o erro fica guardado no pagamento.
 * - Sem duplicados: cada documento leva a referência externa do pagamento e, antes de emitir,
 *   confirma-se no Vendus se já existe um documento com essa referência.
 * - Só pagamentos confirmados depois de a ligação ser ativada ({@link Studio#getVendusReciboDesde()}).
 */
@Service
public class ReciboVendusService {

    static final int MAX_TENTATIVAS = 6;

    /** Publicado quando um pagamento Mollie passa a PAGO. */
    public record PagamentoConfirmado(Origem origem, Long id) {}

    public enum Origem { PAGAMENTO_ONLINE, COMPRA_CREDITO }

    private final PagamentoOnlineRepository pagamentoOnlineRepository;
    private final CompraCreditoRepository compraCreditoRepository;
    private final VendusApiService vendusApiService;
    private final TransactionTemplate tx;

    public ReciboVendusService(PagamentoOnlineRepository pagamentoOnlineRepository,
            CompraCreditoRepository compraCreditoRepository, VendusApiService vendusApiService,
            PlatformTransactionManager transactionManager) {
        this.pagamentoOnlineRepository = pagamentoOnlineRepository;
        this.compraCreditoRepository = compraCreditoRepository;
        this.vendusApiService = vendusApiService;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoConfirmarPagamento(PagamentoConfirmado evento) {
        try {
            if (evento.origem() == Origem.PAGAMENTO_ONLINE) {
                emitirPagamentoOnline(evento.id());
            } else {
                emitirCompraCredito(evento.id());
            }
        } catch (Exception e) {
            System.err.println("Recibo Vendus (" + evento + "): " + e.getMessage());
        }
    }

    /** Repete os que ficaram por emitir (falha do Vendus, app reiniciada antes de emitir…). */
    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 3 * 60 * 1000)
    public void emitirPendentes() {
        LocalDateTime desde = LocalDateTime.now().minusDays(30);
        for (Long id : pagamentoOnlineRepository
                .findByEstadoAndVendusDocumentoIsNullAndDataPagamentoAfter(EstadoPagamentoCredito.PAGO, desde)
                .stream().filter(p -> p.getVendusTentativas() < MAX_TENTATIVAS)
                .map(PagamentoOnline::getId).toList()) {
            try {
                emitirPagamentoOnline(id);
            } catch (Exception e) {
                System.err.println("Recibo Vendus (pagamento " + id + "): " + e.getMessage());
            }
        }
        for (Long id : compraCreditoRepository
                .findByEstadoPagamentoAndMolliePaymentIdIsNotNullAndVendusDocumentoIsNullAndDataCompraAfter(
                        EstadoPagamentoCredito.PAGO, desde)
                .stream().filter(c -> c.getVendusTentativas() < MAX_TENTATIVAS)
                .map(CompraCredito::getId).toList()) {
            try {
                emitirCompraCredito(id);
            } catch (Exception e) {
                System.err.println("Recibo Vendus (compra " + id + "): " + e.getMessage());
            }
        }
    }

    /** Emite (se aplicável) o recibo de um pagamento de inscrição/renovação/mensalidades. */
    public synchronized void emitirPagamentoOnline(Long id) {
        tx.executeWithoutResult(s -> pagamentoOnlineRepository.findById(id).ifPresent(p -> {
            if (p.getEstado() != EstadoPagamentoCredito.PAGO || p.getVendusDocumento() != null
                    || !elegivel(p.getStudio(), p.getDataPagamento())) {
                return;
            }
            Resultado r = emitir(p.getStudio(), p.getAluno(), p.getDescricao(), p.getValor(), "CF-PO-" + p.getId());
            p.setVendusTentativas(p.getVendusTentativas() + 1);
            p.setVendusDocumento(r.numero());
            p.setVendusErro(r.erro());
            pagamentoOnlineRepository.save(p);
        }));
    }

    /** Emite (se aplicável) o recibo de uma compra de aulas avulso/pack paga no Mollie. */
    public synchronized void emitirCompraCredito(Long id) {
        tx.executeWithoutResult(s -> compraCreditoRepository.findById(id).ifPresent(c -> {
            if (c.getEstadoPagamento() != EstadoPagamentoCredito.PAGO || c.getMolliePaymentId() == null
                    || c.getVendusDocumento() != null || !elegivel(c.getStudio(), c.getDataCompra())) {
                return;
            }
            String titulo = "Aulas: " + (c.getNomePack() != null ? c.getNomePack() : "pack de aulas")
                    + " - " + c.getAluno().getNomeCompleto();
            Resultado r = emitir(c.getStudio(), c.getAluno(), titulo, c.getPrecoPago(), "CF-CC-" + c.getId());
            c.setVendusTentativas(c.getVendusTentativas() + 1);
            c.setVendusDocumento(r.numero());
            c.setVendusErro(r.erro());
            compraCreditoRepository.save(c);
        }));
    }

    private static boolean elegivel(Studio studio, LocalDateTime dataPagamento) {
        if (studio == null || !studio.isVendusReciboMollieAtivo()) return false;
        LocalDateTime desde = studio.getVendusReciboDesde();
        return desde == null || dataPagamento == null || !dataPagamento.isBefore(desde);
    }

    private record Resultado(String numero, String erro) {}

    private Resultado emitir(Studio studio, Aluno aluno, String titulo, BigDecimal valor, String referencia) {
        if (valor == null || valor.signum() <= 0) {
            return new Resultado(null, "Sem valor a faturar.");
        }
        String apiKey = studio.getVendusApiKey().trim();
        boolean testes = studio.isVendusModoTestes();
        try {
            var existente = vendusApiService.documentoPorReferencia(apiKey, referencia, testes);
            if (existente.isPresent()) {
                return new Resultado(existente.get().numero(), null);
            }
            String clienteId = vendusApiService.obterOuCriarCliente(apiKey, dadosFaturacao(aluno));
            VendusApiService.Documento doc = vendusApiService.emitirFaturaRecibo(apiKey, clienteId,
                    titulo != null ? titulo : "Pagamento - " + aluno.getNomeCompleto(), valor,
                    studio.getVendusMetodoPagamentoId(), studio.getVendusRegisterId(),
                    studio.getVendusTaxa(), studio.getVendusIsencao(), referencia, testes);
            return new Resultado(doc.numero(), null);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new Resultado(null, msg.length() > 1000 ? msg.substring(0, 1000) : msg);
        }
    }

    /**
     * Quem fica na fatura: num aluno menor com encarregado de educação, o encarregado (é quem
     * paga e quem deduz no IRS); senão o próprio aluno. Morada é sempre a do aluno.
     */
    static VendusApiService.DadosCliente dadosFaturacao(Aluno a) {
        // A data de nascimento manda; a flag "criança" (1 por omissão) só quando não há data.
        boolean menor = a.getDataNascimento() != null
                ? Period.between(a.getDataNascimento(), LocalDate.now()).getYears() < 18
                : a.isCrianca();
        boolean peloEncarregado = menor && naoVazio(a.getEncarregadoNome());
        if (peloEncarregado) {
            return new VendusApiService.DadosCliente(
                    naoVazio(a.getEncarregadoContribuinte()) ? a.getEncarregadoContribuinte() : a.getNumeroContribuinte(),
                    a.getEncarregadoNome().trim(),
                    naoVazio(a.getEncarregadoEmail()) ? a.getEncarregadoEmail().trim() : a.getEmail(),
                    a.getMorada(), a.getCodigoPostal(), a.getLocalidade(), "CF-EE" + a.getId());
        }
        return new VendusApiService.DadosCliente(a.getNumeroContribuinte(), a.getNomeCompleto(),
                a.getEmail() != null ? a.getEmail().trim() : null,
                a.getMorada(), a.getCodigoPostal(), a.getLocalidade(), "CF-A" + a.getId());
    }

    private static boolean naoVazio(String s) {
        return s != null && !s.isBlank();
    }
}
