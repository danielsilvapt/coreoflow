package pt.studioflow.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

//@Entity
@Table(
    name = "presenca",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"aluno_id", "turma_id", "data"})
    }
)
@Entity
public class Presenca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    @ManyToOne(optional = false)
    @JoinColumn(name = "turma_id")
    private Turma turma;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false)
    private Boolean presente = false;

    /** Se veio de matrícula regular ou consumiu um crédito de aula avulsa/pack. Nulo = registo histórico (assume MATRICULA). */
    @Enumerated(EnumType.STRING)
    @Column(name = "origem")
    private OrigemPresenca origem;

    /** Quem/o que registou esta presença. Nulo = registo histórico manual anterior a esta funcionalidade. */
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_registo")
    private MetodoRegistoPresenca metodoRegisto;

    @Column(name = "hora_registo")
    private LocalDateTime horaRegisto;

    /** Preenchido só quando este checkin consumiu um crédito de {@link CompraCredito} (auditoria/estorno). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compra_credito_id")
    private CompraCredito compraCredito;

    public OrigemPresenca getOrigem() { return origem; }
    public void setOrigem(OrigemPresenca origem) { this.origem = origem; }
    public MetodoRegistoPresenca getMetodoRegisto() { return metodoRegisto; }
    public void setMetodoRegisto(MetodoRegistoPresenca metodoRegisto) { this.metodoRegisto = metodoRegisto; }
    public LocalDateTime getHoraRegisto() { return horaRegisto; }
    public void setHoraRegisto(LocalDateTime horaRegisto) { this.horaRegisto = horaRegisto; }
    public CompraCredito getCompraCredito() { return compraCredito; }
    public void setCompraCredito(CompraCredito compraCredito) { this.compraCredito = compraCredito; }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Aluno getAluno() {
        return aluno;
    }

    public void setAluno(Aluno aluno) {
        this.aluno = aluno;
    }

    public Turma getTurma() {
        return turma;
    }

    public void setTurma(Turma turma) {
        this.turma = turma;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public boolean isPresente() {
        return Boolean.TRUE.equals(presente);
    }

    public void setPresente(boolean presente) {
        this.presente = presente;
    }

    // getters & setters

    

}

