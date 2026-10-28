package pt.studioflow.view;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.TipoDesconto;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.TocOnlineApiService;
import pt.studioflow.service.VendusApiService;

@Route(value = "mensalidade", layout = MainLayout.class)
@PageTitle("Gestão de Mensalidades | CoreoFlow")
@RolesAllowed("ADMIN")
public class MensalidadeView extends VerticalLayout {

    private final MensalidadeRepository mensalidadeRepository;
    private final TurmaRepository turmaRepository;
    private final VendusApiService vendusService;
    private final TocOnlineApiService tocOnlineService;

    private final Grid<Mensalidade> grid = new Grid<>(Mensalidade.class, false);
    private List<Mensalidade> listaMensalidades = new ArrayList<>();

    // Stats Cards dinâmicos
    private final Span receitaEstimadaLabel = new Span("0.00€");
    private final Span totalPagoLabel = new Span("0.00€");
    private final Span totalDividaLabel = new Span("0.00€");
    private final Span pendentesLabel = new Span("0");

    // Filtros
    private final TextField nomeAlunoFilter = new TextField("Aluno");
    private final ComboBox<Object> turmaFilter = new ComboBox<>("Turma");
    private final ComboBox<Object> anoFilter = new ComboBox<>("Ano");
    private final ComboBox<Object> mesFilter = new ComboBox<>("Mês");
    private final ComboBox<Object> estadoFilter = new ComboBox<>("Estado");
    private final Button btnFaturarTodos = new Button("Faturar Todos", VaadinIcon.FILE_TEXT.create());
    private List<Mensalidade> ultimaFiltragem = new ArrayList<>();

    private final pt.studioflow.config.MensalidadeConfig mensalidadeConfig;
    private final pt.studioflow.service.MensalidadeService mensalidadeService;

    // Modelo por aulas/semana: cada linha da grelha representa o pacote do aluno no mês;
    // aqui ficam as partes (uma por turma) de cada linha, indexadas pelo id da linha.
    private java.util.Map<Long, List<Mensalidade>> pacotes = new java.util.HashMap<>();

    public MensalidadeView(MensalidadeRepository mensalidadeRepository,
            TurmaRepository turmaRepository,
            VendusApiService vendusService,
            TocOnlineApiService tocOnlineService,
            pt.studioflow.config.MensalidadeConfig mensalidadeConfig,
            pt.studioflow.service.MensalidadeService mensalidadeService) {
        this.mensalidadeRepository = mensalidadeRepository;
        this.turmaRepository = turmaRepository;
        this.vendusService = vendusService;
        this.tocOnlineService = tocOnlineService;
        this.mensalidadeConfig = mensalidadeConfig;
        this.mensalidadeService = mensalidadeService;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        injectStyles();
        ViewUtils.injetarEstiloBotoesAcao();
        configurarFiltros();
        configurarGrid();

        add(criarHeader(), criarStatsCards(), criarToolbarFiltros(), grid);
        updateList();
    }

    private void injectStyles() {
        String styles = ".status-badge { " +
                "  padding: 6px 12px; border-radius: 20px; font-weight: 700; font-size: 0.75rem; " +
                "  text-transform: uppercase; cursor: pointer; transition: all 0.2s ease; " +
                "  display: inline-flex; align-items: center; gap: 6px; border: 1px solid rgba(0,0,0,0.05); " +
                "  box-shadow: 0 2px 4px rgba(0,0,0,0.05); " +
                "} " +
                ".status-badge:hover { transform: scale(1.05); filter: brightness(1.05); } " +
                ".status-pago { background: #E6F4EA; color: #1E8E3E; } " +
                ".status-faturado { background: #E8F0FE; color: #1967D2; } " +
                ".status-divida { background: #FCE8E6; color: #D93025; } " +
                ".status-pendente { background: #F1F3F4; color: #5F6368; } " +

                ".stat-card { background: white; padding: 20px; border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); border: 1px solid #eee; min-width: 220px; } "
                +
                ".stat-title { color: #7f8c8d; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 1px; font-weight: 600; } "
                +
                ".stat-value { font-size: 1.6rem; font-weight: 800; display: block; margin-top: 5px; } " +
                ".filter-bar { background: rgba(255,255,255,0.5); backdrop-filter: blur(10px); padding: 15px; border-radius: 12px; margin: 15px 0; border: 1px solid #e0e0e0; }";

        UI.getCurrent().getElement().executeJs(
                "const style = document.createElement('style'); style.textContent = $0; document.head.appendChild(style);",
                styles);
    }

    private Component criarHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        H2 titulo = new H2("Gestão Financeira");
        titulo.getStyle().set("margin", "0").set("color", "#2D3436");

        Button btnRefresh = new Button(VaadinIcon.REFRESH.create(), e -> updateList());
        btnRefresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        header.add(titulo, btnRefresh);
        return header;
    }

    private Component criarStatsCards() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.getStyle().set("margin-top", "10px");

        layout.add(
                criarStatCard("Receita Estimada", receitaEstimadaLabel, "#8E24AA", VaadinIcon.TRENDING_UP),
                criarStatCard("Liquidado", totalPagoLabel, "#1E8E3E", VaadinIcon.CHECK_CIRCLE),
                criarStatCard("Em Dívida", totalDividaLabel, "#D93025", VaadinIcon.CLOSE_CIRCLE),
                criarStatCard("Por Faturar", pendentesLabel, "#5F6368", VaadinIcon.FILE_PROCESS));
        return layout;
    }

    private Div criarStatCard(String titulo, Span valueLabel, String color, VaadinIcon icon) {
        Div card = new Div();
        card.addClassName("stat-card");
        Icon i = icon.create();
        i.setColor(color);
        i.getStyle().set("float", "right").set("opacity", "0.8");
        Span t = new Span(titulo);
        t.addClassName("stat-title");
        valueLabel.addClassName("stat-value");
        valueLabel.getStyle().set("color", color);
        card.add(i, t, valueLabel);
        return card;
    }

    private Component criarToolbarFiltros() {
        btnFaturarTodos.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnFaturarTodos.setVisible(false);
        btnFaturarTodos.addClickListener(e -> confirmarFaturarTodos());

        HorizontalLayout layout = new HorizontalLayout(nomeAlunoFilter, turmaFilter, anoFilter, mesFilter,
                estadoFilter, btnFaturarTodos);
        layout.addClassName("filter-bar");
        layout.setWidthFull();
        layout.setAlignItems(Alignment.END);
        return layout;
    }

    private void configurarFiltros() {
        nomeAlunoFilter.setPlaceholder("Pesquisar aluno...");
        nomeAlunoFilter.setPrefixComponent(VaadinIcon.SEARCH.create());
        nomeAlunoFilter.setClearButtonVisible(true);
        nomeAlunoFilter.setValueChangeMode(ValueChangeMode.EAGER);
        nomeAlunoFilter.addValueChangeListener(e -> aplicarFiltros());

        List<Object> turmas = new ArrayList<>();
        turmas.add("Todas");
        pt.studioflow.model.Studio studio = TenantContext.getCurrentStudio();
        List<Turma> turmasDisponiveis = studio != null
                ? turmaRepository.findAllByStudio(studio)
                : turmaRepository.findAll();
        turmas.addAll(turmasDisponiveis.stream().filter(Turma::isAtivo).collect(Collectors.toList()));
        turmaFilter.setItems(turmas);
        turmaFilter
                .setItemLabelGenerator(obj -> (obj instanceof Turma) ? ((Turma) obj).getDescricao() : obj.toString());
        turmaFilter.setValue("Todas");
        turmaFilter.addValueChangeListener(e -> aplicarFiltros());

        int anoAtual = LocalDate.now().getYear();
        anoFilter.setItems("Todos", anoAtual - 1, anoAtual, anoAtual + 1);
        anoFilter.setValue(anoAtual);
        anoFilter.addValueChangeListener(e -> aplicarFiltros());

        List<Object> mesesLista = new ArrayList<>();
        mesesLista.add("Todos");
        for (Month m : Month.values()) {
            mesesLista.add(m.getDisplayName(TextStyle.FULL, new Locale("pt", "PT")));
        }
        mesFilter.setItems(mesesLista);
        mesFilter.setValue(LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "PT")));
        mesFilter.addValueChangeListener(e -> aplicarFiltros());

        estadoFilter.setItems("Todos", "Pago", "Por Emitir", "Faturado", "Em Dívida");
        estadoFilter.setValue("Todos");
        estadoFilter.addValueChangeListener(e -> aplicarFiltros());
    }

    private void configurarGrid() {
        grid.setHeightFull();
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);

        grid.addComponentColumn(this::criarAcoesModernas).setHeader("Ações").setWidth("180px").setFlexGrow(0)
                .setFrozen(true);

        grid.addColumn(m -> m == null ? 0
                : m.getAno() + "/" + m.getMes().getDisplayName(TextStyle.SHORT, new Locale("pt", "PT")))
                .setHeader("Período").setAutoWidth(true).setSortable(true);

        grid.addColumn(new ComponentRenderer<>(m -> {
            HorizontalLayout layout = new HorizontalLayout();
            layout.setAlignItems(Alignment.CENTER);
            Div avatar = new Div();
            avatar.setText(m.getAluno().getNomeCompleto().substring(0, 1).toUpperCase());
            avatar.getStyle().set("width", "32px").set("height", "32px").set("background", "#F1F3F4")
                    .set("border-radius", "50%").set("display", "flex").set("align-items", "center")
                    .set("justify-content", "center").set("font-weight", "bold").set("color", "#FF5D13");

            List<Mensalidade> partes = partes(m);
            Span turmas = new Span(partes.size() > 1
                    ? partes.size() + " aulas/semana · " + partes.stream()
                            .map(p -> p.getTurma().getDescricao()).collect(Collectors.joining(", "))
                    : m.getTurma().getDescricao());
            turmas.getStyle().set("font-size", "10px").set("color", "#7f8c8d");
            VerticalLayout info = new VerticalLayout(new Span(m.getAluno().getNomeCompleto()), turmas);
            info.setPadding(false);
            info.setSpacing(false);
            layout.add(avatar, info);
            return layout;
        })).setHeader("Aluno / Turma").setFlexGrow(1);

        grid.addComponentColumn(this::criarSelectDesconto).setHeader("Desconto").setAutoWidth(true);
        grid.addComponentColumn(this::criarCampoValor).setHeader("Valor").setAutoWidth(true);
        grid.addComponentColumn(this::criarBadgeEstadoAlternante).setHeader("Estado").setAutoWidth(true);
    }

    private Component criarSelectDesconto(Mensalidade m) {
        // PROTEÇÃO: Se a mensalidade chegar nula, devolve um componente vazio
        // imediatamente
        if (m == null) {
            return new Span("");
        }

        Select<TipoDesconto> selectDesconto = new Select<>();

        selectDesconto.setItems(TipoDesconto.values());
        selectDesconto.setEmptySelectionAllowed(true);
        selectDesconto.setPlaceholder("Sem Desconto");

        selectDesconto.setItemLabelGenerator(tipo -> {
            if (tipo == null)
                return "Sem Desconto";
            return switch (tipo) {
                case DESCONTO_FAMILIARES -> "Desconto Familiares";
                case DESCONTO_MAIS_DE_UMA_MODALIDADE -> "Mais que uma Modalidade";
                case DESCONTO_ELEMENTO_DIRECAO -> "Elemento Direção";
                case DESCONTO_MAIS_65 -> "Mais de 65 Anos";
            };
        });

        selectDesconto.setValue(m.getDesconto());

        atualizarCorSelectDesconto(selectDesconto, m.getDesconto());

        selectDesconto.addValueChangeListener(event -> {
            TipoDesconto novoDesconto = event.getValue();
            atualizarCorSelectDesconto(selectDesconto, novoDesconto);

            List<Mensalidade> partes = partes(m);
            if (estudioPorPacote()) {
                // Pacote: o desconto incide sobre o escalão total e é repartido pelas partes.
                pt.studioflow.model.Studio s = TenantContext.getCurrentStudio();
                double escalao = s.isModeloModalidades()
                        ? mensalidadeService.valorPacoteAtual(m.getAluno(), s, m.getMes())
                        : mensalidadeConfig.valorTabelaHoras(s, partes.size(),
                                mensalidadeConfig.isMesMeioMensalidade(m.getMes()));
                partes.forEach(p -> p.setDesconto(novoDesconto));
                repartir(partes, aplicarDesconto(escalao, novoDesconto));
                mensalidadeRepository.saveAll(partes);
            } else {
                m.setDesconto(novoDesconto);
                m.setValor(calcularValorComDesconto(m, novoDesconto));
                mensalidadeRepository.save(m);
            }
            updateList();
        });

        selectDesconto.setWidth("200px");
        return selectDesconto;
    }

    private double obterValorBaseTabela(Mensalidade m) {
        // 1. Determinar se é Criança ou Adulto
        boolean esCrianca = m.getAluno().isCrianca();

        // 2. Procurar a turma com a coleção "alunosTurma" já carregada de forma
        // antecipada
        var turmaCarregada = turmaRepository.findByIdWithAlunos(m.getTurma().getId()).orElse(null);
        int aulasSemanais = turmaCarregada == null ? 1
                : turmaCarregada.getAlunosTurma().stream()
                        .filter(at -> at.getAluno().getId().equals(m.getAluno().getId()))
                        .findFirst()
                        .map(AlunoTurma::getAulasPorSemana)
                        .orElse(1);

        // 2b. Turma com mensalidade própria (competição, workshops) — valor fechado,
        // usado tal-qual como base para o cálculo de descontos.
        if (turmaCarregada != null && turmaCarregada.temMensalidadePropria()) {
            Double proprio = turmaCarregada.getTabelaFrequencia().isEmpty()
                    ? (turmaCarregada.getMensalidadeSocio() != null
                            ? turmaCarregada.getMensalidadeSocio()
                            : turmaCarregada.getMensalidadeNaoSocio())
                    : mensalidadeConfig.valorProprioDaTurma(turmaCarregada, true, aulasSemanais);
            if (proprio != null) return proprio;
        }

        // 3. Determinar o preço final de tabela baseado nas propriedades
        if (esCrianca) {
            return (aulasSemanais == 2) ? TenantContext.getCurrentStudio().getMensalidadeCrianca2x() : TenantContext.getCurrentStudio().getMensalidadeCrianca1x();
        } else {
            return (aulasSemanais == 2) ? TenantContext.getCurrentStudio().getMensalidadeAdulto2x() : TenantContext.getCurrentStudio().getMensalidadeAdulto1x();
        }
    }

    // Método auxiliar atualizado que força o estilo diretamente no elemento HTML
    private void atualizarCorSelectDesconto(Select<TipoDesconto> select, TipoDesconto tipo) {
        var style = select.getElement().getStyle();

        if (tipo == null) {
            style.set("background-color", "#f3f4f6");
            style.set("color", "#6b7280");
            style.set("font-weight", "normal");
            style.set("border-radius", "var(--lumo-size-s)");
            return;
        }

        style.set("font-weight", "600");
        style.set("border-radius", "var(--lumo-size-s)");

        switch (tipo) {
            case DESCONTO_FAMILIARES -> {
                style.set("background-color", "#e0f2fe"); // Azul suave
                style.set("color", "#0369a1");
            }
            case DESCONTO_MAIS_DE_UMA_MODALIDADE -> {
                style.set("background-color", "#fef3c7"); // Amarelo suave
                style.set("color", "#b45309");
            }
            case DESCONTO_ELEMENTO_DIRECAO -> {
                style.set("background-color", "#dcfce7"); // Verde suave
                style.set("color", "#15803d");
            }
            case DESCONTO_MAIS_65 -> {
                style.set("background-color", "#f3e8ff"); // Roxo suave
                style.set("color", "#6b21a8");
            }
        }
    }

    private double calcularValorComDesconto(Mensalidade m, TipoDesconto tipo) {
        // Busca o valor base (25, 28, 30 ou 33) definido nas propriedades
        return aplicarDesconto(obterValorBaseTabela(m), tipo);
    }

    private double aplicarDesconto(double valorBase, TipoDesconto tipo) {
        if (tipo == null) {
            return valorBase;
        }

        // Aplica a dedução por cima do valor base correto
        return switch (tipo) {
            case DESCONTO_FAMILIARES -> mensalidadeConfig.aplicarDescontoFamiliar(TenantContext.getCurrentStudio(), valorBase);
            case DESCONTO_MAIS_DE_UMA_MODALIDADE -> valorBase * (1 - (TenantContext.getCurrentStudio().getDescontoMaisModalidadesPercentagem() / 100.0));
            case DESCONTO_ELEMENTO_DIRECAO -> valorBase * (1 - (TenantContext.getCurrentStudio().getDescontoDirecaoPercentagem() / 100.0));
            case DESCONTO_MAIS_65 -> valorBase * (1 - (TenantContext.getCurrentStudio().getDescontoMais65Percentagem() / 100.0));
        };
    }

    private Component criarBadgeEstadoAlternante(Mensalidade m) {
        Button badge = new Button();
        badge.addClassName("status-badge");
        EstadoMensalidade estado = calcularEstadoEfetivo(m);
        List<Mensalidade> partes = partes(m);
        boolean misto = partes.stream().map(this::calcularEstadoEfetivo).distinct().count() > 1;

        switch (estado) {
            case PAGO -> {
                badge.setText("Pago");
                badge.addClassName("status-pago");
                badge.setIcon(VaadinIcon.CHECK.create());
            }
            case FATURADO -> {
                badge.setText("Faturado");
                badge.addClassName("status-faturado");
                badge.setIcon(VaadinIcon.FILE_TEXT.create());
            }
            case EM_DIVIDA -> {
                badge.setText("Dívida");
                badge.addClassName("status-divida");
                badge.setIcon(VaadinIcon.WARNING.create());
            }
            case POR_EMITIR -> {
                badge.setText("Pendente");
                badge.addClassName("status-pendente");
                badge.setIcon(VaadinIcon.CLOCK.create());
            }
        }
        if (misto) {
            badge.setText("Misto");
            badge.getElement().setAttribute("title", partes.stream()
                    .map(p -> p.getTurma().getDescricao() + ": " + calcularEstadoEfetivo(p))
                    .collect(Collectors.joining("\n")));
        }
        badge.addClickListener(e -> {
            EstadoMensalidade novoEstado = proximoEstado(m);
            partes.forEach(p -> p.setEstado(novoEstado));
            mensalidadeRepository.saveAll(partes);

            // Modelo por aulas/semana: o mês é um pacote único, pagar uma parte paga as restantes
            // (incluindo partes que os filtros atuais escondem).
            int outrasPagas = mensalidadeService.marcarPacoteDoMesComoPago(m,
                    pt.studioflow.config.TenantContext.getCurrentStudio());
            if (outrasPagas > 0) {
                Notification.show("Também marcadas como pagas " + outrasPagas
                        + " outra(s) mensalidade(s) de " + m.getAluno().getNomeCompleto() + " neste mês.")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }

            // Faturação automática: ao passar para FATURADO, emite uma fatura (do pacote inteiro)
            // no programa configurado (se ativo)
            if (novoEstado == EstadoMensalidade.FATURADO) {
                pt.studioflow.model.Studio s = pt.studioflow.config.TenantContext.getCurrentStudio();
                if (s != null && s.isFaturacaoAutomatica()) {
                    if (s.isProgramaFaturacaoTocOnline() && s.isTocOnlineLigado()) {
                        tentarFaturacaoAutomaticaTocOnline(m, s);
                    } else if (!s.isProgramaFaturacaoTocOnline()
                            && s.getVendusApiKey() != null && !s.getVendusApiKey().isBlank()) {
                        tentarFaturacaoAutomatica(m);
                    }
                }
            }
            updateList();
        });
        return badge;
    }

    private Component criarAcoesModernas(Mensalidade m) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setSpacing(true);

        Button btnVendus = ViewUtils.botaoAcao(VaadinIcon.CASH, "btn-vendus", "Faturar", e -> confirmarFaturacao(m));

        boolean temObs = m.getObservacoes() != null && !m.getObservacoes().isBlank();
        Button btnObs = ViewUtils.botaoAcao(temObs ? VaadinIcon.NOTEBOOK : VaadinIcon.EDIT, "btn-obs", "Observações", e -> {
            ObservacoesForm obsForm = new ObservacoesForm();
            obsForm.addSaveListener(updated -> {
                mensalidadeRepository.save(updated);
                updateList();
            });
            obsForm.abrir(m);
        });

        Button btnDel = ViewUtils.botaoEliminar(e -> confirmarEliminacao(m));

        layout.add(btnVendus, btnObs, btnDel);
        return layout;
    }

    private Component criarCampoValor(Mensalidade m) {
        // Num pacote (modelo por aulas/semana) o campo mostra e edita o total do mês;
        // um total novo é repartido igualmente pelas partes.
        List<Mensalidade> partes = partes(m);
        double total = arred(partes.stream().mapToDouble(Mensalidade::getValor).sum());
        NumberField field = new NumberField();
        field.setValue(total);
        field.setSuffixComponent(new Span("€"));
        field.setWidth("90px");
        field.addThemeName("small");
        field.setReadOnly(partes.stream().anyMatch(p -> p.getEstado() == EstadoMensalidade.PAGO));
        field.addValueChangeListener(e -> {
            if (e.getValue() != null && Math.abs(e.getValue() - total) > 0.001) {
                repartir(partes, e.getValue());
                mensalidadeRepository.saveAll(partes);
                aplicarFiltros(); // Atualiza stats conforme valor mudou
            }
        });

        // Em dívida com multa configurada: mostra o total efetivo (valor + multa) como
        // referência, mas o campo continua a editar o valor base da mensalidade.
        pt.studioflow.model.Studio studio = TenantContext.getCurrentStudio();
        double comMulta = partes.stream().mapToDouble(p -> mensalidadeConfig.valorComMulta(p, studio)).sum();
        if (comMulta > total + 0.001) {
            field.setHelperText(String.format("Com multa: %.2f €", comMulta));
        }
        return field;
    }

    public void updateList() {
        pt.studioflow.model.Studio studio = TenantContext.getCurrentStudio();
        listaMensalidades = studio != null
                ? mensalidadeRepository.findAllByStudio(studio)
                : mensalidadeRepository.findAll();
        aplicarFiltros();
    }

    private void updateStats(List<Mensalidade> filtradas) {
        // Receita estimada: soma de TODAS as mensalidades filtradas, incluindo as
        // POR_EMITIR — o que se espera faturar com os filtros atuais.
        double estimada = filtradas.stream().mapToDouble(Mensalidade::getValor).sum();
        double pago = filtradas.stream().filter(m -> m.getEstado() == EstadoMensalidade.PAGO)
                .mapToDouble(Mensalidade::getValor).sum();
        pt.studioflow.model.Studio studioAtual = TenantContext.getCurrentStudio();
        double divida = filtradas.stream().filter(m -> calcularEstadoEfetivo(m) == EstadoMensalidade.EM_DIVIDA)
                .mapToDouble(m -> mensalidadeConfig.valorComMulta(m, studioAtual)).sum();
        long pendentes = filtradas.stream().filter(m -> m.getEstado() == EstadoMensalidade.POR_EMITIR).count();
        receitaEstimadaLabel.setText(String.format("%.2f€", estimada));
        totalPagoLabel.setText(String.format("%.2f€", pago));
        totalDividaLabel.setText(String.format("%.2f€", divida));
        pendentesLabel.setText(String.valueOf(pendentes));
    }

    private void aplicarFiltros() {
        List<Mensalidade> filtradas = listaMensalidades.stream().filter(m -> {
            boolean matches = true;
            if (!nomeAlunoFilter.getValue().isEmpty())
                matches &= m.getAluno().getNomeCompleto().toLowerCase()
                        .contains(nomeAlunoFilter.getValue().toLowerCase());
            if (turmaFilter.getValue() instanceof Turma t)
                matches &= m.getTurma().getId().equals(t.getId());
            if (anoFilter.getValue() instanceof Integer ano)
                matches &= m.getAno() == ano;
            if (mesFilter.getValue() != null && !mesFilter.getValue().equals("Todos")) {
                String mesPt = m.getMes().getDisplayName(TextStyle.FULL, new Locale("pt", "PT"));
                matches &= mesPt.equalsIgnoreCase(mesFilter.getValue().toString());
            }
            if (estadoFilter.getValue() != null && !estadoFilter.getValue().equals("Todos"))
                matches &= calcularEstadoEfetivo(m).name()
                        .equalsIgnoreCase(traduzEstadoFiltro(estadoFilter.getValue().toString()));
            return matches;
        })
                .sorted(Comparator.comparing(m -> m.getAluno().getNomeCompleto(), String.CASE_INSENSITIVE_ORDER)) // <--
                                                                                                                  // Linha
                                                                                                                  // adicionada
                .collect(Collectors.toList());

        List<Mensalidade> linhas = agruparEmPacotes(filtradas);
        grid.setItems(linhas);
        updateStats(filtradas);

        // Num estúdio por pacote, faturar abrange o pacote inteiro de cada aluno mostrado.
        ultimaFiltragem = linhas.stream().flatMap(m -> partes(m).stream()).distinct().collect(Collectors.toList());
        boolean turmaEspecifica = turmaFilter.getValue() instanceof Turma;
        boolean mesEspecifico = mesFilter.getValue() != null && !mesFilter.getValue().equals("Todos");
        long porFaturar = ultimaFiltragem.stream().filter(m -> m.getEstado() == EstadoMensalidade.POR_EMITIR).count();

        btnFaturarTodos.setVisible(turmaEspecifica && mesEspecifico);
        btnFaturarTodos.setText("Faturar Todos (" + porFaturar + ")");
        btnFaturarTodos.setEnabled(porFaturar > 0);
    }

    // =====================================================
    // PACOTES (modelos por aulas/semana e por modalidades)
    // =====================================================

    private boolean estudioPorPacote() {
        pt.studioflow.model.Studio s = TenantContext.getCurrentStudio();
        return s != null && s.isModeloPacote();
    }

    /**
     * Chave do pacote de uma mensalidade: aluno + ano + mês. Turmas com mensalidade
     * própria (competição, workshops) são cobranças à parte e ficam sozinhas.
     */
    private String chavePacote(Mensalidade m) {
        if (mensalidadeConfig.valorProprioDaTurma(m.getTurma(), m.getAluno().isSocio()) != null) {
            return "m" + m.getId();
        }
        return m.getAluno().getId() + "-" + m.getAno() + "-" + m.getMes();
    }

    /**
     * Nos estúdios por aulas/semana devolve uma linha por pacote (aluno/mês), com
     * todas as partes — incluindo as que os filtros escondem, ex.: filtro de turma —
     * guardadas em {@link #pacotes}. Nos outros estúdios devolve a lista tal como está.
     */
    private List<Mensalidade> agruparEmPacotes(List<Mensalidade> filtradas) {
        pacotes = new java.util.HashMap<>();
        if (!estudioPorPacote()) {
            return filtradas;
        }
        java.util.Map<String, List<Mensalidade>> todasPorChave = listaMensalidades.stream()
                .sorted(Comparator.comparing(Mensalidade::getId))
                .collect(Collectors.groupingBy(this::chavePacote, java.util.LinkedHashMap::new, Collectors.toList()));
        java.util.Set<String> vistas = new java.util.HashSet<>();
        List<Mensalidade> linhas = new ArrayList<>();
        for (Mensalidade m : filtradas) {
            String chave = chavePacote(m);
            if (vistas.add(chave)) {
                List<Mensalidade> partes = todasPorChave.getOrDefault(chave, List.of(m));
                pacotes.put(m.getId(), partes);
                linhas.add(m);
            }
        }
        return linhas;
    }

    /** Partes do pacote representado por esta linha (só ela própria fora do modelo por pacote). */
    private List<Mensalidade> partes(Mensalidade m) {
        return pacotes.getOrDefault(m.getId(), List.of(m));
    }

    /** Reparte {@code total} igualmente pelas partes; os cêntimos que sobram vão para a última. */
    private void repartir(List<Mensalidade> partes, double total) {
        double parte = arred(total / partes.size());
        for (int i = 0; i < partes.size(); i++) {
            partes.get(i).setValor(i < partes.size() - 1 ? parte : arred(total - parte * (partes.size() - 1)));
        }
    }

    private static double arred(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double totalPacote(Mensalidade m) {
        return arred(partes(m).stream().mapToDouble(Mensalidade::getValor).sum());
    }

    private void marcarPacoteFaturado(Mensalidade m) {
        List<Mensalidade> partes = partes(m);
        partes.forEach(p -> p.setEstado(EstadoMensalidade.FATURADO));
        mensalidadeRepository.saveAll(partes);
    }

    private void confirmarFaturarTodos() {
        List<Mensalidade> porFaturar = ultimaFiltragem.stream()
                .filter(m -> m.getEstado() == EstadoMensalidade.POR_EMITIR)
                .collect(Collectors.toList());
        if (porFaturar.isEmpty())
            return;

        String turmaDescricao = (turmaFilter.getValue() instanceof Turma t) ? t.getDescricao() : "";
        String mesDescricao = mesFilter.getValue() != null ? mesFilter.getValue().toString() : "";

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Faturar Todos");
        dialog.add(new Span("Marcar como Faturado " + porFaturar.size() + " mensalidade(s) de "
                + turmaDescricao + " (" + mesDescricao + ")?"));
        Button confirmar = new Button("Faturar Todos", e -> {
            porFaturar.forEach(m -> m.setEstado(EstadoMensalidade.FATURADO));
            mensalidadeRepository.saveAll(porFaturar);
            updateList();
            dialog.close();
            Notification.show(porFaturar.size() + " mensalidade(s) marcada(s) como Faturado")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        confirmar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), confirmar);
        dialog.open();
    }

    private void confirmarFaturacao(Mensalidade m) {
        String nif = m.getAluno().getNumeroContribuinte();
        if (nif == null || nif.isBlank()) {
            Notification.show("Aluno sem NIF!", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        pt.studioflow.model.Studio studio = TenantContext.getCurrentStudio();
        boolean tocOnline = studio != null && studio.isProgramaFaturacaoTocOnline();

        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Emitir Fatura - " + (tocOnline ? "TOCOnline" : "Vendus"));
        confirm.add(
                new Span("Confirmar emissão de " + totalPacote(m) + "€ para " + m.getAluno().getNomeCompleto() + "?"));
        Button btnConfirm = new Button("Emitir Agora", ev -> {
            String mesStr = m.getMes().getDisplayName(TextStyle.FULL, new Locale("pt", "PT"));
            String descricao = "Mensalidade " + mesStr + " " + m.getAno();
            boolean sucesso;
            if (tocOnline) {
                String resultado = tocOnlineService.emitirFatura(studio, nif, m.getAluno().getNomeCompleto(),
                        descricao, totalPacote(m));
                sucesso = resultado.contains("Sucesso");
                if (!sucesso) {
                    Notification.show(resultado, 5000, Notification.Position.MIDDLE)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            } else {
                String resCliente = vendusService.obterClientePorNIF(nif);
                sucesso = false;
                if (resCliente.contains("ID Vendus: ")) {
                    String idVendus = resCliente.split("ID Vendus: ")[1].replace(")", "").trim();
                    String resultado = vendusService.criarFaturaComVendusPay(idVendus, descricao, totalPacote(m));
                    sucesso = resultado.contains("Sucesso");
                }
            }
            if (sucesso) {
                marcarPacoteFaturado(m);
                updateList();
                Notification.show("Fatura emitida!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }
            confirm.close();
        });
        btnConfirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        confirm.getFooter().add(new Button("Cancelar", e -> confirm.close()), btnConfirm);
        confirm.open();
    }

    private void confirmarEliminacao(Mensalidade m) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Eliminar Registo");
        List<Mensalidade> partes = partes(m);
        dialog.add(new Span(partes.size() > 1
                ? "Deseja realmente eliminar as " + partes.size() + " mensalidades deste mês (todas as turmas)?"
                : "Deseja realmente eliminar esta mensalidade?"));
        Button del = new Button("Eliminar", e -> {
            mensalidadeRepository.deleteAll(partes);
            updateList();
            dialog.close();
        });
        del.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), del);
        dialog.open();
    }

    private EstadoMensalidade proximoEstado(Mensalidade m) {
        return switch (m.getEstado()) {
            case POR_EMITIR -> EstadoMensalidade.FATURADO;
            case FATURADO -> EstadoMensalidade.PAGO;
            case PAGO -> EstadoMensalidade.POR_EMITIR;
            case EM_DIVIDA -> EstadoMensalidade.PAGO;
        };
    }

    private EstadoMensalidade calcularEstadoEfetivo(Mensalidade m) {
        return mensalidadeConfig.estadoEfetivo(m, TenantContext.getCurrentStudio());
    }

    private String traduzEstadoFiltro(String pt) {
        return switch (pt) {
            case "Pago" -> "PAGO";
            case "Por Emitir" -> "POR_EMITIR";
            case "Faturado" -> "FATURADO";
            case "Em Dívida" -> "EM_DIVIDA";
            default -> "";
        };
    }

    private void tentarFaturacaoAutomatica(Mensalidade m) {
        try {
            String nif = m.getAluno() != null ? m.getAluno().getNumeroContribuinte() : null;
            if (nif == null || nif.isBlank()) return;

            String resCliente = vendusService.obterClientePorNIF(nif);
            if (!resCliente.contains("ID Vendus: ")) return;

            String idVendus = resCliente.split("ID Vendus: ")[1].replace(")", "").trim();
            String descricao = "Mensalidade " + m.getMes().getDisplayName(
                    java.time.format.TextStyle.FULL, new java.util.Locale("pt")) + " " + m.getAno();
            String resultado = vendusService.criarFaturaComVendusPay(idVendus, descricao, totalPacote(m));

            if (resultado != null && resultado.contains("uccess")) {
                marcarPacoteFaturado(m);
                com.vaadin.flow.component.notification.Notification
                        .show("✅ Fatura Vendus emitida automaticamente")
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
            }
        } catch (Exception ex) {
            // ignora erro silenciosamente — a faturação é melhor-esforço
        }
    }

    private void tentarFaturacaoAutomaticaTocOnline(Mensalidade m, pt.studioflow.model.Studio studio) {
        try {
            String nif = m.getAluno() != null ? m.getAluno().getNumeroContribuinte() : null;
            if (nif == null || nif.isBlank()) return;

            String descricao = "Mensalidade " + m.getMes().getDisplayName(
                    java.time.format.TextStyle.FULL, new java.util.Locale("pt")) + " " + m.getAno();
            String resultado = tocOnlineService.emitirFatura(studio, nif, m.getAluno().getNomeCompleto(),
                    descricao, totalPacote(m));

            if (resultado != null && resultado.contains("Sucesso")) {
                marcarPacoteFaturado(m);
                com.vaadin.flow.component.notification.Notification
                        .show("✅ Fatura TOCOnline emitida automaticamente")
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
            }
        } catch (Exception ex) {
            // ignora erro silenciosamente — a faturação é melhor-esforço
        }
    }
}
