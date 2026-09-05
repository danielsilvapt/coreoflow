package pt.studioflow.model;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.*;

@Entity
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true)
    private String codigo;

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "professor_id")
    private Professor professor;

    /**
     * Co-professores da turma (além do {@link #professor} principal). O professor
     * principal continua a ser o responsável para folha de pagamento e relatórios
     * de horas; os co-professores servem para dar acesso à turma (turmas de
     * competição costumam ter vários professores).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "turma_coprofessores",
            joinColumns = @JoinColumn(name = "turma_id"),
            inverseJoinColumns = @JoinColumn(name = "professor_id"))
    private Set<Professor> coProfessores = new LinkedHashSet<>();

    @Column(name = "ativo")
    private Boolean ativo = true;

    // =========================
    // RELAÇÕES
    // =========================

    @ManyToOne
    @JoinColumn(name = "modalidade_id", nullable = false)
    private Modalidade modalidade;

    @ManyToOne
    @JoinColumn(name = "sala_id")
    private Sala sala;

    @OneToMany(mappedBy = "turma", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AlunoTurma> alunosTurma;

    @OneToMany(mappedBy = "turma", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Aula> aulas;

    @Column(name = "cor")
    private String cor; // Guardará o código Hexadecimal (ex: #FF0000)

    // Dentro da classe Turma.java
    @Column(name = "whatsapp_group_link")
    private String whatsappGroupLink;

    
    private String googleDriveFolderId; 

    public String getGoogleDriveFolderId() {
        return googleDriveFolderId;
    }

    public void setGoogleDriveFolderId(String googleDriveFolderId) {
        this.googleDriveFolderId = googleDriveFolderId;
    }

    public String getWhatsappGroupLink() {
        return whatsappGroupLink;
    }

    public void setWhatsappGroupLink(String whatsappGroupLink) {
        this.whatsappGroupLink = whatsappGroupLink;
    }

    // Getter e Setter
    public String getCor() {
        return cor;
    }

    public void setCor(String cor) {
        this.cor = cor;
    }


    /** Estúdio a que pertence (multi-tenant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;

    // =========================
    // GETTERS & SETTERS
    // =========================

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Professor getProfessor() {
        return professor;
    }

    public void setProfessor(Professor professor) {
        this.professor = professor;
    }

    public Set<Professor> getCoProfessores() {
        return coProfessores;
    }

    public void setCoProfessores(Set<Professor> coProfessores) {
        this.coProfessores = coProfessores != null ? coProfessores : new LinkedHashSet<>();
    }

    /** Professor principal + co-professores, sem nulos e sem duplicados. */
    @Transient
    public List<Professor> getTodosProfessores() {
        List<Professor> todos = new ArrayList<>();
        if (professor != null) todos.add(professor);
        if (coProfessores != null) {
            for (Professor p : coProfessores) {
                if (p != null && !todos.contains(p)) todos.add(p);
            }
        }
        return todos;
    }

    public boolean isAtivo() {
        return Boolean.TRUE.equals(ativo);
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public Modalidade getModalidade() {
        return modalidade;
    }

    public void setModalidade(Modalidade modalidade) {
        this.modalidade = modalidade;
    }

    public Sala getSala() {
        return sala;
    }

    public void setSala(Sala sala) {
        this.sala = sala;
    }

    public List<AlunoTurma> getAlunosTurma() {
        return alunosTurma;
    }

    public void setAlunosTurma(List<AlunoTurma> alunosTurma) {
        this.alunosTurma = alunosTurma;
    }

    public List<Aula> getAulas() {
        return aulas;
    }

    public void setAulas(List<Aula> aulas) {
        this.aulas = aulas;
    }


    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
