package pt.studioflow.model;

import jakarta.persistence.*;

/**
 * Plano de subscrição da plataforma CoreoFlow.
 * Define preço, limite de alunos e módulos incluídos.
 */
@Entity
@Table(name = "plano_subscricao")
public class PlanoSubscricao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome; // "Basic", "Pro", "Enterprise"

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "preco_mensal", nullable = false)
    private double precoMensal;

    /** Máximo de alunos ativos. -1 = ilimitado */
    @Column(name = "limite_alunos", nullable = false)
    private int limiteAlunos = -1;

    /**
     * Módulos incluídos — lista separada por vírgulas de StudioModulo.
     * Nulo = todos os módulos.
     */
    @Column(name = "modulos_incluidos", columnDefinition = "TEXT")
    private String modulosIncluidos;

    @Column(nullable = false)
    private Boolean ativo = true;

    /** Cor do badge na UI */
    @Column
    private String cor = "#4A90E2";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public double getPrecoMensal() { return precoMensal; }
    public void setPrecoMensal(double precoMensal) { this.precoMensal = precoMensal; }
    public int getLimiteAlunos() { return limiteAlunos; }
    public void setLimiteAlunos(int limiteAlunos) { this.limiteAlunos = limiteAlunos; }
    public String getModulosIncluidos() { return modulosIncluidos; }
    public void setModulosIncluidos(String modulosIncluidos) { this.modulosIncluidos = modulosIncluidos; }
    public boolean isAtivo() { return Boolean.TRUE.equals(ativo); }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }

    @Override public String toString() { return nome; }
}
