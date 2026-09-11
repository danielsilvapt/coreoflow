package pt.studioflow.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

/**
 * Compra de um pack/aula avulsa por um aluno e o saldo de créditos resultante.
 * Guarda um "snapshot" dos dados do pack no momento da compra (nome, nº de
 * aulas, preço) para não depender de futuras edições do catálogo {@link PackAula}.
 */
@Entity
@Table(name = "compra_credito")
public class CompraCredito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_aula_id", nullable = false)
    private PackAula packAula;

    @Column(name = "nome_pack", nullable = false)
    private String nomePack;

    @Column(name = "num_aulas_comprado", nullable = false)
    private Integer numAulasComprado;

    @Column(name = "preco_pago", nullable = false)
    private BigDecimal precoPago;

    /**
     * Modalidade escolhida no momento da compra. Só restringe a utilização do
     * crédito quando {@code packAula.restritoAModalidade == true}; caso
     * contrário é apenas informativa (contexto para a notificação a admin/prof).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modalidade_id")
    private Modalidade modalidade;

    @Column(name = "creditos_restantes", nullable = false)
    private Integer creditosRestantes;

    @Column(name = "data_compra", nullable = false)
    private LocalDateTime dataCompra;

    @Column(name = "validade_ate")
    private LocalDate validadeAte;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pagamento", nullable = false)
    private MetodoPagamentoCredito metodoPagamento;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pagamento", nullable = false)
    private EstadoPagamentoCredito estadoPagamento;

    @Column(name = "mollie_payment_id")
    private String molliePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigemCompraCredito origem;

    public boolean isValido(LocalDate hoje) {
        if (creditosRestantes == null || creditosRestantes <= 0) return false;
        return validadeAte == null || !hoje.isAfter(validadeAte);
    }

    public boolean isElegivelParaTurma(Turma turma) {
        if (!isValido(LocalDate.now())) return false;
        if (packAula != null && packAula.isRestritoAModalidade()) {
            return modalidade != null && turma.getModalidade() != null
                    && modalidade.getId().equals(turma.getModalidade().getId());
        }
        return true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public PackAula getPackAula() { return packAula; }
    public void setPackAula(PackAula packAula) { this.packAula = packAula; }
    public String getNomePack() { return nomePack; }
    public void setNomePack(String nomePack) { this.nomePack = nomePack; }
    public Integer getNumAulasComprado() { return numAulasComprado; }
    public void setNumAulasComprado(Integer numAulasComprado) { this.numAulasComprado = numAulasComprado; }
    public BigDecimal getPrecoPago() { return precoPago; }
    public void setPrecoPago(BigDecimal precoPago) { this.precoPago = precoPago; }
    public Modalidade getModalidade() { return modalidade; }
    public void setModalidade(Modalidade modalidade) { this.modalidade = modalidade; }
    public Integer getCreditosRestantes() { return creditosRestantes; }
    public void setCreditosRestantes(Integer creditosRestantes) { this.creditosRestantes = creditosRestantes; }
    public LocalDateTime getDataCompra() { return dataCompra; }
    public void setDataCompra(LocalDateTime dataCompra) { this.dataCompra = dataCompra; }
    public LocalDate getValidadeAte() { return validadeAte; }
    public void setValidadeAte(LocalDate validadeAte) { this.validadeAte = validadeAte; }
    public MetodoPagamentoCredito getMetodoPagamento() { return metodoPagamento; }
    public void setMetodoPagamento(MetodoPagamentoCredito metodoPagamento) { this.metodoPagamento = metodoPagamento; }
    public EstadoPagamentoCredito getEstadoPagamento() { return estadoPagamento; }
    public void setEstadoPagamento(EstadoPagamentoCredito estadoPagamento) { this.estadoPagamento = estadoPagamento; }
    public String getMolliePaymentId() { return molliePaymentId; }
    public void setMolliePaymentId(String molliePaymentId) { this.molliePaymentId = molliePaymentId; }
    public OrigemCompraCredito getOrigem() { return origem; }
    public void setOrigem(OrigemCompraCredito origem) { this.origem = origem; }
}
