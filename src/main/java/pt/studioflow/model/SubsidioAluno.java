package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "subsidio_aluno")
public class SubsidioAluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @Column(nullable = false)
    private String entidade; // "IPDJ", "Câmara Municipal", "Outro"

    @Column(name = "descricao_apoio")
    private String descricaoApoio;

    /** Percentagem de desconto sobre a mensalidade (0-100) */
    @Column(nullable = false)
    private double percentagem;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_renovacao")
    private LocalDate dataRenovacao;

    @Column(nullable = false)
    private Boolean ativo = true;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public String getEntidade() { return entidade; }
    public void setEntidade(String entidade) { this.entidade = entidade; }
    public String getDescricaoApoio() { return descricaoApoio; }
    public void setDescricaoApoio(String descricaoApoio) { this.descricaoApoio = descricaoApoio; }
    public double getPercentagem() { return percentagem; }
    public void setPercentagem(double percentagem) { this.percentagem = percentagem; }
    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }
    public LocalDate getDataRenovacao() { return dataRenovacao; }
    public void setDataRenovacao(LocalDate dataRenovacao) { this.dataRenovacao = dataRenovacao; }
    public boolean isAtivo() { return Boolean.TRUE.equals(ativo); }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
