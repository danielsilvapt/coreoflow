package pt.studioflow.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;

/**
 * Plano de aprendizagem de dança gerado pelo Treinador IA, por estúdio.
 *
 * <p>O conteúdo do plano (aquecimento, passos, combinações, prática) é guardado
 * como JSON em {@link #conteudoJson}; o histórico do chat com o treinador em
 * {@link #chatJson}. Assim novos campos no plano não obrigam a migrações.</p>
 */
@Entity
public class PlanoDanca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "studio_id")
    private Studio studio;

    /** Email de quem criou o plano (professor ou aluno/encarregado). */
    @Column(length = 200)
    private String criadoPorEmail;

    @Column(length = 160)
    private String criadoPorNome;

    /** Rótulo livre de para quem é o plano (nome do aluno, "turma X", etc.). */
    @Column(length = 160)
    private String paraQuem;

    @Column(length = 80)
    private String estilo;

    @Column(length = 40)
    private String nivel;

    @Column(length = 400)
    private String objetivo;

    private LocalDateTime dataCriacao = LocalDateTime.now();

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String conteudoJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String chatJson;

    /** Índices dos passos marcados como dominados, separados por vírgula (ex.: "0,2,3"). */
    @Column(length = 500)
    private String passosDominados = "";

    public boolean passoDominado(int idx) {
        if (passosDominados == null || passosDominados.isBlank()) {
            return false;
        }
        for (String s : passosDominados.split(",")) {
            if (s.trim().equals(String.valueOf(idx))) {
                return true;
            }
        }
        return false;
    }

    public void definirPassoDominado(int idx, boolean dominado) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (passosDominados != null && !passosDominados.isBlank()) {
            for (String s : passosDominados.split(",")) {
                if (!s.isBlank()) {
                    set.add(s.trim());
                }
            }
        }
        if (dominado) {
            set.add(String.valueOf(idx));
        } else {
            set.remove(String.valueOf(idx));
        }
        passosDominados = String.join(",", set);
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

    public String getCriadoPorEmail() {
        return criadoPorEmail;
    }

    public void setCriadoPorEmail(String criadoPorEmail) {
        this.criadoPorEmail = criadoPorEmail;
    }

    public String getCriadoPorNome() {
        return criadoPorNome;
    }

    public void setCriadoPorNome(String criadoPorNome) {
        this.criadoPorNome = criadoPorNome;
    }

    public String getParaQuem() {
        return paraQuem;
    }

    public void setParaQuem(String paraQuem) {
        this.paraQuem = paraQuem;
    }

    public String getEstilo() {
        return estilo;
    }

    public void setEstilo(String estilo) {
        this.estilo = estilo;
    }

    public String getNivel() {
        return nivel;
    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
    }

    public String getObjetivo() {
        return objetivo;
    }

    public void setObjetivo(String objetivo) {
        this.objetivo = objetivo;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public String getConteudoJson() {
        return conteudoJson;
    }

    public void setConteudoJson(String conteudoJson) {
        this.conteudoJson = conteudoJson;
    }

    public String getChatJson() {
        return chatJson;
    }

    public void setChatJson(String chatJson) {
        this.chatJson = chatJson;
    }

    public String getPassosDominados() {
        return passosDominados;
    }

    public void setPassosDominados(String passosDominados) {
        this.passosDominados = passosDominados;
    }
}
