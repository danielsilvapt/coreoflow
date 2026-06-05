package pt.studioflow.model;

import jakarta.persistence.*;

@Entity
@Table(name = "produto_loja")
public class ProdutoLoja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(nullable = false)
    private double preco;

    @Column(nullable = false)
    private int stock;

    @Column
    private String categoria; // ex: "Equipamento", "Vestuário", "Acessório"

    @Column
    private String imagemPath;

    @Column(nullable = false)
    private Boolean ativo = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public double getPreco() { return preco; }
    public void setPreco(double preco) { this.preco = preco; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getImagemPath() { return imagemPath; }
    public void setImagemPath(String imagemPath) { this.imagemPath = imagemPath; }
    public boolean isAtivo() { return Boolean.TRUE.equals(ativo); }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
