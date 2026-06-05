package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "lista_espera")
public class ListaEspera {

    public enum Estado { AGUARDA, NOTIFICADO, CONVERTIDO, DESISTIU }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    /** Aluno já registado (opcional — pode ser uma pré-inscrição externa) */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column
    private String telemovel;

    @Column
    private String email;

    @Column(name = "data_inscricao", nullable = false)
    private LocalDate dataInscricao = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.AGUARDA;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public String getNomeCompleto() { return nomeCompleto; }
    public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }
    public String getTelemovel() { return telemovel; }
    public void setTelemovel(String telemovel) { this.telemovel = telemovel; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDate getDataInscricao() { return dataInscricao; }
    public void setDataInscricao(LocalDate dataInscricao) { this.dataInscricao = dataInscricao; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
