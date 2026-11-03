package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.service.ExploradorBdService;
import pt.studioflow.service.ExploradorBdService.Coluna;
import pt.studioflow.service.ExploradorBdService.Filtro;
import pt.studioflow.service.ExploradorBdService.Operador;
import pt.studioflow.service.ExploradorBdService.Ordem;
import pt.studioflow.service.ExploradorBdService.Referencia;
import pt.studioflow.service.ExploradorBdService.Tabela;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Explorador gráfico da base de dados (só leitura) para o superadmin:
 * escolher tabela, pesquisar, filtrar por coluna, ordenar, seguir chaves
 * estrangeiras, ver o registo completo e exportar para CSV — sem escrever SQL.
 */
@Route(value = "admin/base-dados", layout = MainLayout.class)
@PageTitle("Base de Dados | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class ExploradorBdView extends VerticalLayout {

    private static final int MAX_EXPORTACAO = 50_000;
    private static final DateTimeFormatter FMT_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExploradorBdService bd;
    private final StudioRepository studioRepository;

    // --- painel das tabelas
    private final TextField filtroTabelas = new TextField();
    private final Grid<Tabela> gridTabelas = new Grid<>();
    private List<Tabela> todasTabelas = List.of();

    // --- painel dos dados
    private final VerticalLayout painelDados = new VerticalLayout();
    private final H3 tituloTabela = new H3();
    private final Span contador = new Span();
    private final Button voltar = new Button(VaadinIcon.ARROW_LEFT.create());
    private final TextField pesquisa = new TextField();
    private final ComboBox<Studio> filtroStudio = new ComboBox<>();
    private final HorizontalLayout chips = new HorizontalLayout();
    private final Grid<Map<String, Object>> grid = new Grid<>();
    private final Anchor exportar = new Anchor();
    private final Button estrutura = new Button("Estrutura", VaadinIcon.LIST.create());

    // --- estado
    private String tabela;
    private List<Coluna> colunas = List.of();
    private final List<Filtro> filtros = new ArrayList<>();
    private List<Ordem> ordensAtuais = List.of();
    private final Map<String, Map<String, String>> rotulosFk = new HashMap<>();
    private final Deque<Estado> historico = new ArrayDeque<>();

    private record Estado(String tabela, List<Filtro> filtros, String pesquisa) {}

    public ExploradorBdView(ExploradorBdService bd, StudioRepository studioRepository) {
        this.bd = bd;
        this.studioRepository = studioRepository;
        setSizeFull();
        setPadding(false);
        addClassName("cf-page");

        H2 titulo = new H2("Base de Dados");
        titulo.getStyle().set("margin", "0");
        Span sub = new Span("Consulta só de leitura · campos sensíveis (passwords, tokens) ocultos");
        sub.getStyle().set("font-size", "12px").set("color", "var(--cf-text-tertiary)");
        Button recarregar = new Button("Atualizar esquema", VaadinIcon.REFRESH.create(), e -> {
            bd.limparCache();
            carregarTabelas();
            if (tabela != null) {
                abrir(tabela, new ArrayList<>(filtros), pesquisa.getValue(), false);
            }
        });
        recarregar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        VerticalLayout tituloBox = new VerticalLayout(titulo, sub);
        tituloBox.setPadding(false);
        tituloBox.setSpacing(false);
        HorizontalLayout topo = new HorizontalLayout(tituloBox, recarregar);
        topo.setWidthFull();
        topo.setAlignItems(FlexComponent.Alignment.CENTER);
        topo.expand(tituloBox);
        add(topo);

        SplitLayout split = new SplitLayout(construirPainelTabelas(), construirPainelDados());
        split.setSizeFull();
        split.setSplitterPosition(20);
        add(split);
        expand(split);

        carregarTabelas();
        mostrarVazio();
    }

    // ================================================================ tabelas

    private Component construirPainelTabelas() {
        filtroTabelas.setPlaceholder("Procurar tabela…");
        filtroTabelas.setPrefixComponent(VaadinIcon.SEARCH.create());
        filtroTabelas.setClearButtonVisible(true);
        filtroTabelas.setValueChangeMode(ValueChangeMode.EAGER);
        filtroTabelas.setWidthFull();
        filtroTabelas.addValueChangeListener(e -> aplicarFiltroTabelas());

        gridTabelas.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        gridTabelas.setSizeFull();
        gridTabelas.addComponentColumn(t -> {
            Span n = new Span(t.nome());
            n.getStyle().set("font-family", "var(--lumo-font-family)").set("font-size", "13px");
            if (t.comentario() != null && !t.comentario().isBlank()) {
                n.setTitle(t.comentario());
            }
            return n;
        }).setHeader("Tabela").setFlexGrow(1);
        gridTabelas.addColumn(t -> "~" + formatarNumero(t.linhasAprox()))
                .setHeader("Linhas").setTextAlign(ColumnTextAlign.END).setAutoWidth(true).setFlexGrow(0);
        gridTabelas.asSingleSelect().addValueChangeListener(e -> {
            if (e.isFromClient() && e.getValue() != null) {
                abrir(e.getValue().nome(), new ArrayList<>(), "", true);
            }
        });

        VerticalLayout v = new VerticalLayout(filtroTabelas, gridTabelas);
        v.setPadding(false);
        v.setSpacing(true);
        v.setSizeFull();
        v.getStyle().set("padding-right", "8px");
        v.expand(gridTabelas);
        return v;
    }

    private void carregarTabelas() {
        todasTabelas = bd.tabelas();
        aplicarFiltroTabelas();
    }

    private void aplicarFiltroTabelas() {
        String f = Objects.toString(filtroTabelas.getValue(), "").trim().toLowerCase(Locale.ROOT);
        gridTabelas.setItems(todasTabelas.stream()
                .filter(t -> f.isEmpty() || t.nome().toLowerCase(Locale.ROOT).contains(f))
                .toList());
        selecionarNaLista();
    }

    private void selecionarNaLista() {
        todasTabelas.stream().filter(t -> t.nome().equals(tabela)).findFirst()
                .ifPresentOrElse(gridTabelas::select, gridTabelas::deselectAll);
    }

    // ================================================================ dados

    private Component construirPainelDados() {
        voltar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        voltar.setTooltipText("Voltar à vista anterior");
        voltar.addClickListener(e -> {
            Estado anterior = historico.pollFirst();
            if (anterior != null) {
                abrir(anterior.tabela(), new ArrayList<>(anterior.filtros()), anterior.pesquisa(), false);
            }
        });
        tituloTabela.getStyle().set("margin", "0").set("font-family", "monospace");
        contador.getStyle().set("font-size", "12px").set("color", "var(--cf-text-tertiary)")
                .set("background", "var(--cf-surface-subtle)").set("padding", "2px 10px").set("border-radius", "10px");

        estrutura.addClickListener(e -> abrirEstrutura());
        estrutura.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button btnExportar = new Button("Exportar CSV", VaadinIcon.DOWNLOAD.create());
        btnExportar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        exportar.add(btnExportar);
        exportar.getElement().setAttribute("download", true);

        HorizontalLayout cabecalho = new HorizontalLayout(voltar, tituloTabela, contador);
        cabecalho.setAlignItems(FlexComponent.Alignment.CENTER);
        HorizontalLayout acoes = new HorizontalLayout(estrutura, exportar);
        HorizontalLayout linha1 = new HorizontalLayout(cabecalho, acoes);
        linha1.setWidthFull();
        linha1.setAlignItems(FlexComponent.Alignment.CENTER);
        linha1.expand(cabecalho);

        pesquisa.setPlaceholder("Pesquisar em todas as colunas de texto…");
        pesquisa.setPrefixComponent(VaadinIcon.SEARCH.create());
        pesquisa.setClearButtonVisible(true);
        pesquisa.setValueChangeMode(ValueChangeMode.LAZY);
        pesquisa.setValueChangeTimeout(400);
        pesquisa.setWidth("340px");
        pesquisa.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                atualizarDados();
            }
        });

        filtroStudio.setPlaceholder("Estúdio (todos)");
        filtroStudio.setItemLabelGenerator(s -> s.getNome() + " (#" + s.getId() + ")");
        filtroStudio.setClearButtonVisible(true);
        filtroStudio.setWidth("240px");
        filtroStudio.addValueChangeListener(e -> {
            if (!e.isFromClient()) {
                return;
            }
            filtros.removeIf(f -> f.coluna().equals("studio_id") && f.operador() == Operador.IGUAL);
            if (e.getValue() != null) {
                filtros.add(new Filtro("studio_id", Operador.IGUAL, String.valueOf(e.getValue().getId())));
            }
            atualizarDados();
        });

        Button addFiltro = new Button("Filtro", VaadinIcon.FILTER.create(), e -> abrirNovoFiltro());
        addFiltro.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        HorizontalLayout linha2 = new HorizontalLayout(pesquisa, filtroStudio, addFiltro);
        linha2.setAlignItems(FlexComponent.Alignment.CENTER);
        linha2.getStyle().set("flex-wrap", "wrap");

        chips.getStyle().set("flex-wrap", "wrap").set("gap", "6px");
        chips.setSpacing(false);

        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS);
        grid.setSizeFull();
        grid.setMultiSort(true, Grid.MultiSortPriority.APPEND);
        grid.addItemClickListener(e -> {
            if (e.getClickCount() >= 1 && e.getItem() != null) {
                abrirRegisto(e.getItem());
            }
        });

        painelDados.add(linha1, linha2, chips, grid);
        painelDados.setPadding(false);
        painelDados.getStyle().set("padding-left", "12px");
        painelDados.setSizeFull();
        painelDados.expand(grid);
        return painelDados;
    }

    private void mostrarVazio() {
        tituloTabela.setText("Escolha uma tabela à esquerda");
        tituloTabela.getStyle().set("font-family", "var(--lumo-font-family)");
        contador.setVisible(false);
        voltar.setVisible(false);
        painelDados.getChildren().skip(1).forEach(c -> c.setVisible(false));
        exportar.setVisible(false);
        estrutura.setVisible(false);
    }

    /**
     * Abre uma tabela com um conjunto de filtros.
     *
     * @param guardar se {@code true}, a vista atual fica no histórico (botão voltar)
     */
    private void abrir(String novaTabela, List<Filtro> novosFiltros, String novaPesquisa, boolean guardar) {
        List<Coluna> novasColunas;
        try {
            novasColunas = bd.colunas(novaTabela);
        } catch (RuntimeException ex) {
            erro("Não foi possível abrir a tabela: " + ex.getMessage());
            return;
        }
        if (guardar && tabela != null) {
            historico.push(new Estado(tabela, List.copyOf(filtros), pesquisa.getValue()));
            while (historico.size() > 30) {
                historico.removeLast();
            }
        }
        tabela = novaTabela;
        colunas = novasColunas;
        filtros.clear();
        filtros.addAll(novosFiltros);
        rotulosFk.clear();
        pesquisa.setValue(novaPesquisa == null ? "" : novaPesquisa);

        tituloTabela.setText(tabela);
        tituloTabela.getStyle().set("font-family", "monospace");
        contador.setVisible(true);
        voltar.setVisible(!historico.isEmpty());
        painelDados.getChildren().forEach(c -> c.setVisible(true));
        exportar.setVisible(true);
        estrutura.setVisible(true);

        boolean temStudio = colunas.stream().anyMatch(c -> c.nome().equals("studio_id"));
        filtroStudio.setVisible(temStudio);
        if (temStudio) {
            List<Studio> studios = studioRepository.findAll().stream()
                    .sorted(Comparator.comparing(s -> Objects.toString(s.getNome(), ""))).toList();
            filtroStudio.setItems(studios);
            String sel = filtros.stream()
                    .filter(f -> f.coluna().equals("studio_id") && f.operador() == Operador.IGUAL)
                    .map(Filtro::valor).findFirst().orElse(null);
            filtroStudio.setValue(studios.stream().filter(s -> String.valueOf(s.getId()).equals(sel)).findFirst().orElse(null));
        }

        construirColunas();
        selecionarNaLista();
        atualizarDados();
    }

    private void construirColunas() {
        grid.removeAllColumns();
        for (Coluna c : colunas) {
            Span header = new Span(c.nome());
            if (c.chavePrimaria()) {
                header.add(" 🔑");
            } else if (c.estrangeira()) {
                header.add(" ↗");
            }
            header.setTitle(c.tipoCompleto() + (c.nulo() ? " · pode ser nulo" : "")
                    + (c.estrangeira() ? " · → " + c.fkTabela() + "." + c.fkColuna() : ""));

            Grid.Column<Map<String, Object>> col;
            if (c.estrangeira()) {
                col = grid.addComponentColumn(linha -> celulaFk(c, linha.get(c.nome())));
            } else {
                col = grid.addComponentColumn(linha -> celula(c, linha.get(c.nome())));
            }
            col.setHeader(header).setKey(c.nome()).setSortable(true).setSortProperty(c.nome())
                    .setResizable(true).setFlexGrow(0).setWidth(larguraPara(c));
            if (isNumerico(c)) {
                col.setTextAlign(ColumnTextAlign.END);
            }
        }
    }

    private void atualizarDados() {
        if (tabela == null) {
            return;
        }
        String termo = pesquisa.getValue();
        String t = tabela;
        List<Filtro> fs = List.copyOf(filtros);
        long total;
        try {
            total = bd.contar(t, fs, termo);
        } catch (RuntimeException ex) {
            erro("Erro ao consultar: " + mensagem(ex));
            grid.setItems(List.of());
            return;
        }
        contador.setText(formatarNumero(total) + (total == 1 ? " registo" : " registos"));
        grid.setItems(query -> {
            List<Ordem> ordens = query.getSortOrders().stream()
                    .map(o -> new Ordem(o.getSorted(), o.getDirection() == SortDirection.ASCENDING))
                    .toList();
            ordensAtuais = ordens;
            List<Map<String, Object>> linhas = bd.linhas(t, fs, termo, ordens, query.getOffset(), query.getLimit());
            carregarRotulosFk(linhas);
            return linhas.stream();
        }, query -> (int) Math.min(total, Integer.MAX_VALUE));
        atualizarChips();
        atualizarExportacao();
    }

    /** Para cada coluna FK, vai buscar o "nome" do registo referenciado (ex.: studio_id → nome do estúdio). */
    private void carregarRotulosFk(List<Map<String, Object>> linhas) {
        for (Coluna c : colunas) {
            if (!c.estrangeira()) {
                continue;
            }
            Map<String, String> cache = rotulosFk.computeIfAbsent(c.nome(), k -> new HashMap<>());
            Set<Object> faltam = new HashSet<>();
            for (Map<String, Object> l : linhas) {
                Object v = l.get(c.nome());
                if (v != null && !cache.containsKey(String.valueOf(v))) {
                    faltam.add(v);
                }
            }
            if (faltam.isEmpty()) {
                continue;
            }
            try {
                cache.putAll(bd.rotulos(c.fkTabela(), c.fkColuna(), faltam));
                faltam.forEach(v -> cache.putIfAbsent(String.valueOf(v), null));
            } catch (RuntimeException ignored) {
                // rótulos são só ajuda visual
            }
        }
    }

    // ================================================================ células

    private Component celula(Coluna c, Object v) {
        Span s = new Span();
        if (v == null) {
            s.setText("NULL");
            s.getStyle().set("color", "var(--cf-text-tertiary)").set("font-style", "italic").set("font-size", "11px");
            return s;
        }
        String texto = formatar(v);
        if (texto.length() > 120) {
            s.setTitle(texto.length() > 2000 ? texto.substring(0, 2000) + "…" : texto);
            texto = texto.substring(0, 120) + "…";
        }
        s.setText(texto.replace('\n', ' '));
        if (ExploradorBdService.MASCARA.equals(v)) {
            s.getStyle().set("color", "var(--cf-text-tertiary)");
        } else if (v instanceof Boolean b) {
            s.getStyle().set("color", b ? "#27AE60" : "#C0392B").set("font-weight", "600");
        }
        return s;
    }

    private Component celulaFk(Coluna c, Object v) {
        if (v == null) {
            return celula(c, null);
        }
        String rotulo = rotulosFk.getOrDefault(c.nome(), Map.of()).get(String.valueOf(v));
        Button b = new Button(v + (rotulo != null ? " · " + rotulo : ""));
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        b.setTooltipText("Abrir " + c.fkTabela() + " onde " + c.fkColuna() + " = " + v);
        b.getElement().addEventListener("click", ev -> { }).addEventData("event.stopPropagation()");
        b.addClickListener(e -> abrir(c.fkTabela(),
                new ArrayList<>(List.of(new Filtro(c.fkColuna(), Operador.IGUAL, String.valueOf(v)))), "", true));
        return b;
    }

    // ================================================================ filtros

    private void atualizarChips() {
        chips.removeAll();
        for (Filtro f : List.copyOf(filtros)) {
            Span chip = new Span(f.descricao());
            Button x = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> {
                filtros.remove(f);
                if (f.coluna().equals("studio_id")) {
                    filtroStudio.clear();
                }
                atualizarDados();
            });
            x.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
            HorizontalLayout h = new HorizontalLayout(chip, x);
            h.setSpacing(false);
            h.setAlignItems(FlexComponent.Alignment.CENTER);
            h.getStyle().set("background", "var(--lumo-primary-color-10pct)").set("color", "var(--lumo-primary-text-color)")
                    .set("border-radius", "14px").set("padding", "0 4px 0 12px").set("font-size", "12px");
            chips.add(h);
        }
        if (filtros.size() > 1) {
            Button limpar = new Button("Limpar filtros", e -> {
                filtros.clear();
                filtroStudio.clear();
                atualizarDados();
            });
            limpar.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
            chips.add(limpar);
        }
        chips.setVisible(!filtros.isEmpty());
    }

    private void abrirNovoFiltro() {
        if (tabela == null) {
            return;
        }
        Dialog d = new Dialog();
        d.setHeaderTitle("Adicionar filtro");

        ComboBox<Coluna> coluna = new ComboBox<>("Coluna");
        coluna.setItems(colunas.stream().filter(c -> !c.sensivel()).toList());
        coluna.setItemLabelGenerator(c -> c.nome() + "  (" + c.tipo() + ")");
        coluna.setWidthFull();

        ComboBox<Operador> operador = new ComboBox<>("Condição");
        operador.setItems(Operador.values());
        operador.setItemLabelGenerator(Operador::getLabel);
        operador.setValue(Operador.IGUAL);
        operador.setWidthFull();

        TextField valor = new TextField("Valor");
        valor.setWidthFull();

        coluna.addValueChangeListener(e -> {
            Coluna c = e.getValue();
            if (c == null) {
                return;
            }
            valor.setPlaceholder(dicaValor(c));
            operador.setValue(c.texto() ? Operador.CONTEM : Operador.IGUAL);
        });
        operador.addValueChangeListener(e -> valor.setEnabled(e.getValue() == null || e.getValue().precisaValor()));

        Button ok = new Button("Aplicar", e -> {
            if (coluna.getValue() == null || operador.getValue() == null) {
                coluna.setInvalid(coluna.getValue() == null);
                return;
            }
            String v = valor.getValue() == null ? "" : valor.getValue().trim();
            if (operador.getValue().precisaValor() && v.isEmpty()) {
                valor.setInvalid(true);
                valor.setErrorMessage("Indique um valor");
                return;
            }
            filtros.add(new Filtro(coluna.getValue().nome(), operador.getValue(), normalizarValor(coluna.getValue(), v)));
            d.close();
            atualizarDados();
        });
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        ok.addClickShortcut(Key.ENTER);
        Button cancelar = new Button("Cancelar", e -> d.close());

        VerticalLayout corpo = new VerticalLayout(coluna, operador, valor);
        corpo.setPadding(false);
        corpo.setWidth("380px");
        d.add(corpo);
        d.getFooter().add(cancelar, ok);
        d.open();
        coluna.focus();
    }

    private static String dicaValor(Coluna c) {
        return switch (c.tipo()) {
            case "date" -> "dd/mm/aaaa ou aaaa-mm-dd";
            case "datetime", "timestamp" -> "aaaa-mm-dd ou aaaa-mm-dd hh:mm";
            case "bit", "tinyint" -> c.tipoCompleto().startsWith("bit") || c.tipoCompleto().equals("tinyint(1)") ? "1 = sim, 0 = não" : "número";
            default -> isNumerico(c) ? "número" : "texto";
        };
    }

    /** Aceita datas em formato português e converte para o formato do MySQL. */
    private static String normalizarValor(Coluna c, String v) {
        if (List.of("date", "datetime", "timestamp").contains(c.tipo())) {
            var m = java.util.regex.Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})(.*)$").matcher(v);
            if (m.matches()) {
                return String.format("%s-%02d-%02d%s", m.group(3), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(1)), m.group(4));
            }
        }
        if (c.tipo().equals("bit") || c.tipoCompleto().equals("tinyint(1)")) {
            String l = v.toLowerCase(Locale.ROOT);
            if (l.equals("sim") || l.equals("true")) return "1";
            if (l.equals("não") || l.equals("nao") || l.equals("false")) return "0";
        }
        return v;
    }

    // ================================================================ registo

    private void abrirRegisto(Map<String, Object> linha) {
        Dialog d = new Dialog();
        d.setWidth("760px");
        d.setMaxWidth("95vw");
        Coluna pk = colunas.stream().filter(Coluna::chavePrimaria).findFirst().orElse(null);
        Object valorPk = pk != null ? linha.get(pk.nome()) : null;
        d.setHeaderTitle(tabela + (valorPk != null ? " #" + valorPk : ""));

        Div campos = new Div();
        campos.getStyle().set("display", "grid").set("grid-template-columns", "minmax(140px, 220px) 1fr")
                .set("gap", "6px 16px").set("align-items", "start");
        for (Coluna c : colunas) {
            Span nome = new Span(c.nome());
            nome.setTitle(c.tipoCompleto());
            nome.getStyle().set("font-family", "monospace").set("font-size", "12px")
                    .set("color", "var(--cf-text-tertiary)").set("padding-top", "2px");
            Object v = linha.get(c.nome());
            Component valor;
            if (c.estrangeira() && v != null) {
                valor = celulaFkDialogo(c, v, d);
            } else {
                Span s = new Span(v == null ? "NULL" : formatar(v));
                s.getStyle().set("white-space", "pre-wrap").set("word-break", "break-word").set("font-size", "13px");
                if (v == null) {
                    s.getStyle().set("color", "var(--cf-text-tertiary)").set("font-style", "italic");
                }
                valor = s;
            }
            campos.add(nome, valor);
        }
        d.add(campos);

        if (valorPk != null) {
            List<Referencia> refs;
            try {
                refs = bd.referenciadoPor(tabela).stream()
                        .filter(r -> r.colunaAlvo().equals(pk.nome())).toList();
            } catch (RuntimeException ex) {
                refs = List.of();
            }
            if (!refs.isEmpty()) {
                H4 h = new H4("Registos ligados a este");
                h.getStyle().set("margin", "18px 0 6px");
                Div lista = new Div();
                lista.getStyle().set("display", "flex").set("flex-wrap", "wrap").set("gap", "6px");
                for (Referencia r : refs) {
                    long n;
                    try {
                        n = bd.contar(r.tabela(), List.of(new Filtro(r.coluna(), Operador.IGUAL, String.valueOf(valorPk))), null);
                    } catch (RuntimeException ex) {
                        continue;
                    }
                    if (n == 0) {
                        continue;
                    }
                    Button b = new Button(r.tabela() + " (" + formatarNumero(n) + ")", e -> {
                        d.close();
                        abrir(r.tabela(), new ArrayList<>(List.of(
                                new Filtro(r.coluna(), Operador.IGUAL, String.valueOf(valorPk)))), "", true);
                    });
                    b.setTooltipText(r.tabela() + "." + r.coluna() + " = " + valorPk);
                    b.addThemeVariants(ButtonVariant.LUMO_SMALL);
                    lista.add(b);
                }
                if (lista.getComponentCount() > 0) {
                    d.add(h, lista);
                }
            }
        }

        Button fechar = new Button("Fechar", e -> d.close());
        d.getFooter().add(fechar);
        d.open();
    }

    private Component celulaFkDialogo(Coluna c, Object v, Dialog d) {
        String rotulo = rotulosFk.getOrDefault(c.nome(), Map.of()).get(String.valueOf(v));
        Button b = new Button(v + (rotulo != null ? " · " + rotulo : "") + "  →  " + c.fkTabela());
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        b.addClickListener(e -> {
            d.close();
            abrir(c.fkTabela(), new ArrayList<>(List.of(new Filtro(c.fkColuna(), Operador.IGUAL, String.valueOf(v)))), "", true);
        });
        return b;
    }

    // ================================================================ estrutura

    private void abrirEstrutura() {
        if (tabela == null) {
            return;
        }
        Dialog d = new Dialog();
        d.setHeaderTitle("Estrutura de " + tabela);
        d.setWidth("820px");
        d.setMaxWidth("95vw");
        Grid<Coluna> g = new Grid<>();
        g.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        g.addColumn(Coluna::nome).setHeader("Coluna").setAutoWidth(true);
        g.addColumn(Coluna::tipoCompleto).setHeader("Tipo").setAutoWidth(true);
        g.addColumn(c -> c.nulo() ? "sim" : "não").setHeader("Nulo").setAutoWidth(true);
        g.addColumn(c -> c.chavePrimaria() ? "PK" : (c.estrangeira() ? "FK → " + c.fkTabela() + "." + c.fkColuna() : ""))
                .setHeader("Chave").setAutoWidth(true);
        g.addColumn(c -> c.sensivel() ? "oculto" : "").setHeader("").setAutoWidth(true);
        g.setItems(colunas);
        g.setAllRowsVisible(true);
        d.add(g);

        List<Referencia> refs = bd.referenciadoPor(tabela);
        if (!refs.isEmpty()) {
            H4 h = new H4("Referenciada por");
            h.getStyle().set("margin", "16px 0 6px");
            Span s = new Span(refs.stream().map(r -> r.tabela() + "." + r.coluna())
                    .collect(Collectors.joining(", ")));
            s.getStyle().set("font-family", "monospace").set("font-size", "12px");
            d.add(h, s);
        }
        d.getFooter().add(new Button("Fechar", e -> d.close()));
        d.open();
    }

    // ================================================================ exportar

    private void atualizarExportacao() {
        String t = tabela;
        List<Filtro> fs = List.copyOf(filtros);
        String termo = pesquisa.getValue();
        List<Coluna> cols = colunas;
        String nomeFicheiro = t + "_" + LocalDate.now() + ".csv";
        exportar.setHref(new StreamResource(nomeFicheiro, () -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                w.write('﻿'); // BOM para o Excel abrir acentos corretamente
                w.println(cols.stream().map(c -> csv(c.nome())).collect(Collectors.joining(";")));
                bd.percorrer(t, fs, termo, ordensAtuais, MAX_EXPORTACAO, linha ->
                        w.println(cols.stream().map(c -> csv(linha.get(c.nome()) == null ? "" : formatar(linha.get(c.nome()))))
                                .collect(Collectors.joining(";"))));
            }
            return new java.io.ByteArrayInputStream(out.toByteArray());
        }));
    }

    private static String csv(String s) {
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ================================================================ util

    private static String formatar(Object v) {
        if (v == null) return "";
        if (v instanceof byte[] b) return "[binário, " + b.length + " bytes]";
        if (v instanceof Boolean b) return b ? "Sim" : "Não";
        if (v instanceof LocalDateTime dt) return dt.format(FMT_DATA_HORA);
        if (v instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().format(FMT_DATA_HORA);
        if (v instanceof LocalDate ld) return ld.format(FMT_DATA);
        if (v instanceof java.sql.Date sd) return sd.toLocalDate().format(FMT_DATA);
        if (v instanceof LocalTime lt) return lt.toString();
        if (v instanceof java.math.BigDecimal bd) return bd.toPlainString();
        return v.toString();
    }

    private static boolean isNumerico(Coluna c) {
        return List.of("int", "bigint", "smallint", "mediumint", "tinyint", "decimal", "double", "float").contains(c.tipo())
                && !c.tipoCompleto().equals("tinyint(1)");
    }

    private static String larguraPara(Coluna c) {
        if (c.chavePrimaria() && isNumerico(c)) return "90px";
        if (c.estrangeira()) return "200px";
        return switch (c.tipo()) {
            case "bit", "tinyint" -> "90px";
            case "int", "bigint", "smallint", "decimal", "double", "float" -> "110px";
            case "date" -> "120px";
            case "datetime", "timestamp" -> "170px";
            case "text", "mediumtext", "longtext" -> "300px";
            default -> "180px";
        };
    }

    private static String formatarNumero(long n) {
        return String.format(Locale.forLanguageTag("pt-PT"), "%,d", n);
    }

    private static String mensagem(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage();
    }

    private void erro(String msg) {
        Notification n = Notification.show(msg, 5000, Notification.Position.BOTTOM_CENTER);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
