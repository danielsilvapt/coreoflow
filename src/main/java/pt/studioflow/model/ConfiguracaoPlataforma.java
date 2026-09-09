package pt.studioflow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Configuração global da plataforma (uma só linha, id = 1). Editável pelo
 * superadmin em runtime — não depende de application.properties.
 */
@Entity
public class ConfiguracaoPlataforma {

    @Id
    private Long id = 1L;

    /** Email para onde vão os pedidos de suporte submetidos pelos utilizadores. */
    @Column(length = 200)
    private String emailSuporte = "dfc.daniel@gmail.com";

    /** Aviso mostrado no topo a todos os utilizadores (ex.: manutenção agendada). */
    @Column(length = 500)
    private String avisoGlobal;

    private boolean avisoGlobalAtivo = false;

    /** Como mostrar o aviso global: BANNER (faixa no topo), POPUP (ao entrar) ou AMBOS. */
    @Column(length = 20)
    private String avisoGlobalModo = "BANNER";

    /** Permitir a criação de novos estúdios (registo/onboarding). */
    private boolean permitirNovosEstudios = true;

    /** Nome a mostrar como remetente nos emails da plataforma (ex.: respostas de suporte). */
    @Column(length = 120)
    private String nomeRemetenteEmails = "CoreoFlow";

    /** Email opcional em cópia (BCC) de todos os pedidos e respostas de suporte. */
    @Column(length = 200)
    private String emailBccSuporte;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmailSuporte() {
        return emailSuporte;
    }

    public void setEmailSuporte(String emailSuporte) {
        this.emailSuporte = emailSuporte;
    }

    public String getAvisoGlobal() {
        return avisoGlobal;
    }

    public void setAvisoGlobal(String avisoGlobal) {
        this.avisoGlobal = avisoGlobal;
    }

    public boolean isAvisoGlobalAtivo() {
        return avisoGlobalAtivo;
    }

    public void setAvisoGlobalAtivo(boolean avisoGlobalAtivo) {
        this.avisoGlobalAtivo = avisoGlobalAtivo;
    }

    public String getAvisoGlobalModo() {
        return avisoGlobalModo != null ? avisoGlobalModo : "BANNER";
    }

    public void setAvisoGlobalModo(String avisoGlobalModo) {
        this.avisoGlobalModo = avisoGlobalModo;
    }

    public boolean mostrarAvisoBanner() {
        return avisoGlobalAtivo && !"POPUP".equals(getAvisoGlobalModo());
    }

    public boolean mostrarAvisoPopup() {
        return avisoGlobalAtivo && !"BANNER".equals(getAvisoGlobalModo());
    }

    public boolean isPermitirNovosEstudios() {
        return permitirNovosEstudios;
    }

    public void setPermitirNovosEstudios(boolean permitirNovosEstudios) {
        this.permitirNovosEstudios = permitirNovosEstudios;
    }

    public String getNomeRemetenteEmails() {
        return nomeRemetenteEmails != null && !nomeRemetenteEmails.isBlank() ? nomeRemetenteEmails : "CoreoFlow";
    }

    public void setNomeRemetenteEmails(String nomeRemetenteEmails) {
        this.nomeRemetenteEmails = nomeRemetenteEmails;
    }

    public String getEmailBccSuporte() {
        return emailBccSuporte;
    }

    public void setEmailBccSuporte(String emailBccSuporte) {
        this.emailBccSuporte = emailBccSuporte;
    }
}
