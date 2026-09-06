package pt.studioflow.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Planeamento e sumário de uma aula concreta (turma + data + hora). O
 * professor planeia antes da aula ({@link #planeamento}) e regista o sumário
 * depois ({@link #sumario}); o sumário pode ser enviado à turma por email.
 */
@Entity
@Table(name = "sumario_aula")
public class SumarioAula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @Column(nullable = false)
    private LocalDate data;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fim")
    private LocalTime horaFim;

    /** Nome do professor (string, como em MarcacaoSala) — quem planeou. */
    private String professor;

    /** REGULAR, ENSAIO, EXTRA... (livre) */
    private String tipo;

    @Column(length = 4000)
    private String planeamento;

    @Column(length = 4000)
    private String sumario;

    @Column(nullable = false)
    private boolean enviado = false;

    @Column(name = "data_envio")
    private LocalDateTime dataEnvio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id")
    private Studio studio;

    public boolean temConteudo() {
        return enviado
                || (planeamento != null && !planeamento.isBlank())
                || (sumario != null && !sumario.isBlank());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }
    public void setHoraFim(LocalTime horaFim) { this.horaFim = horaFim; }
    public String getProfessor() { return professor; }
    public void setProfessor(String professor) { this.professor = professor; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getPlaneamento() { return planeamento; }
    public void setPlaneamento(String planeamento) { this.planeamento = planeamento; }
    public String getSumario() { return sumario; }
    public void setSumario(String sumario) { this.sumario = sumario; }
    public boolean isEnviado() { return enviado; }
    public void setEnviado(boolean enviado) { this.enviado = enviado; }
    public LocalDateTime getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(LocalDateTime dataEnvio) { this.dataEnvio = dataEnvio; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
