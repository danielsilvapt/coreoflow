package pt.studioflow.model;

import jakarta.persistence.*;
import pt.studioflow.model.Studio;
import java.time.LocalDateTime;

@Entity
@Table(name = "inscricao_evento")
public class InscricaoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @ManyToOne
    @JoinColumn(name = "convite_id", nullable = false)
    private Convite convite;

    // Indica se o aluno clicou que "Tinha Interesse" na área dele
    private Boolean interessado = false;

    // Indica se o PROFESSOR confirmou que o aluno vai efetivamente participar
    private Boolean confirmado = false;

    private LocalDateTime dataResposta;

    public InscricaoEvento() {
    }

    public InscricaoEvento(Aluno aluno, Convite convite) {
        this.aluno = aluno;
        this.convite = convite;
        this.dataResposta = LocalDateTime.now();
    }

    // Getters e Setters
    /** Estúdio a que pertence (multi-tenant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public Long getId() {
        return id;
    }

    public Aluno getAluno() {
        return aluno;
    }

    public void setAluno(Aluno aluno) {
        this.aluno = aluno;
    }

    public Convite getConvite() {
        return convite;
    }

    public void setConvite(Convite convite) {
        this.convite = convite;
    }

    public boolean isInteressado() {
        return Boolean.TRUE.equals(interessado);
    }

    public void setInteressado(boolean interessado) {
        this.interessado = interessado;
    }

    public boolean isConfirmado() {
        return Boolean.TRUE.equals(confirmado);
    }

    public void setConfirmado(boolean confirmado) {
        this.confirmado = confirmado;
    }

    public LocalDateTime getDataResposta() {
        return dataResposta;
    }

    public void setDataResposta(LocalDateTime dataResposta) {
        this.dataResposta = dataResposta;
    }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}