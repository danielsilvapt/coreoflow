package pt.studioflow.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Registo de auditoria: quem fez o quê, em que estúdio e quando. Inclui acessos
 * (login, login falhado, logout), páginas abertas e criações/alterações/remoções
 * de dados. Gravado pelo AuditoriaService e consultável pelo superadmin em
 * /admin/acessos.
 */
@Entity
@Table(name = "registo_auditoria", indexes = {
        @Index(name = "idx_aud_data", columnList = "data"),
        @Index(name = "idx_aud_tipo", columnList = "tipo"),
        @Index(name = "idx_aud_studio", columnList = "studioId"),
        @Index(name = "idx_aud_utilizador", columnList = "utilizador")
})
public class RegistoAuditoria {

    public enum Tipo {
        LOGIN("Login", "#27AE60"),
        LOGIN_FALHADO("Login falhado", "#E74C3C"),
        LOGOUT("Logout", "#7F8C8D"),
        NAVEGACAO("Página", "#4A90E2"),
        CRIACAO("Criação", "#16A085"),
        ALTERACAO("Alteração", "#F39C12"),
        REMOCAO("Remoção", "#C0392B");

        private final String label;
        private final String cor;

        Tipo(String label, String cor) {
            this.label = label;
            this.cor = cor;
        }

        public String getLabel() {
            return label;
        }

        public String getCor() {
            return cor;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime data;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Tipo tipo;

    /** Username sem o prefixo do estúdio ("sistema" para tarefas automáticas, "público" para páginas abertas). */
    @Column(length = 150)
    private String utilizador;

    @Column(length = 30)
    private String papel;

    private Long studioId;

    @Column(length = 80)
    private String studioSlug;

    /** Nome do estúdio no momento do registo — mantém-se mesmo que o estúdio seja removido. */
    @Column(length = 150)
    private String studioNome;

    /** Classe da entidade afetada (só criação/alteração/remoção). */
    @Column(length = 80)
    private String entidade;

    @Column(length = 40)
    private String entidadeId;

    @Column(length = 4000)
    private String descricao;

    @Column(length = 64)
    private String ip;

    /** Sessão iniciada com a password mestra da plataforma. */
    private boolean passwordMestra;

    public Long getId() {
        return id;
    }

    public LocalDateTime getData() {
        return data;
    }

    public void setData(LocalDateTime data) {
        this.data = data;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public void setTipo(Tipo tipo) {
        this.tipo = tipo;
    }

    public String getUtilizador() {
        return utilizador;
    }

    public void setUtilizador(String utilizador) {
        this.utilizador = utilizador;
    }

    public String getPapel() {
        return papel;
    }

    public void setPapel(String papel) {
        this.papel = papel;
    }

    public Long getStudioId() {
        return studioId;
    }

    public void setStudioId(Long studioId) {
        this.studioId = studioId;
    }

    public String getStudioSlug() {
        return studioSlug;
    }

    public void setStudioSlug(String studioSlug) {
        this.studioSlug = studioSlug;
    }

    public String getStudioNome() {
        return studioNome;
    }

    public void setStudioNome(String studioNome) {
        this.studioNome = studioNome;
    }

    public String getEntidade() {
        return entidade;
    }

    public void setEntidade(String entidade) {
        this.entidade = entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public void setEntidadeId(String entidadeId) {
        this.entidadeId = entidadeId;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public boolean isPasswordMestra() {
        return passwordMestra;
    }

    public void setPasswordMestra(boolean passwordMestra) {
        this.passwordMestra = passwordMestra;
    }

    /** Estúdio para mostrar: nome, slug, ou "Plataforma" quando não há estúdio. */
    public String getEstudioApresentacao() {
        if (studioNome != null && !studioNome.isBlank()) {
            return studioNome;
        }
        if (studioSlug != null && !studioSlug.isBlank()) {
            return studioSlug;
        }
        return "Plataforma";
    }
}
