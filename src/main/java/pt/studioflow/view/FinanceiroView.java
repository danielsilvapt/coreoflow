package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.TipoTransacao;
import pt.studioflow.model.Transacao;
import pt.studioflow.repository.TransacaoRepository;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@PageTitle("Financeiro | CoreoFlow")
@Route(value = "financeiro", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class FinanceiroView extends VerticalLayout {

    private final TransacaoRepository repository;
    private LocalDate mesRef = LocalDate.now().withDayOfMonth(1);

    private final Grid<Transacao> gRec = new Grid<>(Transacao.class, false);
    private final Grid<Transacao> gDes = new Grid<>(Transacao.class, false);

    private final Span labelReceitas = new Span("0.00€");
    private final Span labelDespesas = new Span("0.00€");
    private final Span labelSaldo = new Span("0.00€");
    private final Span tituloMes = new Span();

    private List<Transacao> dadosRec = new ArrayList<>();
    private List<Transacao> dadosDes = new ArrayList<>();

    public FinanceiroView(TransacaoRepository repository) {
        this.repository = repository;
        setSizeFull();
        setSpacing(false);
        setPadding(false);

        injectStyles();

        H2 titulo = new H2("Fluxo de Caixa");
        titulo.getStyle().set("margin-top", "0");
        add(titulo, criarStatsCards(), criarAbasNavegacao());
        atualizar();
    }

    private void injectStyles() {
        String styles = ".fin-card { background: white; padding: 20px; border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); border: 1px solid #eee; min-width: 250px; flex-grow: 1; }"
                +
                ".fin-title { color: #7f8c8d; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 1px; font-weight: 600; }"
                +
                ".fin-value { font-size: 1.6rem; font-weight: 800; display: block; margin-top: 5px; }" +
                ".action-btn { border-radius: 10px; padding: 8px; transition: all 0.2s; border: 1px solid rgba(0,0,0,0.05); box-shadow: 0 2px 4px rgba(0,0,0,0.05); }"
                +
                ".action-btn:hover { transform: translateY(-2px); box-shadow: 0 4px 8px rgba(0,0,0,0.1); }" +
                ".btn-edit { background: #E8F0FE; color: #1967D2; }" +
                ".btn-del { background: #FCE8E6; color: #D93025; }" +
                ".btn-doc { background: #F1F3F4; color: #5F6368; }";

        UI.getCurrent().getElement().executeJs(
                "const style = document.createElement('style'); style.textContent = $0; document.head.appendChild(style);",
                styles);
    }

    private Component criarStatsCards() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.getStyle().set("margin", "20px 0");

        layout.add(
                criarCard("Receitas", labelReceitas, "#1E8E3E", VaadinIcon.ARROW_UP),
                criarCard("Despesas", labelDespesas, "#D93025", VaadinIcon.ARROW_DOWN),
                criarCard("Saldo Mensal", labelSaldo, "#1967D2", VaadinIcon.CALC_BOOK));
        return layout;
    }

    private Div criarCard(String titulo, Span val, String color, VaadinIcon icon) {
        Div card = new Div();
        card.addClassName("fin-card");

        Icon i = icon.create();
        i.setColor(color);
        i.getStyle().set("float", "right");
        Span t = new Span(titulo);
        t.addClassName("fin-title");
        val.addClassName("fin-value");
        val.getStyle().set("color", color);

        card.add(i, t, val);
        return card;
    }

    private Component criarAbasNavegacao() {
        Tab tabRec = new Tab(VaadinIcon.SIGN_IN.create(), new Span("Receitas"));
        Tab tabDes = new Tab(VaadinIcon.SIGN_OUT.create(), new Span("Despesas"));

        Tabs tabs = new Tabs(tabRec, tabDes);
        tabs.setWidthFull();

        VerticalLayout content = new VerticalLayout();
        content.setSizeFull();
        content.setPadding(false);

        configGrid(gRec, true);
        configGrid(gDes, false);

        // Switch de visibilidade
        tabs.addSelectedChangeListener(e -> {
            boolean isRec = tabs.getSelectedTab().equals(tabRec);
            gRec.setVisible(isRec);
            gDes.setVisible(isRec == false);
        });

        gDes.setVisible(false);
        content.add(gRec, gDes);

        VerticalLayout container = new VerticalLayout(tabs, content);
        container.setSizeFull();
        container.setPadding(false);
        expand(container);

        return container;
    }

    private void configGrid(Grid<Transacao> g, boolean isReceita) {
        g.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        g.setHeightFull();

        // AÇÕES
        g.addComponentColumn(t -> {
            Button edit = new Button(VaadinIcon.EDIT.create(),
                    e -> new TransacaoDialog(repository, this::atualizar, t).open());
            edit.addClassNames("action-btn", "btn-edit");

            Button del = new Button(VaadinIcon.TRASH.create(), e -> deletar(t));
            del.addClassNames("action-btn", "btn-del");

            return new HorizontalLayout(edit, del);
        }).setHeader("AÇÕES").setWidth("120px").setFlexGrow(0);

        // DADOS
        Grid.Column<Transacao> cData = g.addColumn(Transacao::getData).setHeader("DATA").setAutoWidth(true)
                .setSortable(true);
        Grid.Column<Transacao> cCat = g.addColumn(Transacao::getCategoria).setHeader("CATEGORIA").setAutoWidth(true)
                .setSortable(true);
        Grid.Column<Transacao> cDesc = g.addColumn(Transacao::getDescricao).setHeader("DESCRIÇÃO").setFlexGrow(1)
                .setSortable(true);

        g.addColumn(t -> String.format("%.2f€", t.getValor()))
                .setHeader("VALOR")
                .setComparator(Transacao::getValor)
                .setAutoWidth(true).setSortable(true);

        // DOC
        g.addComponentColumn(t -> {
            if (t.getLinkDocumento() != null && t.getLinkDocumento().startsWith("http")) {
                Anchor a = new Anchor(t.getLinkDocumento(), "");
                a.setTarget("_blank");
                Button btn = new Button(VaadinIcon.FILE_SEARCH.create());
                btn.addClassNames("action-btn", "btn-doc");
                a.add(btn);
                return a;
            }
            return new Span("-");
        }).setHeader("DOC").setWidth("80px").setFlexGrow(0);

        // FILTROS
        HeaderRow filterRow = g.appendHeaderRow();
        filterRow.getCell(cData).setComponent(criarFiltro(g, isReceita));
        filterRow.getCell(cCat).setComponent(criarFiltro(g, isReceita));
        filterRow.getCell(cDesc).setComponent(criarFiltro(g, isReceita));
    }

    private TextField criarFiltro(Grid<Transacao> g, boolean isReceita) {
        TextField f = new TextField();
        f.setPlaceholder("Filtrar...");
        f.addThemeName("small");
        f.setWidthFull();
        f.setValueChangeMode(ValueChangeMode.EAGER);
        f.addValueChangeListener(e -> {
            HeaderRow row = g.getHeaderRows().get(1);
            String fData = ((TextField) row.getCell(g.getColumns().get(1)).getComponent()).getValue().toLowerCase();
            String fCat = ((TextField) row.getCell(g.getColumns().get(2)).getComponent()).getValue().toLowerCase();
            String fDesc = ((TextField) row.getCell(g.getColumns().get(3)).getComponent()).getValue().toLowerCase();

            List<Transacao> base = isReceita ? dadosRec : dadosDes;
            g.setItems(base.stream()
                    .filter(t -> t.getData() != null && t.getData().toString().contains(fData))
                    .filter(t -> t.getCategoria() != null && t.getCategoria().toLowerCase().contains(fCat))
                    .filter(t -> t.getDescricao() != null && t.getDescricao().toLowerCase().contains(fDesc))
                    .collect(Collectors.toList()));
        });
        return f;
    }

    private void atualizar() {
        String mesNome = mesRef.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt")).toUpperCase();
        tituloMes.setText(mesNome + " " + mesRef.getYear());

        LocalDate fim = mesRef.plusMonths(1).minusDays(1);
        dadosRec = repository.findByTipoAndDataBetween(TipoTransacao.RECEITA, mesRef, fim);
        dadosDes = repository.findByTipoAndDataBetween(TipoTransacao.DESPESA, mesRef, fim);

        gRec.setItems(dadosRec);
        gDes.setItems(dadosDes);

        double totalRec = dadosRec.stream().mapToDouble(Transacao::getValor).sum();
        double totalDes = dadosDes.stream().mapToDouble(Transacao::getValor).sum();
        double saldo = totalRec - totalDes;

        labelReceitas.setText(String.format("%.2f€", totalRec));
        labelDespesas.setText(String.format("%.2f€", totalDes));
        labelSaldo.setText(String.format("%.2f€", saldo));
        labelSaldo.getStyle().set("color", saldo >= 0 ? "#1E8E3E" : "#D93025");
    }

    private void deletar(Transacao t) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Confirmar Eliminação");
        d.add(new Span("Deseja apagar este lançamento?"));
        Button confirm = new Button("Eliminar", e -> {
            repository.delete(t);
            atualizar();
            d.close();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        d.getFooter().add(new Button("Cancelar", e -> d.close()), confirm);
        d.open();
    }
}