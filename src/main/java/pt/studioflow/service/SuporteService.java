package pt.studioflow.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.ConfiguracaoPlataforma;
import pt.studioflow.model.PedidoSuporte;
import pt.studioflow.repository.ConfiguracaoPlataformaRepository;
import pt.studioflow.repository.PedidoSuporteRepository;

/**
 * Trata os pedidos de suporte e a configuração global da plataforma.
 * O registo de um pedido de suporte NUNCA falha para o utilizador: grava-se
 * sempre em BD e só depois se tenta o email.
 */
@Service
public class SuporteService {

    private final PedidoSuporteRepository pedidoRepo;
    private final ConfiguracaoPlataformaRepository configRepo;
    private final EmailService emailService;

    public SuporteService(PedidoSuporteRepository pedidoRepo,
                          ConfiguracaoPlataformaRepository configRepo,
                          EmailService emailService) {
        this.pedidoRepo = pedidoRepo;
        this.configRepo = configRepo;
        this.emailService = emailService;
    }

    @Transactional
    public ConfiguracaoPlataforma getConfig() {
        return configRepo.findById(1L).orElseGet(() -> {
            ConfiguracaoPlataforma c = new ConfiguracaoPlataforma();
            c.setId(1L);
            return configRepo.save(c);
        });
    }

    @Transactional
    public ConfiguracaoPlataforma guardarConfig(ConfiguracaoPlataforma c) {
        c.setId(1L);
        return configRepo.save(c);
    }

    public String emailSuporte() {
        String e = getConfig().getEmailSuporte();
        return (e != null && !e.isBlank()) ? e.trim() : "dfc.daniel@gmail.com";
    }

    /** Nº de pedidos por resolver (para o badge do menu). */
    public long porResolver() {
        try {
            return pedidoRepo.countByResolvidoFalse();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Regista um pedido de suporte. Grava sempre em BD; tenta enviar o email e
     * marca {@code emailEnviado} conforme o resultado. Não lança exceção.
     */
    @Transactional
    public PedidoSuporte registarPedido(String studio, String utilizador, String emailUtilizador, String tipo,
                                        String assunto, String descricao) {
        PedidoSuporte p = new PedidoSuporte();
        p.setStudio(studio);
        p.setUtilizador(utilizador);
        p.setEmailUtilizador(emailUtilizador);
        p.setTipo(tipo);
        p.setAssunto(assunto);
        p.setDescricao(descricao);
        p = pedidoRepo.save(p);

        try {
            ConfiguracaoPlataforma cfg = getConfig();
            emailService.enviarEmailSuporte(cfg.getEmailSuporte(), cfg.getEmailBccSuporte(), studio, utilizador,
                    emailUtilizador, tipo, assunto, descricao);
            p.setEmailEnviado(true);
            pedidoRepo.save(p);
        } catch (Exception ex) {
            System.err.println("SuporteService: falha ao enviar email de suporte (pedido " + p.getId()
                    + " guardado na mesma) - " + ex.getMessage());
        }
        return p;
    }

    /**
     * Responde ao cliente por email e guarda a resposta. Opcionalmente marca o
     * pedido como resolvido.
     *
     * @return true se o email foi entregue ao servidor de correio
     */
    @Transactional
    public boolean responder(Long pedidoId, String texto, boolean marcarResolvido) {
        PedidoSuporte p = pedidoRepo.findById(pedidoId).orElse(null);
        if (p == null) {
            return false;
        }
        p.setResposta(texto);
        p.setDataResposta(LocalDateTime.now());
        if (marcarResolvido) {
            p.setResolvido(true);
        }
        pedidoRepo.save(p);

        if (p.getEmailUtilizador() == null || p.getEmailUtilizador().isBlank()) {
            return false;
        }
        try {
            ConfiguracaoPlataforma cfg = getConfig();
            emailService.enviarRespostaSuporte(p.getEmailUtilizador(), cfg.getEmailBccSuporte(),
                    cfg.getNomeRemetenteEmails(), p.getAssunto(), texto, p.getDescricao());
            return true;
        } catch (Exception ex) {
            System.err.println("SuporteService.responder: falha ao enviar (pedido " + pedidoId + ") - "
                    + ex.getMessage());
            return false;
        }
    }
}
