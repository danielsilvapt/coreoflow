package pt.studioflow.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Marcação de uma aula experimental numa sessão concreta (turma + dia + hora).
 * Registada pelo estúdio (ex.: contacto telefónico, balcão) ou marcada pelo próprio
 * interessado na página pública (/aula-experimental). Ao marcar cria-se (ou reutiliza-se) um
 * {@link Aluno} com estado EXPERIMENTAL inscrito na turma sem mensalidade, para
 * aparecer nas presenças dessa aula.
 */
@Entity
@Table(name = "aula_experimental", indexes = {
        @Index(name = "idx_aula_exp_studio_data", columnList = "studio_id,data")
})
public class AulaExperimental {

    public enum Estado {
        MARCADA("Marcada"), REALIZADA("Realizada"), FALTOU("Faltou"),
        CANCELADA("Cancelada"), INSCRITO("Inscreveu-se");

        private final String label;
        Estado(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum Origem {
        TELEFONE("Telefone"), PRESENCIAL("Presencial"), EMAIL("Email / mensagem"),
        ONLINE("Marcação online"), OUTRO("Outro");

        private final String label;
        Origem(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "turma_id", nullable = false)
    private Turma turma;

    /**
     * Aluno criado/reutilizado ao marcar. Sem FK na BD e tolerante a alunos apagados
     * (ex.: candidato rejeitado em "Validar Inscrições"): a marcação fica no histórico.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "aluno_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private Aluno aluno;

    @Column(name = "data", nullable = false)
    private LocalDate data;

    @Column(name = "hora_inicio")
    private LocalTime horaInicio;

    @Column(name = "hora_fim")
    private LocalTime horaFim;

    @Column(name = "nome_completo", nullable = false)
    private String nomeCompleto;

    @Column
    private String telemovel;

    @Column
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.MARCADA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origem origem = Origem.TELEFONE;

    @Column(length = 1000)
    private String observacoes;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    /** Data/hora da sessão (para ordenar e comparar com "agora"). */
    public LocalDateTime getInicio() {
        return data.atTime(horaInicio != null ? horaInicio : LocalTime.MIDNIGHT);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public LocalTime getHoraInicio() { return horaInicio; }
    public void setHoraInicio(LocalTime horaInicio) { this.horaInicio = horaInicio; }
    public LocalTime getHoraFim() { return horaFim; }
    public void setHoraFim(LocalTime horaFim) { this.horaFim = horaFim; }
    public String getNomeCompleto() { return nomeCompleto; }
    public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }
    public String getTelemovel() { return telemovel; }
    public void setTelemovel(String telemovel) { this.telemovel = telemovel; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }
    public Origem getOrigem() { return origem; }
    public void setOrigem(Origem origem) { this.origem = origem; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }
}
