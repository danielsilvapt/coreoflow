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

    @Column(nullable = false)
    private String email;

    @Column
    private String telefone;

    /** Valor pago por hora de aula regular (usado quando a remuneração efetiva é HORA). Default: 25€ */
    @Column(name = "valor_hora_aula", nullable = false)
    private double valorHoraAula = 25.0;

    /**
     * Override do tipo de remuneração deste professor: "HORA" ou "PERCENTAGEM".
     * Null = herda o tipo definido no estúdio (Studio.tipoRemuneracaoProf).
     */
    @Column(name = "tipo_remuneracao")
    private String tipoRemuneracao;

    /** €/hora em ensaios. Null = herda do estúdio, depois do valorHoraAula. */
    @Column(name = "valor_hora_ensaio")
    private Double valorHoraEnsaio;

    /** €/hora em aulas privadas/workshops. Null = herda do estúdio, depois do valorHoraAula. */
    @Column(name = "valor_hora_privada")
    private Double valorHoraPrivada;

    /** % da mensalidade para alunos 1x/semana. Null = herda do estúdio. */
    @Column(name = "perc_1x")
    private Double perc1x;

    /** % da mensalidade para alunos 2x/semana. Null = herda do estúdio. */
    @Column(name = "perc_2x")
    private Double perc2x;

    /** % da mensalidade para alunos 3x/semana. Null = herda do estúdio. */
    @Column(name = "perc_3x")
    private Double perc3x;

    /** % para frequências sem valor próprio. Null = herda do estúdio. */
    @Column(name = "perc_outras")
    private Double percOutras;

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

    public String getTipoRemuneracao() { return tipoRemuneracao; }
    public void setTipoRemuneracao(String tipoRemuneracao) { this.tipoRemuneracao = tipoRemuneracao; }

    public Double getValorHoraEnsaio() { return valorHoraEnsaio; }
    public void setValorHoraEnsaio(Double v) { this.valorHoraEnsaio = v; }

    public Double getValorHoraPrivada() { return valorHoraPrivada; }
    public void setValorHoraPrivada(Double v) { this.valorHoraPrivada = v; }

    public Double getPerc1x() { return perc1x; }
    public void setPerc1x(Double v) { this.perc1x = v; }

    public Double getPerc2x() { return perc2x; }
    public void setPerc2x(Double v) { this.perc2x = v; }

    public Double getPerc3x() { return perc3x; }
    public void setPerc3x(Double v) { this.perc3x = v; }

    public Double getPercOutras() { return percOutras; }
    public void setPercOutras(Double v) { this.percOutras = v; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
