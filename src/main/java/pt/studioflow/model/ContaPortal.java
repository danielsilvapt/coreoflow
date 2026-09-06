package pt.studioflow.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Credencial de acesso ao portal do aluno/encarregado. É por email e vive
 * fora da tabela {@code users} — o mesmo email (tipicamente de um pai) dá
 * acesso a todos os {@link Aluno} do estúdio com esse email.
 *
 * <p>A password é definida pelo próprio através de um link de convite
 * ({@link #tokenAtivacao}); enquanto {@link #ativo} for {@code false} a conta
 * não pode autenticar.</p>
 */
@Entity
@Table(name = "conta_portal", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class ContaPortal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    /** Hash BCrypt. Null até a conta ser ativada pelo link de convite. */
    @Column(name = "password_hash")
    private String passwordHash;

    private String nome;

    @Column(nullable = false)
    private boolean ativo = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    @Column(name = "token_ativacao")
    private String tokenAtivacao;

    @Column(name = "token_expira_em")
    private LocalDateTime tokenExpiraEm;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public boolean tokenValido(String token) {
        return token != null && token.equals(tokenAtivacao)
                && tokenExpiraEm != null && tokenExpiraEm.isAfter(LocalDateTime.now());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email != null ? email.trim().toLowerCase() : null; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }

    public String getTokenAtivacao() { return tokenAtivacao; }
    public void setTokenAtivacao(String tokenAtivacao) { this.tokenAtivacao = tokenAtivacao; }

    public LocalDateTime getTokenExpiraEm() { return tokenExpiraEm; }
    public void setTokenExpiraEm(LocalDateTime tokenExpiraEm) { this.tokenExpiraEm = tokenExpiraEm; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
