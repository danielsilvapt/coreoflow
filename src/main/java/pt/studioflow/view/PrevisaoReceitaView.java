package pt.studioflow.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import software.xdev.vaadin.chartjs.ChartContainer;

import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.AulaRepository;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.RegistoHorasRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.SubsidioAlunoRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.RemuneracaoService;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "previsao-receita", layout = MainLayout.class)
@PageTitle("Previsão de Receita | CoreoFlow")
@RolesAllowed({"ADMIN", "SUPERADMIN"})
public class PrevisaoReceitaView extends VerticalLayout {

    record LinhaProjecao(String periodo, long alunosAtivos, double receitaBase,
                         double descontosSubsidios, double custoProfessores,
                         double receitaLiquida, String tendencia) {}

    private final MensalidadeRepository mensalidadeRepo;
    private final AlunoRepository alunoRepo;
    private final StudioRepository studioRepo;
    private final SubsidioAlunoRepository subsidioRepo;
    private final TurmaRepository turmaRepo;
    private final RegistoHorasRepository registoHorasRepo;
    private final AlunoTurmaRepository alunoTurmaRepo;
    private final AulaRepository aulaRepo;
    private final RemuneracaoService remuneracaoService;

    public PrevisaoReceitaView(MensalidadeRepository mensalidadeRepo,
                                AlunoRepository alunoRepo,
                                StudioRepository studioRepo,
                                SubsidioAlunoRepository subsidioRepo,
                                TurmaRepository turmaRepo,
                                RegistoHorasRepository registoHorasRepo,
                                AlunoTurmaRepository alunoTurmaRepo,
                                AulaRepository aulaRepo,
                                RemuneracaoService remuneracaoService) {
        this.mensalidadeRepo = mensalidadeRepo;
        this.alunoRepo = alunoRepo;
        this.studioRepo = studioRepo;
        this.subsidioRepo = subsidioRepo;
        this.turmaRepo = turmaRepo;
        this.registoHorasRepo = registoHorasRepo;
        this.alunoTurmaRepo = alunoTurmaRepo;
        this.aulaRepo = aulaRepo;
        this.remuneracaoService = remuneracaoService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        Studio studio = TenantContext.getCurrentStudio();
        boolean isSA = studio == null;

        H2 titulo = new H2("Previsão de Receita");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        // Para SA: selector de estúdio
        if (isSA) {
            ComboBox<Studio> studioCombo = new ComboBox<>("Estúdio");
            studioCombo.setItems(studioRepo.findAll().stream().filter(Studio::isAtivo).toList());
            studioCombo.setItemLabelGenerator(Studio::getNome);
            studioCombo.setWidth("280px");
            studioCombo.addValueChangeListener(e -> {
                removeAll();
                add(titulo);
                add(studioCombo);
                if (e.getValue() != null) {
                    VerticalLayout dash = criarDashboard(e.getValue());
                    add(dash);
                    expand(dash);
                }
            });
            add(studioCombo);
            return;
        }

        VerticalLayout dash = criarDashboard(studio);
        add(dash);
        expand(dash);
    }

    private VerticalLayout criarDashboard(Studio studio) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSizeFull();

        List<Mensalidade> todas = mensalidadeRepo.findAllByStudio(studio);
        List<Aluno> alunos = alunoRepo.findAllByStudio(studio);
        long alunosAtivos = alunos.stream().filter(a -> a.getStatus() == Aluno.AlunoStatus.ATIVO).count();

        List<pt.studioflow.model.Turma> turmas = turmaRepo.findAllByStudio(studio);
        java.util.Set<Long> turmaIds = turmas.stream()
                .map(pt.studioflow.model.Turma::getId).collect(java.util.stream.Collectors.toSet());
        RemuneracaoService.Dados dadosRemun = new RemuneracaoService.Dados()
                .mensalidades(todas)
                .registos(registoHorasRepo.findAllByStudio(studio))
                .aulas(aulaRepo.findByTurmaStudio(studio))
                .inscricoes(alunoTurmaRepo.findAll().stream()
                        .filter(at -> at.getTurma() != null && turmaIds.contains(at.getTurma().getId()))
                        .toList());

        // Receita real dos últimos 6 meses
        double receitaHistorica = todas.stream()
                .filter(m -> m.getEstado() == EstadoMensalidade.PAGO)
                .filter(m -> LocalDate.of(m.getAno(), m.getMes(), 1)
                        .isAfter(LocalDate.now().minusMonths(6)))
                .mapToDouble(Mensalidade::getValor).sum();

        double mediaHistorica = receitaHistorica / 6.0;

        // Receita prevista nos próximos 6 meses (baseada na média)
        double descontoSubsidios = subsidioRepo.findByStudioOrderByDataRenovacaoAsc(studio).stream()
                .filter(s -> s.isAtivo())
                .mapToDouble(s -> mediaHistorica * (s.getPercentagem() / 100.0) / alunosAtivos)
                .sum();

        // Cards de resumo
        HorizontalLayout cards = new HorizontalLayout(
                cardMetrica("Alunos Ativos", String.valueOf(alunosAtivos), "#1976D2"),
                cardMetrica("Média Mensal (6m)", String.format("%.0f €", mediaHistorica), "#27AE60"),
                cardMetrica("Receita Anual Prev.", String.format("%.0f €", mediaHistorica * 12), "#E67E22"),
                cardMetrica("Descontos/Subsídios", String.format("%.0f €/mês", descontoSubsidios), "#7B1FA2")
        );
        cards.setWidthFull();
        cards.getStyle().set("flex-wrap", "wrap").set("gap", "12px").set("margin-bottom", "16px");

        // Tabela de projeção mês a mês
        List<LinhaProjecao> linhas = new ArrayList<>();
        LocalDate hoje = LocalDate.now();
        for (int i = -3; i <= 6; i++) {
            LocalDate mes = hoje.plusMonths(i);
            String periodo = mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt"))
                    + " " + mes.getYear();

            // Real: buscar do histórico
            final int anoFinal = mes.getYear();
            final Month mesFinal = mes.getMonth();
            double receitaReal = todas.stream()
                    .filter(m -> m.getAno() == anoFinal && m.getMes() == mesFinal
                            && m.getEstado() == EstadoMensalidade.PAGO)
                    .mapToDouble(Mensalidade::getValor).sum();

            boolean futuro = mes.isAfter(hoje);
            double base = futuro ? mediaHistorica : receitaReal;

            java.time.YearMonth ym = java.time.YearMonth.from(mes);
            double custoProfs = remuneracaoService.rentabilidadePorTurma(turmas, studio, ym, dadosRemun)
                    .values().stream().mapToDouble(x -> x[1]).sum();

            double liquida = base - (futuro ? descontoSubsidios : 0) - custoProfs;
            String tendencia = futuro ? "📈 Previsão" : (receitaReal >= mediaHistorica * 0.9 ? "✅" : "⚠️");

            linhas.add(new LinhaProjecao(periodo, alunosAtivos, base, futuro ? descontoSubsidios : 0,
                    custoProfs, liquida, tendencia));
        }

        Grid<LinhaProjecao> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setHeight("300px");

        grid.addColumn(LinhaProjecao::periodo).setHeader("Período").setFlexGrow(1);
        grid.addColumn(l -> l.alunosAtivos() + " alunos").setHeader("Alunos").setAutoWidth(true);
        grid.addColumn(l -> String.format("%.2f €", l.receitaBase())).setHeader("Receita Base").setAutoWidth(true);
        grid.addColumn(l -> l.descontosSubsidios() > 0
                ? String.format("-%.2f €", l.descontosSubsidios()) : "—")
                .setHeader("Descontos").setAutoWidth(true);
        grid.addColumn(l -> l.custoProfessores() > 0
                ? String.format("-%.2f €", l.custoProfessores()) : "—")
                .setHeader("Custo Professores").setAutoWidth(true);
        grid.addComponentColumn(l -> {
            Span s = new Span(String.format("%.2f €", l.receitaLiquida()));
            s.getStyle().set("font-weight", "700")
                    .set("color", l.receitaLiquida() >= 0 ? "#27AE60" : "#E74C3C");
            return s;
        }).setHeader("Líquido após Profs").setAutoWidth(true);
        grid.addColumn(LinhaProjecao::tendencia).setHeader("Estado").setAutoWidth(true);

        grid.setItems(linhas);

        Component grafico = criarGraficoPrevisao(linhas);

        H3 tituloGrafico = new H3("Receita e resultado líquido — real e previsão");
        tituloGrafico.getStyle().set("margin", "4px 0 0 0");
        H3 tituloGrid = new H3("Projeção por Mês (últimos 3 + próximos 6)");
        tituloGrid.getStyle().set("margin", "8px 0 0 0");

        layout.add(cards, tituloGrafico, grafico, tituloGrid, grid);
        layout.expand(grafico);
        return layout;
    }

    // Gráfico de linha com a receita base e o resultado líquido, ao longo de 10 meses
    // (últimos 3 reais + próximos 6 de previsão). A parte de previsão fica a tracejado.
    private Component criarGraficoPrevisao(List<LinhaProjecao> linhas) {
        int n = linhas.size();
        int idxFut = (int) linhas.stream().filter(l -> !l.tendencia().contains("Previs")).count();
        if (idxFut < 1) {
            idxFut = 1;
        }

        List<String> labels = new ArrayList<>();
        for (LinhaProjecao l : linhas) {
            String[] p = l.periodo().split(" ");
            String m = p[0].length() >= 3 ? p[0].substring(0, 3) : p[0];
            m = Character.toUpperCase(m.charAt(0)) + m.substring(1).toLowerCase();
            String y = p.length > 1 && p[1].length() >= 4 ? " '" + p[1].substring(2) : "";
            labels.add(m + y);
        }

        List<Double> recReal = new ArrayList<>();
        List<Double> recPrev = new ArrayList<>();
        List<Double> liqReal = new ArrayList<>();
        List<Double> liqPrev = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LinhaProjecao l = linhas.get(i);
            boolean fut = i >= idxFut;
            boolean ligacao = i == idxFut - 1; // último ponto real também entra na série "previsão", para ligar as linhas
            recReal.add(fut ? null : round2(l.receitaBase()));
            liqReal.add(fut ? null : round2(l.receitaLiquida()));
            recPrev.add((fut || ligacao) ? round2(l.receitaBase()) : null);
            liqPrev.add((fut || ligacao) ? round2(l.receitaLiquida()) : null);
        }

        List<Map<String, Object>> datasets = new ArrayList<>();
        datasets.add(dataset("Receita", recReal, "#27AE60", "rgba(39,174,96,0.14)", true, false));
        datasets.add(dataset("Receita (previsão)", recPrev, "#27AE60", "rgba(0,0,0,0)", false, true));
        datasets.add(dataset("Líquido", liqReal, "#1976D2", "rgba(25,118,210,0.12)", true, false));
        datasets.add(dataset("Líquido (previsão)", liqPrev, "#1976D2", "rgba(0,0,0,0)", false, true));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        data.put("datasets", datasets);

        Map<String, Object> legend = new LinkedHashMap<>();
        legend.put("display", false);
        Map<String, Object> tooltip = new LinkedHashMap<>();
        tooltip.put("backgroundColor", "#2D3436");
        tooltip.put("padding", 12);
        tooltip.put("cornerRadius", 8);
        tooltip.put("displayColors", true);
        Map<String, Object> plugins = new LinkedHashMap<>();
        plugins.put("legend", legend);
        plugins.put("tooltip", tooltip);

        Map<String, Object> interaction = new LinkedHashMap<>();
        interaction.put("mode", "index");
        interaction.put("intersect", false);

        Map<String, Object> gridY = new LinkedHashMap<>();
        gridY.put("color", "#eef0f2");
        Map<String, Object> scaleY = new LinkedHashMap<>();
        scaleY.put("beginAtZero", true);
        scaleY.put("grid", gridY);
        Map<String, Object> gridX = new LinkedHashMap<>();
        gridX.put("display", false);
        Map<String, Object> scaleX = new LinkedHashMap<>();
        scaleX.put("grid", gridX);
        Map<String, Object> scales = new LinkedHashMap<>();
        scales.put("y", scaleY);
        scales.put("x", scaleX);

        Map<String, Object> animation = new LinkedHashMap<>();
        animation.put("duration", 900);
        animation.put("easing", "easeOutQuart");

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("responsive", true);
        options.put("maintainAspectRatio", false);
        options.put("interaction", interaction);
        options.put("animation", animation);
        options.put("plugins", plugins);
        options.put("scales", scales);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", "line");
        config.put("data", data);
        config.put("options", options);

        String json;
        try {
            json = new ObjectMapper().writeValueAsString(config);
        } catch (Exception ex) {
            json = "{}";
        }

        ChartContainer chart = new ChartContainer() {
        };
        chart.setSizeFull();
        chart.getStyle().set("min-height", "320px");
        chart.showChart(json);

        HorizontalLayout legenda = new HorizontalLayout(
                itemLegenda("#27AE60", "Receita base"),
                itemLegenda("#1976D2", "Líquido (após professores e descontos)"),
                itemLegenda("#95a5a6", "— — previsão (próximos meses)"));
        legenda.getStyle().set("gap", "20px").set("flex-wrap", "wrap").set("font-size", "12px")
                .set("color", "#555").set("margin-top", "6px");

        Div wrap = new Div(chart, legenda);
        wrap.setWidthFull();
        wrap.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("background", "white").set("border-radius", "14px").set("padding", "16px")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)").set("flex", "1").set("min-height", "0");
        return wrap;
    }

    private Map<String, Object> dataset(String label, List<Double> dados, String cor, String fundo,
            boolean fill, boolean tracejado) {
        Map<String, Object> ds = new LinkedHashMap<>();
        ds.put("label", label);
        ds.put("data", dados);
        ds.put("borderColor", cor);
        ds.put("backgroundColor", fundo);
        ds.put("fill", fill);
        ds.put("tension", 0.4);
        ds.put("borderWidth", 3);
        ds.put("pointRadius", 3);
        ds.put("pointHoverRadius", 6);
        ds.put("pointBackgroundColor", cor);
        ds.put("spanGaps", false);
        if (tracejado) {
            ds.put("borderDash", List.of(8, 6));
        }
        return ds;
    }

    private Span itemLegenda(String cor, String texto) {
        Span dot = new Span();
        dot.getStyle().set("display", "inline-block").set("width", "12px").set("height", "12px")
                .set("border-radius", "3px").set("background", cor).set("margin-right", "7px");
        Span item = new Span(dot, new Span(texto));
        item.getStyle().set("display", "inline-flex").set("align-items", "center");
        return item;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private VerticalLayout cardMetrica(String label, String valor, String cor) {
        Span v = new Span(valor);
        v.getStyle().set("font-size", "22px").set("font-weight", "700").set("color", cor);
        Span l = new Span(label);
        l.getStyle().set("font-size", "11px").set("color", "#888").set("text-transform", "uppercase");
        VerticalLayout card = new VerticalLayout(v, l);
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.setSpacing(false);
        card.setPadding(true);
        card.getStyle()
                .set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)").set("flex", "1")
                .set("min-width", "140px");
        return card;
    }
}
