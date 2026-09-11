package pt.studioflow.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.CompraCredito;
import pt.studioflow.model.EstadoPagamentoCredito;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.MetodoPagamentoCredito;
import pt.studioflow.model.OrigemCompraCredito;
import pt.studioflow.model.PackAula;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.CompraCreditoRepository;

@Service
public class CompraCreditoService {

    private final CompraCreditoRepository compraCreditoRepository;
    private final MollieApiService mollieApiService;
    private final EmailService emailService;

    public CompraCreditoService(CompraCreditoRepository compraCreditoRepository,
            MollieApiService mollieApiService, EmailService emailService) {
        this.compraCreditoRepository = compraCreditoRepository;
        this.mollieApiService = mollieApiService;
        this.emailService = emailService;
    }

    public static class ResultadoCompra {
        public final CompraCredito compra;
        /** Preenchido só quando o pagamento foi feito via Mollie — redirecionar o aluno para aqui. */
        public final String mollieCheckoutUrl;
        public ResultadoCompra(CompraCredito compra, String mollieCheckoutUrl) {
            this.compra = compra;
            this.mollieCheckoutUrl = mollieCheckoutUrl;
        }
    }

    /**
     * Cria a compra de um pack/aula avulsa. O acesso/crédito fica sempre ativo de
     * imediato — sem validação de admin — independentemente do método de
     * pagamento escolhido: com Mollie a pessoa é redirecionada para o checkout,
     * sem Mollie (ou se o estúdio não o tiver configurado) fica como "pagar no
     * estúdio", com o estado de pagamento pendente para o admin resolver depois.
     */
    @Transactional
    public ResultadoCompra criarCompra(Aluno aluno, PackAula pack, Modalidade modalidadeEscolhida,
            boolean usarMollie, OrigemCompraCredito origem, String redirectUrl, String webhookUrl) {

        Studio studio = pack.getStudio();

        CompraCredito compra = new CompraCredito();
        compra.setStudio(studio);
        compra.setAluno(aluno);
        compra.setPackAula(pack);
        compra.setNomePack(pack.getNome());
        compra.setNumAulasComprado(pack.getNumAulas());
        compra.setPrecoPago(pack.getPreco());
        compra.setModalidade(modalidadeEscolhida);
        compra.setCreditosRestantes(pack.getNumAulas());
        compra.setDataCompra(LocalDateTime.now());
        compra.setValidadeAte(pack.getValidadeDias() != null
                ? LocalDate.now().plusDays(pack.getValidadeDias()) : null);
        compra.setOrigem(origem);

        String mollieCheckoutUrl = null;
        if (usarMollie && studio.isMollieAtivo()) {
            compra.setMetodoPagamento(MetodoPagamentoCredito.MOLLIE);
            compra.setEstadoPagamento(EstadoPagamentoCredito.PENDENTE);
            MollieApiService.PagamentoCriado pagamento = mollieApiService.criarPagamento(
                    studio, pack.getPreco(), pack.getNome() + " - " + aluno.getNomeCompleto(),
                    redirectUrl, webhookUrl);
            compra.setMolliePaymentId(pagamento.paymentId);
            mollieCheckoutUrl = pagamento.checkoutUrl;
        } else {
            compra.setMetodoPagamento(MetodoPagamentoCredito.MANUAL);
            compra.setEstadoPagamento(EstadoPagamentoCredito.PENDENTE);
        }

        compra = compraCreditoRepository.save(compra);
        emailService.enviarEmailNotificacaoCompraAvulso(compra);

        return new ResultadoCompra(compra, mollieCheckoutUrl);
    }

    /** Chamado pelo webhook do Mollie: confirma o estado real do pagamento junto da API (nunca confia no corpo do webhook). */
    @Transactional
    public void confirmarPagamentoMollie(String molliePaymentId) {
        compraCreditoRepository.findByMolliePaymentId(molliePaymentId).ifPresent(compra -> {
            EstadoPagamentoCredito estado = mollieApiService.verificarEstado(compra.getStudio(), molliePaymentId);
            compra.setEstadoPagamento(estado);
            compraCreditoRepository.save(compra);
        });
    }

    public List<CompraCredito> listarDoAluno(Aluno aluno) {
        return compraCreditoRepository.findByAlunoOrderByDataCompraDesc(aluno);
    }

    public List<CompraCredito> listarCreditosValidos(Aluno aluno) {
        return compraCreditoRepository.findCreditosValidosDoAluno(aluno);
    }

    public List<CompraCredito> listarRecentesDoStudio(Studio studio, int dias) {
        return compraCreditoRepository.findByStudioAndDataCompraAfterOrderByDataCompraDesc(
                studio, LocalDateTime.now().minusDays(dias));
    }

    @Transactional
    public CompraCredito save(CompraCredito compra) {
        return compraCreditoRepository.save(compra);
    }
}
