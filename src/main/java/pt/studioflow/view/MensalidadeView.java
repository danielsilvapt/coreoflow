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
import pt.studioflow.service.VendusApiService;

@Route(value = "mensalidade", layout = MainLayout.class)
@PageTitle("Gestão de Mensalidades | CoreoFlow")
@RolesAllowed("ADMIN")
public class MensalidadeView extends VerticalLayout {

    private final MensalidadeRepository mensalidadeRepository;
    private final TurmaRepository turmaRepository;
    private final VendusApiService vendusService;

    private final Grid<Mensalidade> grid = new Grid<>(Mensalidade.class, false);
    private List<Mensalidade> listaMensalidades = new ArrayList<>();

    // Stats Cards dinâmicos
    private final Span totalPagoLabel = new Span("0.00€");
    private final Span totalDividaLabel = new Span("0.00€");
    private final Span pendentesLabel = new Span("0");

    // Filtros
    private final TextField nomeAlunoFilter = new TextField("Aluno");
    private final ComboBox<Object> turmaFilter = new ComboBox<>("Turma");
    private final ComboBox<Object> anoFilter = new ComboBox<>("Ano");
    private final ComboBox<Object> mesFilter = new ComboBox<>("Mês");
    private final ComboBox<Object> estadoFilter = new ComboBox<>("Estado");

    public MensalidadeView(MensalidadeRepository mensalidadeRepository,
            TurmaRepository turmaRepository,
            VendusApiService vendusService) {
        this.mensalidadeRepository = mensalidadeRepository;
        this.turmaRepository = turmaRepository;
        this.vendusService = vendusService;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        injectStyles();
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

                ".action-btn { " +
                "  border-radius: 10px; padding: 8px; min-width: 40px; height: 40px; " +
                "  transition: all 0.2s ease; border: 1px solid rgba(0,0,0,0.05); " +
                "  box-shadow: 0 2px 4px rgba(0,0,0,0.05); display: flex; align-items: center; justify-content: center; "
                +
                "} " +
                ".action-btn:hover { transform: translateY(-2px); box-shadow: 0 4px 8px rgba(0,0,0,0.1); } " +
                ".btn-vendus { background: #E6F4EA; color: #1E8E3E; } " +
                ".btn-obs { background: #FFF4E5; color: #FF5D13; } " +
                ".btn-del { background: #FCE8E6; color: #D93025; } " +

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
        HorizontalLayout layout = new HorizontalLayout(nomeAlunoFilter, turmaFilter, anoFilter, mesFilter,
                estadoFilter);
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

            VerticalLayout info = new VerticalLayout(new Span(m.getAluno().getNomeCompleto()),
                    new Span(m.getTurma().getDescricao()));
            info.setPadding(false);
            info.setSpacing(false);
            info.getChildren().forEach(c -> {
                if (((Span) c).getText().equals(m.getTurma().getDescricao()))
                    c.getStyle().set("font-size", "10px").set("color", "#7f8c8d");
            });
            layout.add(avatar, info);
            return layout;
        })).setHeader("Aluno / Turma").setFlexGrow(1);

        grid.addComponentColumn(this::criarSelectDesconto).setHeader("Desconto").setAutoWidth(true);
        grid.addComponentColumn(this::criarCampoValor).setHeader("Valor").setAutoWidth(true);
        grid.addComponentColumn(this::criarBadgeEstadoAlternante).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(this::criarAcoesModernas).setHeader("Ações").setWidth("180px").setFlexGrow(0)
                .setFrozenToEnd(true);
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
            m.setDesconto(novoDesconto);

            atualizarCorSelectDesconto(selectDesconto, novoDesconto);

            double novoValor = calcularValorComDesconto(m, novoDesconto);
            m.setValor(novoValor);

            mensalidadeRepository.save(m);
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
        int aulasSemanais = turmaRepository.findByIdWithAlunos(m.getTurma().getId())
                .map(turmaCarregada -> turmaCarregada.getAlunosTurma().stream()
                        .filter(at -> at.getAluno().getId().equals(m.getAluno().getId()))
                        .findFirst()
                        .map(AlunoTurma::getAulasPorSemana)
                        .orElse(1) // Fallback se não encontrar a linha específica do AlunoTurma
                ).orElse(1); // Fallback se não encontrar a turma na BD

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
        double valorBase = obterValorBaseTabela(m);

        if (tipo == null) {
            return valorBase;
        }

        // Aplica a dedução por cima do valor base correto
        return switch (tipo) {
            case DESCONTO_FAMILIARES -> Math.max(0.0, valorBase - TenantContext.getCurrentStudio().getDescontoFamiliaresEuros());
            case DESCONTO_MAIS_DE_UMA_MODALIDADE -> valorBase * (1 - (TenantContext.getCurrentStudio().getDescontoMaisModalidadesPercentagem() / 100.0));
            case DESCONTO_ELEMENTO_DIRECAO -> valorBase * (1 - (TenantContext.getCurrentStudio().getDescontoDirecaoPercentagem() / 100.0));
            case DESCONTO_MAIS_65 -> valorBase * (1 - (TenantContext.getCurrentStudio().getDescontoMais65Percentagem() / 100.0));
        };
    }

    private Component criarBadgeEstadoAlternante(Mensalidade m) {
        Button badge = new Button();
        badge.addClassName("status-badge");
        EstadoMensalidade estado = calcularEstadoEfetivo(m);

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
        badge.addClickListener(e -> {
            EstadoMensalidade novoEstado = proximoEstado(m);
            m.setEstado(novoEstado);
            mensalidadeRepository.save(m);

            // Faturação automática: ao passar para FATURADO, emite recibo Vendus se ativo
            if (novoEstado == EstadoMensalidade.FATURADO) {
                pt.studioflow.model.Studio s = pt.studioflow.config.TenantContext.getCurrentStudio();
                if (s != null && s.isFaturacaoAutomatica()
                        && s.getVendusApiKey() != null && !s.getVendusApiKey().isBlank()) {
                    tentarFaturacaoAutomatica(m);
                }
            }
            updateList();
        });
        return badge;
    }

    private Component criarAcoesModernas(Mensalidade m) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setSpacing(true);

        Button btnVendus = new Button(VaadinIcon.CASH.create(), e -> confirmarFaturacao(m));
        btnVendus.addClassNames("action-btn", "btn-vendus");

        Icon obsIcon = (m.getObservacoes() != null && !m.getObservacoes().isBlank()) ? VaadinIcon.NOTEBOOK.create()
                : VaadinIcon.EDIT.create();
        Button btnObs = new Button(obsIcon, e -> {
            ObservacoesForm obsForm = new ObservacoesForm();
            obsForm.addSaveListener(updated -> {
                mensalidadeRepository.save(updated);
                updateList();
            });
            obsForm.abrir(m);
        });
        btnObs.addClassNames("action-btn", "btn-obs");

        Button btnDel = new Button(VaadinIcon.TRASH.create(), e -> confirmarEliminacao(m));
        btnDel.addClassNames("action-btn", "btn-del");

        layout.add(btnVendus, btnObs, btnDel);
        return layout;
    }

    private Component criarCampoValor(Mensalidade m) {
        NumberField field = new NumberField();
        field.setValue(m.getValor());
        field.setSuffixComponent(new Span("€"));
        field.setWidth("90px");
        field.addThemeName("small");
        field.setReadOnly(m.getEstado() == EstadoMensalidade.PAGO);
        field.addValueChangeListener(e -> {
            if (e.getValue() != null && !e.getValue().equals(m.getValor())) {
                m.setValor(e.getValue());
                mensalidadeRepository.save(m);
                aplicarFiltros(); // Atualiza stats conforme valor mudou
            }
        });
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
        double pago = filtradas.stream().filter(m -> m.getEstado() == EstadoMensalidade.PAGO)
                .mapToDouble(Mensalidade::getValor).sum();
        double divida = filtradas.stream().filter(m -> calcularEstadoEfetivo(m) == EstadoMensalidade.EM_DIVIDA)
                .mapToDouble(Mensalidade::getValor).sum();
        long pendentes = filtradas.stream().filter(m -> m.getEstado() == EstadoMensalidade.POR_EMITIR).count();
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

        grid.setItems(filtradas);
        updateStats(filtradas); 
    }

    private void confirmarFaturacao(Mensalidade m) {
        String nif = m.getAluno().getNumeroContribuinte();
        if (nif == null || nif.isBlank()) {
            Notification.show("Aluno sem NIF!", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Emitir Fatura - Vendus");
        confirm.add(
                new Span("Confirmar emissão de " + m.getValor() + "€ para " + m.getAluno().getNomeCompleto() + "?"));
        Button btnConfirm = new Button("Emitir Agora", ev -> {
            String resCliente = vendusService.obterClientePorNIF(nif);
            if (resCliente.contains("ID Vendus: ")) {
                String idVendus = resCliente.split("ID Vendus: ")[1].replace(")", "").trim();
                String mesStr = m.getMes().getDisplayName(TextStyle.FULL, new Locale("pt", "PT"));
                String resultado = vendusService.criarFaturaComVendusPay(idVendus,
                        "Mensalidade " + mesStr + " " + m.getAno(), m.getValor());
                if (resultado.contains("Sucesso")) {
                    m.setEstado(EstadoMensalidade.FATURADO);
                    mensalidadeRepository.save(m);
                    updateList();
                    Notification.show("Fatura emitida!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                }
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
        dialog.add(new Span("Deseja realmente eliminar esta mensalidade?"));
        Button del = new Button("Eliminar", e -> {
            mensalidadeRepository.delete(m);
            updateList();
            dialog.close();
        });
        del.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), del);
        dialog.open();
    }

    private EstadoMensalidade calcularEstadoEfetivo(Mensalidade m) {
        if (m.getEstado() == EstadoMensalidade.FATURADO) {
            LocalDate dataLimite = LocalDate.of(m.getAno(), m.getMes(), 10);
            if (LocalDate.now().isAfter(dataLimite))
                return EstadoMensalidade.EM_DIVIDA;
        }
        return m.getEstado();
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
            String resultado = vendusService.criarFaturaComVendusPay(idVendus, descricao, m.getValor());

            if (resultado != null && resultado.contains("uccess")) {
                m.setEstado(EstadoMensalidade.FATURADO);
                mensalidadeRepository.save(m);
                com.vaadin.flow.component.notification.Notification
                        .show("✅ Fatura Vendus emitida automaticamente")
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_SUCCESS);
            }
        } catch (Exception ex) {
            // ignora erro silenciosamente — a faturação é melhor-esforço
        }
    }
}
