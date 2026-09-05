package pt.studioflow.model;

import java.time.LocalDate;
import pt.studioflow.model.Studio;
import java.util.List;

import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;

@Entity
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "aluno", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlunoTurma> turmas;

    public List<AlunoTurma> getTurmas() {
        return turmas;
    }

    public void setTurmas(List<AlunoTurma> turmas) {
        this.turmas = turmas;
    }

    public List<Presenca> getPresencas() {
        return presencas;
    }

    public void setPresencas(List<Presenca> presencas) {
        this.presencas = presencas;
    }

    @Column(name = "numero_socios")
    private int numeroSocio;

    @Column(name = "nome_completo")
    private String nomeCompleto;

    @Column(name = "telemovel")
    private String telemovel;

    @Column(name = "email")
    private String email;

    @Column(name = "morada")
    private String morada;

    @Column(name = "codigo_postal")
    private String codigoPostal;

    @Column(name = "localidade")
    private String localidade;

    @Column(name = "nacionalidade")
    private String nacionalidade;

    @Column(name = "genero")
    private String genero;

    @Column(name = "n_identificacao")
    private String numeroIdentificacao;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "n_contribuinte")
    private String numeroContribuinte;

    @Column(name = "seguro_desportivo")
    private String seguroDesportivo; // Associação ou Próprio

    @Column(name = "socio")
    private int socio; // Sim ou Não

    @Column(name = "crianca")
    private Integer crianca = 1; // Sim ou Não

    @Column(name = "data_quota_pagamento")
    private LocalDate dataQuotaPagamento;

    @Column(name = "data_expiracao_quota")
    private LocalDate dataExpiracaoQuota;

    @Column(name = "data_seguro_pagamento")
    private LocalDate dataSeguroPagamento;

    @Column(name = "data_expiracao_seguro")
    private LocalDate dataExpiracaoSeguro;

    @Column(name = "data_inscricao_renovacao")
    private LocalDate dataInscricaoRenovacao;

    /** Data em que a inscrição original do aluno foi validada/ativada — preenchida uma única vez. */
    @Column(name = "data_inscricao")
    private LocalDate dataInscricao;

    @Column(name = "divida")
    private int divida; // Sim ou Não

    @Column(name = "ativo")
    private int ativo; // Sim ou Não

    @Column(name = "socio_ativo")
    private Boolean socioAtivo = false;

    @Column(name = "carimbo_data_hora")
    private LocalDate carimboDataHora;

    /** true se o pedido PENDENTE atual é uma renovação de matrícula (não uma nova inscrição). */
    @Column(name = "pedido_renovacao")
    private Boolean pedidoRenovacao = false;

    /**
     * Marcado em ValidacaoInscricoesView pelo botão "Validar Dados" - confirma que a ficha
     * foi revista pela secretaria, sem ainda ativar o aluno nem gerar mensalidades.
     */
    @Column(name = "dados_validados")
    private Boolean dadosValidados = false;

    /** Chave do objeto no bucket R2 (ver R2StorageService). Fotos deixaram de ser guardadas na BD. */
    @Column(name = "foto_chave")
    private String fotoChave;

    @OneToMany(mappedBy = "aluno", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Presenca> presencas;


    /** Estúdio a que pertence (multi-tenant). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "studio_id", nullable = false)
    private Studio studio;
    public AlunoStatus getStatus() {
        return status;
    }

    public void setStatus(AlunoStatus status) {
        this.status = status;
    }

    public enum AlunoStatus {
        PENDENTE, ATIVO, INATIVO, EXPERIMENTAL
    }

    @Column(name = "status")
    private AlunoStatus status = AlunoStatus.PENDENTE;

    // Getters e Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public void setNomeCompleto(String nomeCompleto) {
        this.nomeCompleto = nomeCompleto;
    }

    public String getGenero() {
        return genero;
    }

    public void setGenero(String genero) {
        this.genero = genero;
    }

    public String getTelemovel() {
        return telemovel;
    }

    public void setTelemovel(String telemovel) {
        this.telemovel = telemovel;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email != null ? email.trim() : null;
    }

    public String getMorada() {
        return morada;
    }

    public void setMorada(String morada) {
        this.morada = morada;
    }

    public String getCodigoPostal() {
        return codigoPostal;
    }

    public void setCodigoPostal(String codigoPostal) {
        this.codigoPostal = codigoPostal;
    }

    public String getLocalidade() {
        return localidade;
    }

    public void setLocalidade(String localidade) {
        this.localidade = localidade;
    }

    public String getNumeroIdentificacao() {
        return numeroIdentificacao;
    }

    public void setNumeroIdentificacao(String numeroIdentificacao) {
        this.numeroIdentificacao = numeroIdentificacao;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public String getNumeroContribuinte() {
        return numeroContribuinte;
    }

    public void setNumeroContribuinte(String numeroContribuinte) {
        this.numeroContribuinte = numeroContribuinte;
    }

    public String getNacionalidade() {
        return nacionalidade;
    }

    public void setNacionalidade(String nacionalidade) {
        this.nacionalidade = nacionalidade;
    }

    public String getSeguroDesportivo() {
        return seguroDesportivo;
    }

    public void setSeguroDesportivo(String seguroDesportivo) {
        this.seguroDesportivo = seguroDesportivo;
    }

    public boolean isSocio() {
        return socio == 1;
    }

    public void setSocio(boolean socio) {
        this.socio = socio ? 1 : 0;
    }

    public boolean isCrianca() {
        return crianca == 1;
    }

    public void setCrianca(boolean crianca) {
        this.crianca = crianca ? 1 : 0;
    }

    public LocalDate getDataQuotaPagamento() {
        return dataQuotaPagamento;
    }

    public void setDataQuotaPagamento(LocalDate dataQuotaPagamento) {
        this.dataQuotaPagamento = dataQuotaPagamento;
    }

    public LocalDate getDataExpiracaoQuota() {
        return dataExpiracaoQuota;
    }

    public void setDataExpiracaoQuota(LocalDate dataExpiracaoQuota) {
        this.dataExpiracaoQuota = dataExpiracaoQuota;
    }

    public LocalDate getDataSeguroPagamento() {
        return dataSeguroPagamento;
    }

    public void setDataSeguroPagamento(LocalDate dataSeguroPagamento) {
        this.dataSeguroPagamento = dataSeguroPagamento;
    }

    public LocalDate getDataExpiracaoSeguro() {
        return dataExpiracaoSeguro;
    }

    public void setDataExpiracaoSeguro(LocalDate dataExpiracaoSeguro) {
        this.dataExpiracaoSeguro = dataExpiracaoSeguro;
    }

    public LocalDate getDataInscricaoRenovacao() {
        return dataInscricaoRenovacao;
    }

    public void setDataInscricaoRenovacao(LocalDate dataInscricaoRenovacao) {
        this.dataInscricaoRenovacao = dataInscricaoRenovacao;
    }

    public LocalDate getDataInscricao() {
        return dataInscricao;
    }

    public void setDataInscricao(LocalDate dataInscricao) {
        this.dataInscricao = dataInscricao;
    }

    public boolean isDivida() {
        return divida == 1;
    }

    public void setDivida(boolean divida) {
        this.divida = divida ? 1 : 0;
    }

    public boolean isAtivo() {
        return ativo == 1;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo ? 1 : 0;
    }

    public int getNumeroSocio() {
        return numeroSocio;
    }

    public void setNumeroSocio(int numeroSocio) {
        this.numeroSocio = numeroSocio;
    }

    public LocalDate getCarimboDataHora() {
        return carimboDataHora;
    }

    public void setCarimboDataHora(LocalDate carimboDataHora) {
        this.carimboDataHora = carimboDataHora;
    }

    public String getFotoChave() {
        return fotoChave;
    }

    public void setFotoChave(String fotoChave) {
        this.fotoChave = fotoChave;
    }

    public boolean isSocioAtivo() {
        return socio == 1;
    }

    public void setSocioAtivo(boolean socioAtivo) {
        this.socioAtivo = socioAtivo;
    }



    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }

    public boolean isPedidoRenovacao() { return pedidoRenovacao != null && pedidoRenovacao; }
    public void setPedidoRenovacao(boolean pedidoRenovacao) { this.pedidoRenovacao = pedidoRenovacao; }

    public boolean isDadosValidados() { return Boolean.TRUE.equals(dadosValidados); }
    public void setDadosValidados(boolean dadosValidados) { this.dadosValidados = dadosValidados; }
}
