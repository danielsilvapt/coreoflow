package pt.studioflow.model;


import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_turma_studio_codigo", columnNames = {"studio_id", "codigo"}))
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false)
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
    // EAGER: associação pequena (0-3 profs/turma) e as views do Vaadin não têm
    // open-session-in-view — sem isto, getTodosProfessores() rebentava com
    // LazyInitializationException na navegação.
    @ManyToMany(fetch = FetchType.EAGER)
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

    /** Para quem é a turma (criança, adulto ou ambas) — filtra as opções na inscrição pública consoante a data de nascimento. */
    @Enumerated(EnumType.STRING)
    @Column(name = "publico")
    private PublicoTurma publico = PublicoTurma.AMBAS;

    @OneToMany(mappedBy = "turma", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AlunoTurma> alunosTurma;

    @OneToMany(mappedBy = "turma", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Aula> aulas;

    @Column(name = "cor")
    private String cor; // Guardará o código Hexadecimal (ex: #FF0000)

    /**
     * Mensalidade própria desta turma, que substitui a tabela do estúdio
     * (criança/adulto x 1x/2x por semana). Serve para turmas com preço fechado —
     * competição, workshops, turmas de nível avançado. A null, aplica-se a tabela
     * normal do {@link Studio}.
     */
    @Column(name = "mensalidade_socio")
    private Double mensalidadeSocio;

    /**
     * Contraparte de {@link #mensalidadeSocio} para não-sócios. A null (mas com
     * {@code mensalidadeSocio} preenchido), o não-sócio paga esse valor mais o
     * acréscimo de não-sócio do estúdio.
     */
    @Column(name = "mensalidade_nao_socio")
    private Double mensalidadeNaoSocio;

    /**
     * Mensalidade própria por frequência semanal, no formato {@code "1:35;3:45"}
     * (1 treino/semana 35€, 3 treinos/semana 45€). Tem prioridade sobre
     * {@link #mensalidadeSocio}/{@link #mensalidadeNaoSocio}, é um valor final
     * (sem acréscimo de não-sócio) e define também as frequências que o
     * formulário público oferece para esta turma.
     */
    @Column(name = "precos_frequencia")
    private String precosFrequencia;

    /** true se esta turma tem preço próprio em vez da tabela do estúdio. */
    @Transient
    public boolean temMensalidadePropria() {
        return mensalidadeSocio != null || mensalidadeNaoSocio != null || !getTabelaFrequencia().isEmpty();
    }

    /** {@link #precosFrequencia} interpretado: aulas/semana → valor, por ordem crescente. */
    @Transient
    public java.util.SortedMap<Integer, Double> getTabelaFrequencia() {
        java.util.SortedMap<Integer, Double> tabela = new java.util.TreeMap<>();
        if (precosFrequencia == null) return tabela;
        for (String par : precosFrequencia.split(";")) {
            String[] kv = par.split(":");
            if (kv.length != 2) continue;
            try {
                tabela.put(Integer.parseInt(kv[0].trim()), Double.parseDouble(kv[1].trim().replace(',', '.')));
            } catch (NumberFormatException ignored) {
                // par mal formado: ignora-se
            }
        }
        return tabela;
    }

    /** Frequências (aulas/semana) que um aluno pode escolher nesta turma. */
    @Transient
    public java.util.List<Integer> getFrequenciasDisponiveis() {
        java.util.SortedMap<Integer, Double> tabela = getTabelaFrequencia();
        return tabela.isEmpty() ? java.util.List.of(1, 2) : java.util.List.copyOf(tabela.keySet());
    }

    /** Rótulo usado nos formulários e nas notas de interesses, ex.: "3 vezes / semana". */
    public static String rotuloFrequencia(int aulasPorSemana) {
        return aulasPorSemana == 1 ? "1 vez / semana" : aulasPorSemana + " vezes / semana";
    }

    /** Inverso de {@link #rotuloFrequencia}; um rótulo ilegível conta como 2x (omissão antiga). */
    public static int frequenciaDoRotulo(String rotulo) {
        if (rotulo != null && !rotulo.isEmpty() && Character.isDigit(rotulo.charAt(0))) {
            return Character.getNumericValue(rotulo.charAt(0));
        }
        return 2;
    }

    public String getPrecosFrequencia() {
        return precosFrequencia;
    }

    public void setPrecosFrequencia(String precosFrequencia) {
        this.precosFrequencia = precosFrequencia;
    }

    public Double getMensalidadeSocio() {
        return mensalidadeSocio;
    }

    public void setMensalidadeSocio(Double mensalidadeSocio) {
        this.mensalidadeSocio = mensalidadeSocio;
    }

    public Double getMensalidadeNaoSocio() {
        return mensalidadeNaoSocio;
    }

    public void setMensalidadeNaoSocio(Double mensalidadeNaoSocio) {
        this.mensalidadeNaoSocio = mensalidadeNaoSocio;
    }

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

    /** Nunca null: turmas antigas sem valor definido contam como "ambas" (comportamento anterior à feature). */
    public PublicoTurma getPublico() {
        return publico != null ? publico : PublicoTurma.AMBAS;
    }

    public void setPublico(PublicoTurma publico) {
        this.publico = publico;
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
