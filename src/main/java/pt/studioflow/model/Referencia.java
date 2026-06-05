package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "referencia")
public class Referencia {

    public enum Estado { PENDENTE, CONFIRMADA, RECOMPENSADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Aluno que fez a referência */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "referenciador_id", nullable = false)
    private Aluno referenciador;

    /** Novo aluno trazido */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "referenciado_id", nullable = false)
    private Aluno referenciado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.PENDENTE;

    /** Desconto em euros aplicado ao referenciador quando confirmado */
    @Column(name = "desconto_euros")
    private double descontoEuros = 0;

    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia = LocalDate.now();

    @Column(name = "data_confirmacao")
    private LocalDate dataConfirmacao;

    @Column
    private String observacoes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getReferenciador() { return referenciador; }
    public void setReferenciador(Aluno referenciador) { this.referenciador = referenciador; }
    public Aluno getReferenciado() { return referenciado; }
    public void setReferenciado(Aluno referenciado) { this.referenciado = referenciado; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public double getDescontoEuros() { return descontoEuros; }
    public void setDescontoEuros(double descontoEuros) { this.descontoEuros = descontoEuros; }
    public LocalDate getDataReferencia() { return dataReferencia; }
    public void setDataReferencia(LocalDate dataReferencia) { this.dataReferencia = dataReferencia; }
    public LocalDate getDataConfirmacao() { return dataConfirmacao; }
    public void setDataConfirmacao(LocalDate dataConfirmacao) { this.dataConfirmacao = dataConfirmacao; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
