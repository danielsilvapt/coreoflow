package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "inquerito")
public class Inquerito {

    public enum Estado { RASCUNHO, ENVIADO, FECHADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo; // ex: "Satisfação 2024/2025"

    @Column(name = "ano_letivo")
    private String anoLetivo;

    /** Perguntas JSON: [{"id":"q1","texto":"...","tipo":"ESCALA"}, ...] */
    @Column(columnDefinition = "TEXT")
    private String perguntas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.RASCUNHO;

    @Column(name = "data_criacao")
    private LocalDate dataCriacao = LocalDate.now();

    @Column(name = "data_envio")
    private LocalDate dataEnvio;

    @Column(name = "data_fecho")
    private LocalDate dataFecho;

    @Column(name = "respostas_count")
    private int respostasCount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getAnoLetivo() { return anoLetivo; }
    public void setAnoLetivo(String anoLetivo) { this.anoLetivo = anoLetivo; }
    public String getPerguntas() { return perguntas; }
    public void setPerguntas(String perguntas) { this.perguntas = perguntas; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public LocalDate getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDate dataCriacao) { this.dataCriacao = dataCriacao; }
    public LocalDate getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(LocalDate dataEnvio) { this.dataEnvio = dataEnvio; }
    public LocalDate getDataFecho() { return dataFecho; }
    public void setDataFecho(LocalDate dataFecho) { this.dataFecho = dataFecho; }
    public int getRespostasCount() { return respostasCount; }
    public void setRespostasCount(int respostasCount) { this.respostasCount = respostasCount; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
