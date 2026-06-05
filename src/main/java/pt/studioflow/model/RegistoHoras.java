package pt.studioflow.model;


import jakarta.persistence.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "registo_horas") // Nome da tabela no banco de dados
public class RegistoHoras {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Geração automática de ID
    private Long id; // Chave primária (ID)

    @Column(name = "professor")
    private String professor;

    @Column(name = "tipo_atividade")
    private String tipoAtividade;

    @Column(name = "inicio") // Nome da coluna para a data/hora de início
    private LocalDateTime inicio;

    @Column(name = "fim") // Nome da coluna para a data/hora de fim
    private LocalDateTime fim;

    private String turma;

    private String mes;

    // Adiciona este campo na classe RegistoHoras
    @Column(name = "observacoes", length = 500)
    private String observacoes;

    // Adiciona os Getters e Setters
    public String getObservacoes() {
        return observacoes;
    }

    public void setObservacoes(String observacoes) {
        this.observacoes = observacoes;
    }

    public String getTurma() {
        return turma;
    }

    public void setTurma(String turma) {
        this.turma = turma;
    }

    @Column(name = "mes_numero")
    private int mesNumero; // Campo para armazenar o número do mês

    @Column(name = "ano")
    private int ano; // Campo para armazenar o ano

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

    public String getProfessor() {
        return professor;
    }

    public void setProfessor(String professor) {
        this.professor = professor;
    }

    public String getTipoAtividade() {
        return tipoAtividade;
    }

    public void setTipoAtividade(String tipoAtividade) {
        this.tipoAtividade = tipoAtividade;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public void setInicio(LocalDateTime inicio) {
        this.inicio = inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public void setFim(LocalDateTime fim) {
        this.fim = fim;
    }

    public String getMes() {
        return mes;
    }

    public void setMes(String mes) {
        this.mes = mes;
    }

    public void setMesNumero(int mesNumero) {
        this.mesNumero = mesNumero;
    }

    public int getAno() {
        return ano;
    }

    public void setAno(int ano) {
        this.ano = ano;
    }

    // Método para obter o mês como número a partir da data de início (opcional)
    public int getMesNumero() {
        return inicio.getMonthValue(); // Retorna o valor numérico do mês baseado na data de início
    }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
