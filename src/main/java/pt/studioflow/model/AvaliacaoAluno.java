package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Avaliação periódica de um aluno por um professor.
 * Competências armazenadas como "Postura:4,Ritmo:5,Expressão:3" (nome:nota 1-5).
 */
@Entity
@Table(name = "avaliacao_aluno")
public class AvaliacaoAluno {

    public enum Nivel { INICIANTE, INTERMEDIO, AVANCADO, EXCELENTE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "turma_id")
    private Turma turma;

    @Column(nullable = false)
    private String periodo; // ex: "2024/2025 · 1º Período"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Nivel nivel = Nivel.INICIANTE;

    /** "Postura:4,Ritmo:5,Expressão:3" — nota de 1 a 5 por competência */
    @Column(columnDefinition = "TEXT")
    private String competencias;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "data_avaliacao", nullable = false)
    private LocalDate dataAvaliacao = LocalDate.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public Nivel getNivel() { return nivel; }
    public void setNivel(Nivel nivel) { this.nivel = nivel; }
    public String getCompetencias() { return competencias; }
    public void setCompetencias(String competencias) { this.competencias = competencias; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public LocalDate getDataAvaliacao() { return dataAvaliacao; }
    public void setDataAvaliacao(LocalDate dataAvaliacao) { this.dataAvaliacao = dataAvaliacao; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
