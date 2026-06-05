package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Regista ocorrências extraordinárias para uma aula num dia específico:
 * substituições de professor, cancelamentos ou reposições.
 */
@Entity
@Table(name = "ocorrencia_aula")
public class OcorrenciaAula {

    public enum Tipo { SUBSTITUICAO, CANCELAMENTO, REPOSICAO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    /** Professor substituto (se SUBSTITUICAO) */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "professor_substituto_id")
    private Professor professorSubstituto;

    /** Data original cancelada (se REPOSICAO) */
    @Column(name = "data_original_cancelada")
    private LocalDate dataOriginalCancelada;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "notificar_alunos")
    private Boolean notificarAlunos = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public Professor getProfessorSubstituto() { return professorSubstituto; }
    public void setProfessorSubstituto(Professor p) { this.professorSubstituto = p; }
    public LocalDate getDataOriginalCancelada() { return dataOriginalCancelada; }
    public void setDataOriginalCancelada(LocalDate d) { this.dataOriginalCancelada = d; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public boolean isNotificarAlunos() { return Boolean.TRUE.equals(notificarAlunos); }
    public void setNotificarAlunos(boolean notificarAlunos) { this.notificarAlunos = notificarAlunos; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
