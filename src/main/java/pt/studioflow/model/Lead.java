package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Lead / prospect comercial da CoreoFlow — uma escola, associação ou
 * organização com quem estamos em processo de venda (ainda não é um
 * Studio cliente da plataforma).
 *
 * Dado de nível de plataforma (sem studio_id), tal como PlanoSubscricao.
 * Gerido pelo SuperAdmin em /admin/leads.
 */
@Entity
@Table(name = "leads")
public class Lead {

    public enum TipoLead {
        ESCOLA_DANCA("Escola de Dança"),
        ASSOCIACAO_DESPORTIVA("Associação Desportiva"),
        GINASIO_FITNESS("Ginásio / Fitness"),
        OUTRO("Outro");

        private final String label;
        TipoLead(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum EstadoLead {
        NOVO("Novo Lead", "#7B61FF"),
        DEMO_MARCADA("Demo Marcada", "#4A90E2"),
        DEMO_REALIZADA("Demo Realizada", "#00ACC1"),
        PROPOSTA_ENVIADA("Proposta Enviada", "#FF6F00"),
        NEGOCIACAO("Em Negociação", "#F9A825"),
        GANHO("Ganho", "#27AE60"),
        PERDIDO("Perdido", "#C62828");

        private final String label;
        private final String cor;
        EstadoLead(String label, String cor) { this.label = label; this.cor = cor; }
        public String getLabel() { return label; }
        public String getCor() { return cor; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nome da escola/organização (ex: "RiSa by ADCR") */
    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoLead tipo = TipoLead.ESCOLA_DANCA;

    /** Nome da pessoa de contacto (ex: "Sandra Silva") */
    @Column(name = "nome_contacto")
    private String nomeContacto;

    @Column
    private String telefone;

    @Column
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoLead estado = EstadoLead.NOVO;

    /** Data da demo (agendada ou realizada) */
    @Column(name = "data_demo")
    private LocalDate dataDemo;

    /** De onde veio o lead: Indicação, Instagram, Prospecção direta, etc. */
    @Column
    private String origem;

    /** Próxima ação a fazer (ex: "Confirmar demo por WhatsApp") */
    @Column(name = "proximo_passo")
    private String proximoPasso;

    @Column(name = "data_proximo_passo")
    private LocalDate dataProximoPasso;

    /** Valor mensal estimado (€) se este lead fechar, para previsão de receita. */
    @Column(name = "valor_mensal_estimado")
    private Double valorMensalEstimado = 0.0;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void aoCriar() {
        LocalDateTime agora = LocalDateTime.now();
        createdAt = agora;
        updatedAt = agora;
    }

    @PreUpdate
    protected void aoAtualizar() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public TipoLead getTipo() { return tipo; }
    public void setTipo(TipoLead tipo) { this.tipo = tipo; }
    public String getNomeContacto() { return nomeContacto; }
    public void setNomeContacto(String nomeContacto) { this.nomeContacto = nomeContacto; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public EstadoLead getEstado() { return estado; }
    public void setEstado(EstadoLead estado) { this.estado = estado; }
    public LocalDate getDataDemo() { return dataDemo; }
    public void setDataDemo(LocalDate dataDemo) { this.dataDemo = dataDemo; }
    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }
    public String getProximoPasso() { return proximoPasso; }
    public void setProximoPasso(String proximoPasso) { this.proximoPasso = proximoPasso; }
    public LocalDate getDataProximoPasso() { return dataProximoPasso; }
    public void setDataProximoPasso(LocalDate dataProximoPasso) { this.dataProximoPasso = dataProximoPasso; }
    public Double getValorMensalEstimado() { return valorMensalEstimado; }
    public void setValorMensalEstimado(Double valorMensalEstimado) { this.valorMensalEstimado = valorMensalEstimado; }
    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() { return nome; }
}
