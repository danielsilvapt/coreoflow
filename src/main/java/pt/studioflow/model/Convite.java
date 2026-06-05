package pt.studioflow.model;

import jakarta.persistence.*;
import pt.studioflow.model.Studio;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "convites")
public class Convite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String evento;

    private LocalDate data;

    private LocalTime hora;

    private String local;

    @Column(length = 500)
    private String observacoes;

    // Dentro da classe Convite.java
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "convite_figurinos", joinColumns = @JoinColumn(name = "convite_id"))
    @MapKeyColumn(name = "turma_id")
    @Column(name = "status_figurino")
    private Map<Long, String> statusFigurinos = new HashMap<>();
    // "OK", "EM_FALTA", "AJUSTAR"

    /**
     * O Mapa armazena: Key = ID da Turma | Value = Status da Participação
     * Isto cria uma tabela secundária 'convite_participacoes' automaticamente.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "convite_participacoes", joinColumns = @JoinColumn(name = "convite_id"))
    @MapKeyColumn(name = "turma_id")
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private Map<Long, StatusParticipacao> participacoes = new HashMap<>();

    public Convite() {
    }

    // Getters e Setters
    /** Estúdio a que pertence (multi-tenant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEvento() {
        return evento;
    }

    public void setEvento(String evento) {
        this.evento = evento;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public String getLocal() {
        return local;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public Map<Long, StatusParticipacao> getParticipacoes() {
        return participacoes;
    }

    public void setParticipacoes(Map<Long, StatusParticipacao> participacoes) {
        this.participacoes = participacoes;
    }

    public Map<Long, String> getStatusFigurinos() {
        return statusFigurinos;
    }

    public void setStatusFigurinos(Map<Long, String> statusFigurinos) {
        this.statusFigurinos = statusFigurinos;
    }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}