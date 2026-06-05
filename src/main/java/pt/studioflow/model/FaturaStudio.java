package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "fatura_studio")
public class FaturaStudio {

    public enum Estado { PENDENTE, PAGA, VENCIDA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plano_id")
    private PlanoSubscricao plano;

    @Column(nullable = false)
    private int ano;

    @Column(nullable = false)
    private int mes;

    @Column(nullable = false)
    private double valor;

    @Column(name = "alunos_ativos")
    private int alunosAtivos;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao = LocalDate.now();

    @Column(name = "data_vencimento")
    private LocalDate dataVencimento;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.PENDENTE;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
    public PlanoSubscricao getPlano() { return plano; }
    public void setPlano(PlanoSubscricao plano) { this.plano = plano; }
    public int getAno() { return ano; }
    public void setAno(int ano) { this.ano = ano; }
    public int getMes() { return mes; }
    public void setMes(int mes) { this.mes = mes; }
    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }
    public int getAlunosAtivos() { return alunosAtivos; }
    public void setAlunosAtivos(int alunosAtivos) { this.alunosAtivos = alunosAtivos; }
    public LocalDate getDataEmissao() { return dataEmissao; }
    public void setDataEmissao(LocalDate dataEmissao) { this.dataEmissao = dataEmissao; }
    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }
    public LocalDate getDataPagamento() { return dataPagamento; }
    public void setDataPagamento(LocalDate dataPagamento) { this.dataPagamento = dataPagamento; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
}
