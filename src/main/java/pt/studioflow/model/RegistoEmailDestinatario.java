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
 * Um aluno destinatário de uma comunicação ({@link RegistoEmail}) e o estado de
 * entrega do email a esse aluno, atualizado pelos eventos do webhook do Brevo.
 */
@Entity
@Table(name = "registo_email_destinatario", indexes = {
        @Index(name = "idx_remaild_registo", columnList = "registoId"),
        @Index(name = "idx_remaild_email", columnList = "email")
})
public class RegistoEmailDestinatario {

    /**
     * Estado de entrega. O {@code nivel} impede que um evento que chega fora de
     * ordem faça "recuar" o estado (ex.: "entregue" depois de "aberto").
     */
    public enum EstadoEntrega {
        PENDENTE("A aguardar confirmação", "#95A5A6", 0, false),
        ADIADO("Entrega adiada", "#F39C12", 1, false),
        DEVOLVIDO_TEMP("Devolvido (temporário)", "#E67E22", 1, false),
        ENTREGUE("Entregue", "#27AE60", 2, false),
        ABERTO("Aberto", "#2E86DE", 3, false),
        SPAM("Marcado como spam", "#8E44AD", 4, true),
        DEVOLVIDO("Devolvido", "#E74C3C", 5, true),
        BLOQUEADO("Bloqueado", "#C0392B", 5, true),
        INVALIDO("Email inválido", "#C0392B", 5, true),
        ERRO("Erro", "#C0392B", 5, true);

        private final String label;
        private final String cor;
        private final int nivel;
        private final boolean falha;

        EstadoEntrega(String label, String cor, int nivel, boolean falha) {
            this.label = label;
            this.cor = cor;
            this.nivel = nivel;
            this.falha = falha;
        }

        public String getLabel() { return label; }
        public String getCor() { return cor; }
        public int getNivel() { return nivel; }
        public boolean isFalha() { return falha; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long registoId;

    @Column(length = 200)
    private String nome;

    /** Email em minúsculas (comparado com o do evento do Brevo). */
    @Column(length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EstadoEntrega estado = EstadoEntrega.PENDENTE;

    /** Motivo indicado pelo Brevo (devoluções, bloqueios, adiamentos). */
    @Column(length = 1000)
    private String detalhe;

    private LocalDateTime atualizadoEm;

    private LocalDateTime abertoEm;

    public Long getId() { return id; }

    public Long getRegistoId() { return registoId; }
    public void setRegistoId(Long registoId) { this.registoId = registoId; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public EstadoEntrega getEstado() { return estado; }
    public void setEstado(EstadoEntrega estado) { this.estado = estado; }

    public String getDetalhe() { return detalhe; }
    public void setDetalhe(String detalhe) { this.detalhe = detalhe; }

    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(LocalDateTime atualizadoEm) { this.atualizadoEm = atualizadoEm; }

    public LocalDateTime getAbertoEm() { return abertoEm; }
    public void setAbertoEm(LocalDateTime abertoEm) { this.abertoEm = abertoEm; }
}
