package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "encomenda_loja")
public class EncomendaLoja {

    public enum Estado { PENDENTE, CONFIRMADA, ENTREGUE, CANCELADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "produto_id", nullable = false)
    private ProdutoLoja produto;

    @Column(nullable = false)
    private int quantidade;

    @Column(nullable = false)
    private double valorTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estado estado = Estado.PENDENTE;

    @Column(name = "data_encomenda", nullable = false)
    private LocalDateTime dataEncomenda = LocalDateTime.now();

    @Column
    private String observacoes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public ProdutoLoja getProduto() { return produto; }
    public void setProduto(ProdutoLoja produto) { this.produto = produto; }
    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
    public double getValorTotal() { return valorTotal; }
    public void setValorTotal(double valorTotal) { this.valorTotal = valorTotal; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public LocalDateTime getDataEncomenda() { return dataEncomenda; }
    public void setDataEncomenda(LocalDateTime dataEncomenda) { this.dataEncomenda = dataEncomenda; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
