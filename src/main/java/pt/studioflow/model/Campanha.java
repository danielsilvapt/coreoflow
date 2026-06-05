package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "campanha")
public class Campanha {

    public enum Canal { EMAIL, WHATSAPP }
    public enum Segmento { TODOS, ATIVOS, INATIVOS, TURMA, ANIVERSARIANTES_MES }
    public enum Estado { RASCUNHO, ENVIADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Canal canal = Canal.EMAIL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Segmento segmento = Segmento.TODOS;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "turma_id")
    private Turma turmaAlvo;

    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao = LocalDateTime.now();

    @Column(name = "data_envio")
    private LocalDateTime dataEnvio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.RASCUNHO;

    @Column(name = "destinatarios_count")
    private int destinatariosCount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
    public Canal getCanal() { return canal; }
    public void setCanal(Canal canal) { this.canal = canal; }
    public Segmento getSegmento() { return segmento; }
    public void setSegmento(Segmento segmento) { this.segmento = segmento; }
    public Turma getTurmaAlvo() { return turmaAlvo; }
    public void setTurmaAlvo(Turma turmaAlvo) { this.turmaAlvo = turmaAlvo; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
    public LocalDateTime getDataEnvio() { return dataEnvio; }
    public void setDataEnvio(LocalDateTime dataEnvio) { this.dataEnvio = dataEnvio; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public int getDestinatariosCount() { return destinatariosCount; }
    public void setDestinatariosCount(int destinatariosCount) { this.destinatariosCount = destinatariosCount; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
