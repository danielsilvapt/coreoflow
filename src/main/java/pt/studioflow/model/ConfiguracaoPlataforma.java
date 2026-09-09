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

    /** Permitir a criação de novos estúdios (registo/onboarding). */
    private boolean permitirNovosEstudios = true;

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

    public boolean isPermitirNovosEstudios() {
        return permitirNovosEstudios;
    }

    public void setPermitirNovosEstudios(boolean permitirNovosEstudios) {
        this.permitirNovosEstudios = permitirNovosEstudios;
    }
}
