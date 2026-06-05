package pt.studioflow.model;

import jakarta.persistence.*;
import pt.studioflow.model.Studio;

@Entity
@Table(name = "professores")
public class Professor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column
    private String telefone;

    /** Valor pago por hora de aula (usado no cálculo de rentabilidade por turma). Default: 25€ */
    @Column(name = "valor_hora_aula", nullable = false)
    private double valorHoraAula = 25.0;

    // --- Construtores ---
    public Professor() {}

    public Professor(String nome, String email, String telefone) {
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
    }

    // --- Getters e Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    /** Estúdio a que pertence (multi-tenant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public double getValorHoraAula() { return valorHoraAula; }
    public void setValorHoraAula(double valorHoraAula) { this.valorHoraAula = valorHoraAula; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
