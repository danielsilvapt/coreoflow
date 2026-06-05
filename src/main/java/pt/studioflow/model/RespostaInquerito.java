package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resposta_inquerito")
public class RespostaInquerito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inquerito_id", nullable = false)
    private Inquerito inquerito;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    /** JSON: {"q1":4,"q2":5,"q3":"Muito satisfeito"} */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String respostas;

    @Column(name = "data_resposta", nullable = false)
    private LocalDateTime dataResposta = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Inquerito getInquerito() { return inquerito; }
    public void setInquerito(Inquerito inquerito) { this.inquerito = inquerito; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public String getRespostas() { return respostas; }
    public void setRespostas(String respostas) { this.respostas = respostas; }
    public LocalDateTime getDataResposta() { return dataResposta; }
    public void setDataResposta(LocalDateTime dataResposta) { this.dataResposta = dataResposta; }
}
