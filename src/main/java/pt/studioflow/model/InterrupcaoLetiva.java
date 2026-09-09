package pt.studioflow.model;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * Período do ano letivo sem aulas regulares (férias de Natal/Páscoa, feriados
 * prolongados, etc.), por estúdio. Usado nas estimativas de rentabilidade para
 * não contar as ocorrências de aulas que caem dentro destes intervalos.
 */
@Entity
public class InterrupcaoLetiva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "studio_id")
    private Studio studio;

    private String descricao;

    private LocalDate dataInicio;
    private LocalDate dataFim;

    public InterrupcaoLetiva() {
    }

    public InterrupcaoLetiva(Studio studio, String descricao, LocalDate dataInicio, LocalDate dataFim) {
        this.studio = studio;
        this.descricao = descricao;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    /** True se {@code data} cai dentro deste período de interrupção (inclusive). */
    public boolean contem(LocalDate data) {
        return dataInicio != null && dataFim != null
                && !data.isBefore(dataInicio) && !data.isAfter(dataFim);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Studio getStudio() {
        return studio;
    }

    public void setStudio(Studio studio) {
        this.studio = studio;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDate dataInicio) {
        this.dataInicio = dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public void setDataFim(LocalDate dataFim) {
        this.dataFim = dataFim;
    }
}
