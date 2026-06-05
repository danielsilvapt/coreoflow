package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contrato_digital")
public class ContratoDigital {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @Column(nullable = false)
    private String tipo; // "Inscrição" ou "Renovação"

    @Column(name = "ano_letivo", nullable = false)
    private String anoLetivo; // "2024/2025"

    @Column(columnDefinition = "LONGTEXT")
    private String conteudo; // HTML do contrato gerado

    @Column(name = "data_geracao", nullable = false)
    private LocalDate dataGeracao = LocalDate.now();

    @Column(name = "data_assinatura")
    private LocalDateTime dataAssinatura;

    @Column(nullable = false)
    private String estado = "PENDENTE"; // PENDENTE, ASSINADO, REVOGADO

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getAnoLetivo() { return anoLetivo; }
    public void setAnoLetivo(String anoLetivo) { this.anoLetivo = anoLetivo; }
    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }
    public LocalDate getDataGeracao() { return dataGeracao; }
    public void setDataGeracao(LocalDate dataGeracao) { this.dataGeracao = dataGeracao; }
    public LocalDateTime getDataAssinatura() { return dataAssinatura; }
    public void setDataAssinatura(LocalDateTime dataAssinatura) { this.dataAssinatura = dataAssinatura; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
