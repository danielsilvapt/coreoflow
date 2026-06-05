package pt.studioflow.model;

import java.time.LocalDate;

import jakarta.persistence.*;

//@Entity
@Table(
    name = "presenca",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"aluno_id", "turma_id", "data"})
    }
)
@Entity
public class Presenca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    @ManyToOne(optional = false)
    @JoinColumn(name = "turma_id")
    private Turma turma;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private Boolean presente = false;

    public Long getId() {
        return id;
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

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public boolean isPresente() {
        return Boolean.TRUE.equals(presente);
    }

    public void setPresente(boolean presente) {
        this.presente = presente;
    }

    // getters & setters

    

}

