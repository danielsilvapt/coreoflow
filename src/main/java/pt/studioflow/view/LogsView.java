package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.data.domain.PageRequest;
import pt.studioflow.model.LogEntry;
import pt.studioflow.repository.LogEntryRepository;
import pt.studioflow.util.DataUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Route(value = "admin/logs", layout = MainLayout.class)
@PageTitle("Logs & Erros | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class LogsView extends VerticalLayout {

    private final LogEntryRepository repo;

    private final ComboBox<String> nivel = new ComboBox<>("Nível");
    private final ComboBox<String> estudio = new ComboBox<>("Estúdio");
    private final ComboBox<String> periodo = new ComboBox<>("Período");
    private final TextField pesquisa = new TextField("Pesquisar");
    private final Grid<LogEntry> grid = new Grid<>();
    private final HorizontalLayout cards = new HorizontalLayout();

    public LogsView(LogEntryRepository repo) {
        this.repo = repo;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Logs & Erros");
        titulo.getStyle().set("margin-top", "0");
        Button atualizar = new Button("Atualizar", VaadinIcon.REFRESH.create(), e -> {
            recarregarFiltroEstudios();
            recarregar();
        });
        atualizar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout topo = new HorizontalLayout(titulo, atualizar);
        topo.setWidthFull();
        topo.setAlignItems(FlexComponent.Alignment.CENTER);
        topo.expand(titulo);
        add(topo);

        cards.setWidthFull();
        cards.getStyle().set("flex-wrap", "wrap").set("gap", "12px");
        add(cards);

        nivel.setItems("Todos", "ERROR", "WARN");
        nivel.setValue("Todos");
        nivel.setWidth("140px");
        estudio.setWidth("200px");
        periodo.setItems("Últimas 24h", "Últimos 7 dias", "Últimos 30 dias", "Tudo");
        periodo.setValue("Últimos 7 dias");
        periodo.setWidth("170px");
        pesquisa.setPlaceholder("mensagem ou classe…");
        pesquisa.setClearButtonVisible(true);
        pesquisa.setValueChangeMode(ValueChangeMode.LAZY);
        pesquisa.setWidth("260px");

        nivel.addValueChangeListener(e -> recarregar());
        estudio.addValueChangeListener(e -> recarregar());
        periodo.addValueChangeListener(e -> recarregar());
        pesquisa.addValueChangeListener(e -> recarregar());

        HorizontalLayout filtros = new HorizontalLayout(nivel, estudio, periodo, pesquisa);
        filtros.setAlignItems(FlexComponent.Alignment.END);
        filtros.getStyle().set("flex-wrap", "wrap");
        add(filtros);

        configurarGrid();
        add(grid);
        expand(grid);

        recarregarFiltroEstudios();
        recarregar();
    }

    private void configurarGrid() {
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(l -> l.getData() != null ? DataUtil.formatar(l.getData()) : "")
                .setHeader("Data").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(l -> {
            Span s = new Span(l.getNivel());
            boolean erro = "ERROR".equals(l.getNivel());
            s.getStyle().set("background", erro ? "#E74C3C" : "#F39C12").set("color", "white")
                    .set("padding", "1px 8px").set("border-radius", "10px")
                    .set("font-size", "11px").set("font-weight", "700");
            return s;
        }).setHeader("Nível").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(LogEntry::getStudioSlug).setHeader("Estúdio").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(LogEntry::getLoggerCurto).setHeader("Origem").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(LogEntry::getMensagem).setHeader("Mensagem").setFlexGrow(1);

        grid.setItemDetailsRenderer(new ComponentRenderer<>(l -> {
            VerticalLayout d = new VerticalLayout();
            d.setPadding(false);
            Span meta = new Span(l.getLogger() + "  ·  thread " + l.getThread());
            meta.getStyle().set("font-size", "12px").set("color", "#888");
            d.add(meta);
            Pre msg = new Pre(l.getMensagem() != null ? l.getMensagem() : "");
            msg.getStyle().set("white-space", "pre-wrap").set("margin", "6px 0")
                    .set("font-size", "12px").set("background", "#f7f7f9")
                    .set("padding", "8px").set("border-radius", "6px");
            d.add(msg);
            if (l.getStacktrace() != null && !l.getStacktrace().isBlank()) {
                Pre st = new Pre(l.getStacktrace());
                st.getStyle().set("white-space", "pre").set("overflow", "auto")
                        .set("max-height", "320px").set("font-size", "11px")
                        .set("background", "#2d2d2d").set("color", "#eee")
                        .set("padding", "10px").set("border-radius", "6px");
                d.add(st);
            }
            return d;
        }));
    }

    private void recarregarFiltroEstudios() {
        List<String> opcoes = new ArrayList<>();
        opcoes.add("Todos");
        opcoes.addAll(repo.studiosComLogs());
        estudio.setItems(opcoes);
        if (estudio.getValue() == null) {
            estudio.setValue("Todos");
        }
    }

    private LocalDateTime desde() {
        return switch (periodo.getValue() == null ? "" : periodo.getValue()) {
            case "Últimas 24h" -> LocalDateTime.now().minusHours(24);
            case "Últimos 7 dias" -> LocalDateTime.now().minusDays(7);
            case "Últimos 30 dias" -> LocalDateTime.now().minusDays(30);
            default -> null;
        };
    }

    private void recarregar() {
        String n = "Todos".equals(nivel.getValue()) ? null : nivel.getValue();
        String s = (estudio.getValue() == null || "Todos".equals(estudio.getValue())) ? null : estudio.getValue();
        String t = pesquisa.getValue() != null && !pesquisa.getValue().isBlank() ? pesquisa.getValue().trim() : null;

        List<LogEntry> res = repo.pesquisar(n, s, t, desde(), PageRequest.of(0, 500));
        grid.setItems(res);

        cards.removeAll();
        LocalDateTime h24 = LocalDateTime.now().minusHours(24);
        LocalDateTime d7 = LocalDateTime.now().minusDays(7);
        cards.add(
                card("Erros (24h)", repo.countByNivelAndDataAfter("ERROR", h24), "#E74C3C"),
                card("Avisos (24h)", repo.countByNivelAndDataAfter("WARN", h24), "#F39C12"),
                card("Erros (7 dias)", repo.countByNivelAndDataAfter("ERROR", d7), "#C0392B"),
                card("Resultados", (long) res.size(), "#34495E"));
    }

    private VerticalLayout card(String label, long valor, String cor) {
        Span v = new Span(String.valueOf(valor));
        v.getStyle().set("font-size", "24px").set("font-weight", "800").set("color", cor);
        Span l = new Span(label);
        l.getStyle().set("font-size", "11px").set("color", "#888").set("text-transform", "uppercase");
        VerticalLayout c = new VerticalLayout(v, l);
        c.setAlignItems(FlexComponent.Alignment.CENTER);
        c.setSpacing(false);
        c.setPadding(true);
        c.getStyle().set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.07)").set("flex", "1").set("min-width", "150px");
        return c;
    }
}
