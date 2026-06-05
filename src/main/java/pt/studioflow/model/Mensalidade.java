package pt.studioflow.model;

import jakarta.persistence.*;
import pt.studioflow.model.Studio;

import java.sql.Date;
import java.time.LocalDate;
import java.time.Month;

@Entity
public class Mensalidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @ManyToOne
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma; // Vamos assumir que você tem uma entidade Turma

    private int ano;
    private Month mes;
    private LocalDate diaPagamento;

    private double valor;

    @Enumerated(EnumType.STRING)
    private EstadoMensalidade estado; // Pode ser PAGO, FATURADO ou POR_EMITIR

    // --- NOVO ATRIBUTO DE DESCONTO ---
    @Enumerated(EnumType.STRING)
    @Column(name = "desconto", length = 50) 
    private TipoDesconto desconto;

    private String observacoes;

    public Mensalidade() {
    }

    // Construtor original atualizado para incluir o desconto
    public Mensalidade(Aluno aluno, Turma turma, int ano, Month mes, EstadoMensalidade estado, TipoDesconto desconto) {
        this.aluno = aluno;
        this.turma = turma;
        this.ano = ano;
        this.mes = mes;
        this.estado = estado;
        this.desconto = desconto;
        this.observacoes = observacoes; // Mantido exatamente como tinha no original
    }


    /** Estúdio a que pertence (multi-tenant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    // Getters e Setters

    public LocalDate getDiaPagamento() {
        return diaPagamento;
    }

    public void setDiaPagamento(LocalDate diaPagamento) {
        this.diaPagamento = diaPagamento;
    }

    public Long getId() {
        return id;
    }

    public Aluno getAluno() {
        return aluno;
    }

    public void setAluno(Aluno aluno) {
        this.aluno = aluno;
    }

    public Turma getTurma() {
        return turma;
    }

    public void setTurma(Turma turma) {
        this.turma = turma;
    }

    public int getAno() {
        return ano;
    }

    public void setAno(int ano) {
        this.ano = ano;
    }

    public Month getMes() {
        return mes;
    }

    public void setMes(Month mes) {
        this.mes = mes;
    }

    public EstadoMensalidade getEstado() {
        return estado;
    }

    public void setEstado(EstadoMensalidade estado) {
        this.estado = estado;
    }

    public double getValor() {
        return valor;
    }

    public void setValor(double valor) {
        this.valor = valor;
    }

    // --- GETTER E SETTER DO DESCONTO ---
    public TipoDesconto getDesconto() {
        return desconto;
    }

    public void setDesconto(TipoDesconto desconto) {
        this.desconto = desconto;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }


    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
