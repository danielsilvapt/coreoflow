package pt.studioflow.model;


import java.util.List;

import jakarta.persistence.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;

@Entity
public class Modalidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo")
    private String codigo;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "prof_responsavel")
    private String profResponsavel;

    @Column(name = "ativo")
    private Boolean ativo = false;  // Sim ou Não

    @Lob
    @Column(name = "icon", columnDefinition = "LONGBLOB")
    private byte[] icon; // Foto do aluno em Base64 (armazenada como bytes)

    @OneToMany(mappedBy = "modalidade")
    private List<Turma> turmas;

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

    public String getProfResponsavel() {
        return profResponsavel;
    }

    public void setProfResponsavel(String profResponsavel) {
        this.profResponsavel = profResponsavel;
    }

    public boolean isAtivo() {
        return Boolean.TRUE.equals(ativo);
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public byte[] getIcon() {
        return icon;
    }

    public void setIcon(byte[] icon) {
        this.icon = icon;
    }

    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
