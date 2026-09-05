package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Um vídeo enviado por um professor para uma aula concreta (turma + data),
 * guardado no bucket R2 da plataforma. Vários vídeos podem existir para a
 * mesma turma+data (partilhado por todos os alunos inscritos, ao contrário
 * de {@link Presenca} que é por aluno).
 */
@Entity
@Table(name = "video_aula")
public class VideoAula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    @Column(nullable = false)
    private LocalDate data;

    /** Chave do objeto no bucket R2 (ex: "estudio-slug/turma-id/2026-09-05/uuid_nome.mp4"). */
    @Column(name = "chave_armazenamento", nullable = false)
    private String chaveArmazenamento;

    @Column(name = "nome_ficheiro")
    private String nomeFicheiro;

    @Column(name = "tamanho_bytes")
    private long tamanhoBytes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "professor_id")
    private Professor professor;

    @Column(name = "data_upload", nullable = false)
    private LocalDateTime dataUpload = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public String getChaveArmazenamento() { return chaveArmazenamento; }
    public void setChaveArmazenamento(String chaveArmazenamento) { this.chaveArmazenamento = chaveArmazenamento; }
    public String getNomeFicheiro() { return nomeFicheiro; }
    public void setNomeFicheiro(String nomeFicheiro) { this.nomeFicheiro = nomeFicheiro; }
    public long getTamanhoBytes() { return tamanhoBytes; }
    public void setTamanhoBytes(long tamanhoBytes) { this.tamanhoBytes = tamanhoBytes; }
    public Professor getProfessor() { return professor; }
    public void setProfessor(Professor professor) { this.professor = professor; }
    public LocalDateTime getDataUpload() { return dataUpload; }
    public void setDataUpload(LocalDateTime dataUpload) { this.dataUpload = dataUpload; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
