package pt.studioflow.model;

import jakarta.persistence.*;
import pt.studioflow.model.Studio;

@Entity
public class Sala {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "cor")
    private String cor; // opcional, para destacar salas na view

    // construtores
    public Sala() {}
    public Sala(String nome, String cor) {
        this.nome = nome;
        this.cor = cor;
    }

    // getters e setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    /** Estúdio a que pertence (multi-tenant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
