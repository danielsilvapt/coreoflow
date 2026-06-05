package pt.studioflow.model;

import jakarta.persistence.*;
import pt.studioflow.model.Studio;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Transferencia")
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String urgencia; // Ex: "Urgente", "Normal"
    private String categoria; // Ex: "Honorários", "Ajudas de Custo", "Campeonato"
    private String descricao; // Ex: "Sapatos_Maria Sousa"
    private BigDecimal valor;

    private String iban;
    private String destinatario;
    private String estado; // "AGUARDA_ASSINATURA", "AGUARDA_PAGAMENTO", "PAGO", "SEM_EFEITO"

    private String criadoPor;
    private LocalDateTime dataCriacao;

    private String assinadoPor1; // Registo se danielsilva assinou
    private String assinadoPor2; // Registo se verafortes assinou

    private String nomeFicheiroComprovativo; // Referência ao PDF/JPG carregado

    // Getters, Setters e Construtores
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrgencia() {
        return urgencia;
    }

    public void setUrgencia(String urgencia) {
        this.urgencia = urgencia;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public void setDestinatario(String destinatario) {
        this.destinatario = destinatario;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public void setCriadoPor(String criadoPor) {
        this.criadoPor = criadoPor;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public String getAssinadoPor1() {
        return assinadoPor1;
    }

    public void setAssinadoPor1(String assinadoPor1) {
        this.assinadoPor1 = assinadoPor1;
    }

    public String getAssinadoPor2() {
        return assinadoPor2;
    }

    public void setAssinadoPor2(String assinadoPor2) {
        this.assinadoPor2 = assinadoPor2;
    }

    /** Estúdio a que pertence (multi-tenant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public String getNomeFicheiroComprovativo() {
        return nomeFicheiroComprovativo;
    }

    public void setNomeFicheiroComprovativo(String nomeFicheiroComprovativo) {
        this.nomeFicheiroComprovativo = nomeFicheiroComprovativo;
    }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}