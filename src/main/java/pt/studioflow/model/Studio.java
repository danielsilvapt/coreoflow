package pt.studioflow.model;

import jakarta.persistence.*;

/**
 * Entidade que representa um estúdio (tenant).
 * Cada estúdio tem os seus próprios dados, configurações e utilizadores.
 */
@Entity
@Table(name = "studios")
public class Studio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nome completo do estúdio (ex: "CoreoFlow") */
    @Column(nullable = false)
    private String nome;

    /**
     * Slug único usado para identificar o estúdio na URL e no login.
     * Ex: "obidosdance", "lisboadance", "portofit"
     */
    @Column(nullable = false, unique = true)
    private String slug;

    /** Email de contacto principal do estúdio */
    @Column
    private String emailContacto;

    /** Cor primária da identidade visual (hex, ex: #FF5D13) */
    @Column(name = "cor_primaria")
    private String corPrimaria = "#4A90E2";

    /** Cor secundária da identidade visual */
    @Column(name = "cor_secundaria")
    private String corSecundaria = "#2D3436";

    /** Logotipo (nome do ficheiro em /images/ ou URL externa) */
    @Column(name = "logo_path")
    private String logoPath;

    /** Se o estúdio está ativo na plataforma */
    @Column(nullable = false)
    private Boolean ativo = true;

    /** Plano de subscrição do estúdio. Nulo = sem plano atribuído. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plano_id")
    private PlanoSubscricao plano;

    /**
     * Módulos ativos — lista separada por vírgulas dos valores de StudioModulo.
     * Nulo ou vazio = todos os módulos ativos (retrocompatibilidade).
     */
    @Column(name = "modulos_ativos", columnDefinition = "TEXT")
    private String modulosAtivos;

    /**
     * Campos do formulário de aluno ativos — lista separada por vírgulas dos valores de CampoAluno.
     * Nulo ou vazio = todos os campos ativos (retrocompatibilidade).
     */
    @Column(name = "campos_aluno", columnDefinition = "TEXT")
    private String camposAluno;

    /**
     * Cards do Dashboard (KPIs do ADMIN) ativos — lista separada por vírgulas dos valores
     * de DashboardCard. Nulo ou vazio = todos os cards ativos (retrocompatibilidade).
     */
    @Column(name = "dashboard_cards_ativos", columnDefinition = "TEXT")
    private String dashboardCardsAtivos;

    /**
     * Faturação automática Vendus ao marcar mensalidade como Faturado.
     * Requer vendusApiKey configurado.
     */
    @Column(name = "faturacao_automatica")
    private Boolean faturacaoAutomatica = false;

    /**
     * Tipo de remuneração dos professores: "HORA" ou "PERCENTAGEM".
     */
    @Column(name = "tipo_remuneracao_prof")
    private String tipoRemuneracaoProf = "HORA";

    /**
     * Valor de referência quando HORA: €/hora de aula regular.
     * Quando PERCENTAGEM, é ignorado (usam-se os campos percProf*).
     */
    @Column(name = "valor_remuneracao_prof")
    private Double valorRemuneracaoProf = 0.0;

    /** €/hora pago em ensaios (fallback: valorRemuneracaoProf). */
    @Column(name = "valor_hora_ensaio_prof")
    private Double valorHoraEnsaioProf;

    /** €/hora pago em aulas privadas/workshops (fallback: valorRemuneracaoProf). */
    @Column(name = "valor_hora_privada_prof")
    private Double valorHoraPrivadaProf;

    /** Percentagem da mensalidade paga ao professor por alunos com 1x/semana. */
    @Column(name = "perc_prof_1x")
    private Double percProf1x;

    /** Percentagem da mensalidade paga ao professor por alunos com 2x/semana. */
    @Column(name = "perc_prof_2x")
    private Double percProf2x;

    /** Percentagem da mensalidade paga ao professor por alunos com 3x/semana. */
    @Column(name = "perc_prof_3x")
    private Double percProf3x;

    /** Percentagem usada para frequências sem valor próprio (fallback). */
    @Column(name = "perc_prof_outras")
    private Double percProfOutras;

    // =====================================================
    // CONFIGURAÇÕES DE MENSALIDADES (por estúdio)
    // =====================================================

    @Column(name = "mensalidade_crianca_1x")
    private Double mensalidadeCrianca1x = 28.0;

    @Column(name = "mensalidade_crianca_2x")
    private Double mensalidadeCrianca2x = 33.0;

    @Column(name = "mensalidade_adulto_1x")
    private Double mensalidadeAdulto1x = 25.0;

    @Column(name = "mensalidade_adulto_2x")
    private Double mensalidadeAdulto2x = 30.0;

    @Column(name = "mensalidade_nao_socio_adicional")
    private Double mensalidadeNaoSocioAdicional = 10.0;

    // =====================================================
    // CONFIGURAÇÕES DE DESCONTOS (por estúdio)
    // =====================================================

    @Column(name = "desconto_familiares_euros")
    private Double descontoFamiliaresEuros = 5.0;

    @Column(name = "desconto_direcao_percentagem")
    private Double descontoDirecaoPercentagem = 50.0;

    @Column(name = "desconto_mais_modalidades_percentagem")
    private Double descontoMaisModalidadesPercentagem = 25.0;

    @Column(name = "desconto_mais65_percentagem")
    private Double descontoMais65Percentagem = 10.0;

    /** Taxa de inscrição cobrada uma vez a novos alunos. */
    @Column(name = "taxa_inscricao")
    private Double taxaInscricao = 0.0;

    /** Taxa cobrada ao renovar a matrícula num novo ano letivo. */
    @Column(name = "taxa_renovacao")
    private Double taxaRenovacao = 0.0;

    // =====================================================
    // IDIOMAS DO FORMULÁRIO PÚBLICO (por estúdio)
    // =====================================================

    /**
     * Idiomas disponíveis no formulário público — lista separada por vírgulas
     * dos valores de Idioma. Nulo ou vazio = apenas Português (ao contrário do
     * padrão "vazio = todos" de modulosAtivos/camposAluno: um estúdio não deve
     * expor EN/FR sem configuração explícita).
     */
    @Column(name = "idiomas_disponiveis")
    private String idiomasDisponiveis;

    // =====================================================
    // CONFIGURAÇÕES FINANCEIRAS (por estúdio)
    // =====================================================

    /** Email de quem cria transferências */
    @Column(name = "email_criador_transferencias")
    private String emailCriadorTransferencias;

    /** Email do primeiro assinante de transferências */
    @Column(name = "email_assinante1")
    private String emailAssinante1;

    /** Email do segundo assinante de transferências */
    @Column(name = "email_assinante2")
    private String emailAssinante2;

    // =====================================================
    // INTEGRAÇÕES (por estúdio)
    // =====================================================

    /** Chave API Vendus para faturação */
    @Column(name = "vendus_api_key")
    private String vendusApiKey;

    // =====================================================
    // GETTERS & SETTERS
    // =====================================================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getEmailContacto() { return emailContacto; }
    public void setEmailContacto(String emailContacto) { this.emailContacto = emailContacto; }

    public String getCorPrimaria() { return corPrimaria; }
    public void setCorPrimaria(String corPrimaria) { this.corPrimaria = corPrimaria; }

    public String getCorSecundaria() { return corSecundaria; }
    public void setCorSecundaria(String corSecundaria) { this.corSecundaria = corSecundaria; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public boolean isAtivo() { return Boolean.TRUE.equals(ativo); }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public Double getMensalidadeCrianca1x() { return mensalidadeCrianca1x; }
    public void setMensalidadeCrianca1x(Double v) { this.mensalidadeCrianca1x = v; }

    public Double getMensalidadeCrianca2x() { return mensalidadeCrianca2x; }
    public void setMensalidadeCrianca2x(Double v) { this.mensalidadeCrianca2x = v; }

    public Double getMensalidadeAdulto1x() { return mensalidadeAdulto1x; }
    public void setMensalidadeAdulto1x(Double v) { this.mensalidadeAdulto1x = v; }

    public Double getMensalidadeAdulto2x() { return mensalidadeAdulto2x; }
    public void setMensalidadeAdulto2x(Double v) { this.mensalidadeAdulto2x = v; }

    public Double getMensalidadeNaoSocioAdicional() { return mensalidadeNaoSocioAdicional; }
    public void setMensalidadeNaoSocioAdicional(Double v) { this.mensalidadeNaoSocioAdicional = v; }

    public Double getDescontoFamiliaresEuros() { return descontoFamiliaresEuros; }
    public void setDescontoFamiliaresEuros(Double v) { this.descontoFamiliaresEuros = v; }

    public Double getDescontoDirecaoPercentagem() { return descontoDirecaoPercentagem; }
    public void setDescontoDirecaoPercentagem(Double v) { this.descontoDirecaoPercentagem = v; }

    public Double getDescontoMaisModalidadesPercentagem() { return descontoMaisModalidadesPercentagem; }
    public void setDescontoMaisModalidadesPercentagem(Double v) { this.descontoMaisModalidadesPercentagem = v; }

    public Double getDescontoMais65Percentagem() { return descontoMais65Percentagem; }
    public void setDescontoMais65Percentagem(Double v) { this.descontoMais65Percentagem = v; }

    public Double getTaxaInscricao() { return taxaInscricao; }
    public void setTaxaInscricao(Double v) { this.taxaInscricao = v; }

    public Double getTaxaRenovacao() { return taxaRenovacao; }
    public void setTaxaRenovacao(Double v) { this.taxaRenovacao = v; }

    public String getIdiomasDisponiveis() { return idiomasDisponiveis; }
    public void setIdiomasDisponiveis(String idiomasDisponiveis) { this.idiomasDisponiveis = idiomasDisponiveis; }

    /** Lista de idiomas ativos para este estúdio. Nulo/vazio = apenas PT. */
    public java.util.List<Idioma> getIdiomasDisponiveisList() {
        if (idiomasDisponiveis == null || idiomasDisponiveis.isBlank()) {
            return java.util.List.of(Idioma.PT);
        }
        java.util.List<Idioma> idiomas = new java.util.ArrayList<>();
        for (String i : idiomasDisponiveis.split(",")) {
            try {
                idiomas.add(Idioma.valueOf(i.trim()));
            } catch (IllegalArgumentException ignored) {
                // valor inválido no CSV, ignora
            }
        }
        return idiomas.isEmpty() ? java.util.List.of(Idioma.PT) : idiomas;
    }

    public String getEmailCriadorTransferencias() { return emailCriadorTransferencias; }
    public void setEmailCriadorTransferencias(String v) { this.emailCriadorTransferencias = v; }

    public String getEmailAssinante1() { return emailAssinante1; }
    public void setEmailAssinante1(String emailAssinante1) { this.emailAssinante1 = emailAssinante1; }

    public String getEmailAssinante2() { return emailAssinante2; }
    public void setEmailAssinante2(String emailAssinante2) { this.emailAssinante2 = emailAssinante2; }

    public String getVendusApiKey() { return vendusApiKey; }
    public void setVendusApiKey(String vendusApiKey) { this.vendusApiKey = vendusApiKey; }

    public PlanoSubscricao getPlano() { return plano; }
    public void setPlano(PlanoSubscricao plano) { this.plano = plano; }

    public String getModulosAtivos() { return modulosAtivos; }
    public void setModulosAtivos(String modulosAtivos) { this.modulosAtivos = modulosAtivos; }

    public String getCamposAluno() { return camposAluno; }
    public void setCamposAluno(String camposAluno) { this.camposAluno = camposAluno; }

    public String getDashboardCardsAtivos() { return dashboardCardsAtivos; }
    public void setDashboardCardsAtivos(String dashboardCardsAtivos) { this.dashboardCardsAtivos = dashboardCardsAtivos; }

    public boolean isFaturacaoAutomatica() { return Boolean.TRUE.equals(faturacaoAutomatica); }
    public void setFaturacaoAutomatica(boolean faturacaoAutomatica) { this.faturacaoAutomatica = faturacaoAutomatica; }

    public String getTipoRemuneracaoProf() { return tipoRemuneracaoProf; }
    public void setTipoRemuneracaoProf(String tipoRemuneracaoProf) { this.tipoRemuneracaoProf = tipoRemuneracaoProf; }

    public Double getValorRemuneracaoProf() { return valorRemuneracaoProf; }
    public void setValorRemuneracaoProf(Double valorRemuneracaoProf) { this.valorRemuneracaoProf = valorRemuneracaoProf; }

    public Double getValorHoraEnsaioProf() { return valorHoraEnsaioProf; }
    public void setValorHoraEnsaioProf(Double v) { this.valorHoraEnsaioProf = v; }

    public Double getValorHoraPrivadaProf() { return valorHoraPrivadaProf; }
    public void setValorHoraPrivadaProf(Double v) { this.valorHoraPrivadaProf = v; }

    public Double getPercProf1x() { return percProf1x; }
    public void setPercProf1x(Double v) { this.percProf1x = v; }

    public Double getPercProf2x() { return percProf2x; }
    public void setPercProf2x(Double v) { this.percProf2x = v; }

    public Double getPercProf3x() { return percProf3x; }
    public void setPercProf3x(Double v) { this.percProf3x = v; }

    public Double getPercProfOutras() { return percProfOutras; }
    public void setPercProfOutras(Double v) { this.percProfOutras = v; }

    /** Verifica se um campo do aluno está ativo. Nulo/vazio = todos ativos. */
    public boolean hasCampo(CampoAluno campo) {
        if (camposAluno == null || camposAluno.isBlank()) return true;
        for (String c : camposAluno.split(",")) {
            if (c.trim().equals(campo.name())) return true;
        }
        return false;
    }

    /**
     * Verifica se um módulo está ativo para este estúdio.
     * Se modulosAtivos for nulo/vazio, todos os módulos estão ativos (retrocompatibilidade).
     */
    public boolean hasModulo(StudioModulo modulo) {
        if (modulosAtivos == null || modulosAtivos.isBlank()) return true;
        for (String m : modulosAtivos.split(",")) {
            if (m.trim().equals(modulo.name())) return true;
        }
        return false;
    }

    /** Verifica se um card do Dashboard está ativo. Nulo/vazio = todos ativos (retrocompatibilidade). */
    public boolean hasDashboardCard(DashboardCard card) {
        if (dashboardCardsAtivos == null || dashboardCardsAtivos.isBlank()) return true;
        for (String c : dashboardCardsAtivos.split(",")) {
            if (c.trim().equals(card.name())) return true;
        }
        return false;
    }

    @Override
    public String toString() { return nome + " (" + slug + ")"; }
}
