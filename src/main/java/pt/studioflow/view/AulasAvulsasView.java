package pt.studioflow.view;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.CompraCredito;
import pt.studioflow.model.EstadoPagamentoCredito;
import pt.studioflow.model.MetodoRegistoPresenca;
import pt.studioflow.model.Presenca;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.CompraCreditoRepository;
import pt.studioflow.repository.PresencaRepository;
import pt.studioflow.util.DataUtil;

/**
 * Histórico de aulas avulsas / packs do estúdio: as aulas utilizadas (presenças
 * que consumiram crédito) e as compras de packs/aulas avulsas.
 */
@Route(value = "aulas-avulsas", layout = MainLayout.class)
@PageTitle("Aulas Avulsas | CoreoFlow")
@RolesAllowed("ADMIN")
public class AulasAvulsasView extends VerticalLayout {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final CompraCreditoRepository compraRepo;
    private final PresencaRepository presencaRepo;
    private final Studio studio;

    private final ComboBox<String> periodo = new ComboBox<>("Período");
    private final TextField pesquisa = new TextField("Pesquisar");
    private final HorizontalLayout cards = new HorizontalLayout();
    private final Tab tabUtilizadas = new Tab(VaadinIcon.CHECK_SQUARE_O.create(), new Span("Aulas utilizadas"));
    private final Tab tabCompras = new Tab(VaadinIcon.CART.create(), new Span("Compras"));
    private final Tabs tabs = new Tabs(tabUtilizadas, tabCompras);
    private final Grid<Presenca> gridUtilizadas = new Grid<>();
    private final Grid<CompraCredito> gridCompras = new Grid<>();

    private List<Presenca> utilizadas = List.of();
    private List<CompraCredito> compras = List.of();

    public AulasAvulsasView(CompraCreditoRepository compraRepo, PresencaRepository presencaRepo) {
        this.compraRepo = compraRepo;
        this.presencaRepo = presencaRepo;
        this.studio = TenantContext.getCurrentStudio();

        setSizeFull();
        setPadding(false);
        addClassName("cf-page");

        H2 titulo = new H2("Aulas Avulsas");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        cards.setWidthFull();
        cards.getStyle().set("flex-wrap", "wrap").set("gap", "12px");
        add(cards);

        periodo.setItems("Últimos 30 dias", "Últimos 90 dias", "Últimos 12 meses", "Tudo");
        periodo.setValue("Últimos 90 dias");
        periodo.setWidth("170px");
        pesquisa.setPlaceholder("aluno, turma ou pack…");
        pesquisa.setClearButtonVisible(true);
        pesquisa.setValueChangeMode(ValueChangeMode.LAZY);
        pesquisa.setWidth("260px");
        Button atualizar = new Button("Atualizar", VaadinIcon.REFRESH.create(), e -> recarregar());
        atualizar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        periodo.addValueChangeListener(e -> recarregar());
        pesquisa.addValueChangeListener(e -> filtrar());

        HorizontalLayout filtros = new HorizontalLayout(periodo, pesquisa, atualizar);
        filtros.setAlignItems(FlexComponent.Alignment.BASELINE);
        filtros.getStyle().set("flex-wrap", "wrap");
        add(filtros, tabs);

        configurarGridUtilizadas();
        configurarGridCompras();
        add(gridUtilizadas, gridCompras);
        expand(gridUtilizadas, gridCompras);

        tabs.addSelectedChangeListener(e -> mostrarTab());
        recarregar();
        mostrarTab();
    }

    private void configurarGridUtilizadas() {
        gridUtilizadas.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        gridUtilizadas.setSizeFull();
        gridUtilizadas.setEmptyStateText("Sem aulas avulsas utilizadas neste período.");
        gridUtilizadas.addColumn(p -> DataUtil.formatar(p.getData())
                        + (p.getHoraRegisto() != null ? "  " + p.getHoraRegisto().format(HORA) : ""))
                .setHeader("Data").setAutoWidth(true).setFlexGrow(0)
                .setSortable(true).setComparator(p -> p.getData());
        gridUtilizadas.addColumn(p -> p.getAluno().getNomeCompleto()).setHeader("Aluno").setFlexGrow(1)
                .setSortable(true);
        gridUtilizadas.addColumn(p -> p.getTurma().getDescricao()).setHeader("Turma").setFlexGrow(1)
                .setSortable(true);
        gridUtilizadas.addColumn(p -> p.getCompraCredito().getNomePack()).setHeader("Pack").setAutoWidth(true);
        gridUtilizadas.addColumn(p -> metodo(p.getMetodoRegisto())).setHeader("Registado por").setAutoWidth(true);
        gridUtilizadas.addColumn(p -> {
            CompraCredito c = p.getCompraCredito();
            return valor(c.getCreditosRestantes()) + " de " + valor(c.getNumAulasComprado());
        }).setHeader("Créditos restantes").setAutoWidth(true).setFlexGrow(0);
    }

    private void configurarGridCompras() {
        gridCompras.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        gridCompras.setSizeFull();
        gridCompras.setEmptyStateText("Sem compras neste período.");
        gridCompras.addColumn(c -> c.getDataCompra() != null ? DataUtil.formatar(c.getDataCompra()) : "")
                .setHeader("Data").setAutoWidth(true).setFlexGrow(0)
                .setSortable(true).setComparator(CompraCredito::getDataCompra);
        gridCompras.addColumn(c -> c.getAluno().getNomeCompleto()).setHeader("Aluno").setFlexGrow(1).setSortable(true);
        gridCompras.addColumn(CompraCredito::getNomePack).setHeader("Pack").setFlexGrow(1);
        gridCompras.addColumn(c -> c.getModalidade() != null ? c.getModalidade().getDescricao() : "Todas")
                .setHeader("Modalidade").setAutoWidth(true);
        gridCompras.addColumn(c -> (valor(c.getNumAulasComprado()) - valor(c.getCreditosRestantes()))
                        + " / " + valor(c.getNumAulasComprado()))
                .setHeader("Usadas").setAutoWidth(true).setFlexGrow(0);
        gridCompras.addColumn(c -> c.getValidadeAte() != null ? DataUtil.formatar(c.getValidadeAte()) : "Sem validade")
                .setHeader("Validade").setAutoWidth(true).setFlexGrow(0);
        gridCompras.addColumn(c -> euros(c.getPrecoPago())).setHeader("Preço").setAutoWidth(true).setFlexGrow(0);
        gridCompras.addComponentColumn(this::estadoCompra).setHeader("Pagamento").setAutoWidth(true).setFlexGrow(0);
    }

    private Component estadoCompra(CompraCredito c) {
        EstadoPagamentoCredito e = c.getEstadoPagamento();
        String label;
        String cor;
        if (e == null) {
            label = "—"; cor = "#95A5A6";
        } else {
            switch (e) {
                case PAGO -> { label = "Pago"; cor = "#27AE60"; }
                case PENDENTE -> { label = "Pendente"; cor = "#F39C12"; }
                case CANCELADO -> { label = "Cancelado"; cor = "#7F8C8D"; }
                case REEMBOLSADO -> { label = "Reembolsado"; cor = "#8E44AD"; }
                default -> { label = e.name(); cor = "#95A5A6"; }
            }
        }
        String metodo = c.getMetodoPagamento() == null ? ""
                : switch (c.getMetodoPagamento()) {
                    case MOLLIE -> " · online";
                    case MANUAL -> " · manual";
                };
        Span s = new Span(label);
        s.getStyle().set("background", cor).set("color", "white").set("padding", "1px 8px")
                .set("border-radius", "10px").set("font-size", "11px").set("font-weight", "700")
                .set("white-space", "nowrap");
        Span m = new Span(metodo);
        m.getStyle().set("font-size", "12px").set("color", "var(--cf-text-tertiary)");
        return new Span(s, m);
    }

    private void recarregar() {
        if (studio == null) {
            utilizadas = List.of();
            compras = List.of();
        } else {
            LocalDate desde = desde();
            utilizadas = presencaRepo.avulsasUtilizadas(studio, desde);
            compras = compraRepo.historicoDoStudio(studio, desde != null ? desde.atStartOfDay() : null);
        }
        filtrar();
        atualizarCards();
    }

    private void filtrar() {
        String t = normalizar(pesquisa.getValue());
        gridUtilizadas.setItems(t.isEmpty() ? utilizadas : utilizadas.stream()
                .filter(p -> contem(t, p.getAluno().getNomeCompleto(), p.getTurma().getDescricao(),
                        p.getCompraCredito().getNomePack()))
                .toList());
        gridCompras.setItems(t.isEmpty() ? compras : compras.stream()
                .filter(c -> contem(t, c.getAluno().getNomeCompleto(), c.getNomePack(),
                        c.getModalidade() != null ? c.getModalidade().getDescricao() : null))
                .toList());
    }

    private void atualizarCards() {
        cards.removeAll();
        List<CompraCredito> pagas = compras.stream()
                .filter(c -> c.getEstadoPagamento() == EstadoPagamentoCredito.PAGO).toList();
        BigDecimal receita = pagas.stream().map(CompraCredito::getPrecoPago)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long alunos = utilizadas.stream().map(p -> p.getAluno().getId()).distinct().count();
        long creditosPorUsar = compras.stream()
                .filter(c -> c.getEstadoPagamento() == EstadoPagamentoCredito.PAGO && c.isValido(LocalDate.now()))
                .mapToLong(c -> valor(c.getCreditosRestantes())).sum();
        cards.add(card("Aulas utilizadas", String.valueOf(utilizadas.size()), "#2E86DE"),
                card("Alunos distintos", String.valueOf(alunos), "#16A085"),
                card("Compras pagas", String.valueOf(pagas.size()), "#27AE60"),
                card("Receita", euros(receita), "#27AE60"),
                card("Créditos por usar", String.valueOf(creditosPorUsar), "#F39C12"));
    }

    private void mostrarTab() {
        boolean u = tabs.getSelectedTab() == tabUtilizadas;
        gridUtilizadas.setVisible(u);
        gridCompras.setVisible(!u);
    }

    private LocalDate desde() {
        return switch (periodo.getValue() == null ? "" : periodo.getValue()) {
            case "Últimos 30 dias" -> LocalDate.now().minusDays(30);
            case "Últimos 90 dias" -> LocalDate.now().minusDays(90);
            case "Últimos 12 meses" -> LocalDate.now().minusMonths(12);
            default -> null;
        };
    }

    private static String metodo(MetodoRegistoPresenca m) {
        if (m == null) return "—";
        return switch (m) {
            case KIOSK_PRESENCA -> "Kiosk";
            case AUTO_ALUNO -> "Aluno (portal)";
            case MANUAL_PROFESSOR -> "Professor";
        };
    }

    private static int valor(Integer i) {
        return i != null ? i : 0;
    }

    private static String euros(BigDecimal v) {
        return v == null ? "—" : String.format(Locale.forLanguageTag("pt-PT"), "%.2f €", v);
    }

    private static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static boolean contem(String termo, String... campos) {
        for (String c : campos) {
            if (c != null && normalizar(c).contains(termo)) return true;
        }
        return false;
    }

    private static VerticalLayout card(String label, String valor, String cor) {
        Span v = new Span(valor);
        v.getStyle().set("font-size", "22px").set("font-weight", "800").set("color", cor);
        Span l = new Span(label);
        l.getStyle().set("font-size", "11px").set("color", "var(--cf-text-tertiary)").set("text-transform", "uppercase");
        VerticalLayout c = new VerticalLayout(v, l);
        c.setAlignItems(FlexComponent.Alignment.CENTER);
        c.setSpacing(false);
        c.setPadding(true);
        c.getStyle().set("background", "var(--cf-surface)").set("border-radius", "12px")
                .set("box-shadow", "var(--cf-shadow-sm)").set("flex", "1").set("min-width", "140px");
        return c;
    }
}
