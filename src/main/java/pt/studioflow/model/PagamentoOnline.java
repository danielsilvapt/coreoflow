package pt.studioflow.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.*;

/**
 * Pagamento online (Mollie) de uma inscrição, renovação ou de mensalidades em
 * aberto pagas no portal do aluno. As compras de aulas avulso/packs têm o seu
 * próprio registo em {@link CompraCredito}.
 */
@Entity
@Table(name = "pagamento_online")
public class PagamentoOnline {

    public enum Tipo {
        INSCRICAO,
        RENOVACAO,
        MENSALIDADES
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tipo tipo;

    @Column(nullable = false)
    private BigDecimal valor;

    private String descricao;

    /** Só para {@link Tipo#MENSALIDADES}: ids das mensalidades cobertas, separados por vírgulas. */
    @Column(name = "mensalidade_ids", length = 1000)
    private String mensalidadeIds;

    @Column(name = "mollie_payment_id", unique = true)
    private String molliePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPagamentoCredito estado = EstadoPagamentoCredito.PENDENTE;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    /**
     * Inscrição/renovação: passa a true quando o valor pago é refletido nas
     * mensalidades geradas ao aceitar o pedido (evita aplicar duas vezes).
     */
    @Column(nullable = false)
    private boolean aplicado = false;

    public Long getId() { return id; }

    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }

    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }

    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getMensalidadeIds() { return mensalidadeIds; }
    public void setMensalidadeIds(String mensalidadeIds) { this.mensalidadeIds = mensalidadeIds; }

    public List<Long> getListaMensalidadeIds() {
        if (mensalidadeIds == null || mensalidadeIds.isBlank()) return List.of();
        return Arrays.stream(mensalidadeIds.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).collect(Collectors.toList());
    }

    public void setListaMensalidadeIds(List<Long> ids) {
        this.mensalidadeIds = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public String getMolliePaymentId() { return molliePaymentId; }
    public void setMolliePaymentId(String molliePaymentId) { this.molliePaymentId = molliePaymentId; }

    public EstadoPagamentoCredito getEstado() { return estado; }
    public void setEstado(EstadoPagamentoCredito estado) { this.estado = estado; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDateTime dataPagamento) { this.dataPagamento = dataPagamento; }

    public boolean isAplicado() { return aplicado; }
    public void setAplicado(boolean aplicado) { this.aplicado = aplicado; }
}
