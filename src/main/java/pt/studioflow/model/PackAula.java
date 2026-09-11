package pt.studioflow.model;

import java.math.BigDecimal;

import jakarta.persistence.*;

/**
 * Catálogo de packs de aulas avulso que um estúdio vende (ex: "Aula Avulsa" = 1
 * aula, "Pack 10 aulas" = 10 aulas). Cada estúdio define os seus próprios packs
 * e preços — nada disto é fixo na plataforma.
 */
@Entity
@Table(name = "pack_aula")
public class PackAula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    @Column(nullable = false)
    private String nome;

    /** Número de aulas/créditos que o pack contém (1 = aula avulsa única). */
    @Column(name = "num_aulas", nullable = false)
    private Integer numAulas;

    @Column(nullable = false)
    private BigDecimal preco;

    /** Dias de validade a partir da compra. Nulo = sem expiração. */
    @Column(name = "validade_dias")
    private Integer validadeDias;

    /**
     * Se true, o crédito só pode ser usado em turmas da modalidade escolhida na
     * compra. Se false, é utilizável em qualquer turma/modalidade ativa do
     * estúdio.
     */
    @Column(name = "restrito_a_modalidade", nullable = false)
    private Boolean restritoAModalidade = false;

    @Column(nullable = false)
    private Boolean ativo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Integer getNumAulas() { return numAulas; }
    public void setNumAulas(Integer numAulas) { this.numAulas = numAulas; }
    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
    public Integer getValidadeDias() { return validadeDias; }
    public void setValidadeDias(Integer validadeDias) { this.validadeDias = validadeDias; }
    public boolean isRestritoAModalidade() { return Boolean.TRUE.equals(restritoAModalidade); }
    public void setRestritoAModalidade(boolean restritoAModalidade) { this.restritoAModalidade = restritoAModalidade; }
    public boolean isAtivo() { return Boolean.TRUE.equals(ativo); }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
