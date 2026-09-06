package pt.studioflow.model;

import jakarta.persistence.*;

@Entity
public class AlunoTurma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @ManyToOne
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @Column(name = "aulas_por_semana")
    private Integer aulasPorSemana = 2; // default 2x por semana

    /**
     * Inscrição nesta turma ainda por validar pela secretaria. Enquanto pendente,
     * o aluno aparece na turma mas não se geram mensalidades para ela.
     */
    @Column(name = "pendente")
    private Boolean pendente = false;

    /** Data em que o aluno pediu renovação da matrícula nesta turma. */
    @Column(name = "data_pedido_renovacao")
    private java.time.LocalDate dataPedidoRenovacao;

    public int getAulasPorSemana() {
        return aulasPorSemana;
    }

    public void setAulasPorSemana(int aulasPorSemana) {
        this.aulasPorSemana = aulasPorSemana;
    }

    // construtores, getters e setters
    public Long getId() {
        return id;
    }

    public boolean isAtivo() {
        return aluno.isAtivo();
    }

    public void setId(Long id) {
        this.id = id;
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

    public boolean isPendente() {
        return pendente != null && pendente;
    }

    public void setPendente(boolean pendente) {
        this.pendente = pendente;
    }

    public java.time.LocalDate getDataPedidoRenovacao() {
        return dataPedidoRenovacao;
    }

    public void setDataPedidoRenovacao(java.time.LocalDate dataPedidoRenovacao) {
        this.dataPedidoRenovacao = dataPedidoRenovacao;
    }
}
