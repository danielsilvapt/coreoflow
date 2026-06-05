package pt.studioflow.model;

import jakarta.persistence.*;
import java.time.DayOfWeek;

@Entity
public class DiaAula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false)
    private DayOfWeek diaSemana;

    // construtores
    public DiaAula() {}
    public DiaAula(DayOfWeek diaSemana) {
        this.diaSemana = diaSemana;
    }

    // getters e setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }
    public void setDiaSemana(DayOfWeek diaSemana) {
        this.diaSemana = diaSemana;
    }
}
