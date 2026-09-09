package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.PedidoSuporte;
import pt.studioflow.repository.PedidoSuporteRepository;
import pt.studioflow.service.SuporteService;
import pt.studioflow.util.DataUtil;

import java.util.ArrayList;
import java.util.List;

@Route(value = "admin/suporte", layout = MainLayout.class)
@PageTitle("Suporte | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class SuporteView extends VerticalLayout {

    private final SuporteService suporteService;
    private final PedidoSuporteRepository repo;

    private final HorizontalLayout cards = new HorizontalLayout();
    private final ComboBox<String> estado = new ComboBox<>("Estado");
    private final ComboBox<String> estudio = new ComboBox<>("Estúdio");
    private final TextField pesquisa = new TextField("Pesquisar");
    private final Grid<PedidoSuporte> grid = new Grid<>();

    public SuporteView(SuporteService suporteService, PedidoSuporteRepository repo) {
        this.suporteService = suporteService;
        this.repo = repo;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Suporte");
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

        estado.setItems("Todos", "Por tratar", "Respondidos", "Resolvidos");
        estado.setValue("Por tratar");
        estado.setWidth("170px");
        estudio.setWidth("200px");
        pesquisa.setPlaceholder("assunto, utilizador…");
        pesquisa.setClearButtonVisible(true);
        pesquisa.setValueChangeMode(ValueChangeMode.LAZY);
        pesquisa.setWidth("260px");
        estado.addValueChangeListener(e -> recarregar());
        estudio.addValueChangeListener(e -> recarregar());
        pesquisa.addValueChangeListener(e -> recarregar());
        HorizontalLayout filtros = new HorizontalLayout(estado, estudio, pesquisa);
        filtros.setAlignItems(FlexComponent.Alignment.END);
        filtros.getStyle().set("flex-wrap", "wrap");
        add(filtros);

        configurarGrid();
        add(grid);
        expand(grid);

        recarregarFiltroEstudios();
        recarregar();
    }

    private void recarregarFiltroEstudios() {
        String atual = estudio.getValue();
        List<String> ops = new ArrayList<>();
        ops.add("Todos");
        ops.addAll(repo.estudiosComPedidos());
        estudio.setItems(ops);
        estudio.setValue(atual != null && ops.contains(atual) ? atual : "Todos");
    }

    private void configurarGrid() {
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(p -> p.getDataCriacao() != null ? DataUtil.formatar(p.getDataCriacao()) : "")
                .setHeader("Data").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(PedidoSuporte::getStudio).setHeader("Estúdio").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(PedidoSuporte::getUtilizador).setHeader("Utilizador").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(PedidoSuporte::getTipo).setHeader("Tipo").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(PedidoSuporte::getAssunto).setHeader("Assunto").setFlexGrow(1);
        grid.addComponentColumn(p -> {
            String txt;
            String cor;
            if (p.isResolvido()) {
                txt = "Resolvido";
                cor = "#27AE60";
            } else if (p.isRespondido()) {
                txt = "Respondido";
                cor = "#3498DB";
            } else {
                txt = "Por tratar";
                cor = "#E67E22";
            }
            Span s = new Span(txt);
            s.getStyle().set("background", cor).set("color", "white").set("padding", "1px 9px")
                    .set("border-radius", "10px").set("font-size", "11px").set("font-weight", "700");
            return s;
        }).setHeader("Estado").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(p -> {
            Button abrir = new Button("Ver / responder", VaadinIcon.COMMENT_ELLIPSIS.create(),
                    e -> abrirDetalhe(p));
            abrir.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return abrir;
        }).setHeader("").setAutoWidth(true).setFlexGrow(0);

        grid.addItemDoubleClickListener(e -> abrirDetalhe(e.getItem()));
    }

    private void recarregar() {
        List<PedidoSuporte> todos = repo.findAllByOrderByDataCriacaoDesc();

        String est = estado.getValue();
        String stu = (estudio.getValue() == null || "Todos".equals(estudio.getValue())) ? null : estudio.getValue();
        String q = pesquisa.getValue() != null ? pesquisa.getValue().trim().toLowerCase() : "";

        List<PedidoSuporte> filtrados = todos.stream()
                .filter(p -> switch (est == null ? "Todos" : est) {
                    case "Por tratar" -> !p.isResolvido();
                    case "Respondidos" -> p.isRespondido();
                    case "Resolvidos" -> p.isResolvido();
                    default -> true;
                })
                .filter(p -> stu == null || stu.equals(p.getStudio()))
                .filter(p -> q.isEmpty()
                        || (p.getAssunto() != null && p.getAssunto().toLowerCase().contains(q))
                        || (p.getUtilizador() != null && p.getUtilizador().toLowerCase().contains(q))
                        || (p.getDescricao() != null && p.getDescricao().toLowerCase().contains(q)))
                .toList();
        grid.setItems(filtrados);

        long porTratar = todos.stream().filter(p -> !p.isResolvido()).count();
        long respondidos = todos.stream().filter(PedidoSuporte::isRespondido).count();
        long resolvidos = todos.stream().filter(PedidoSuporte::isResolvido).count();
        cards.removeAll();
        cards.add(
                card("Por tratar", porTratar, "#E67E22"),
                card("Respondidos", respondidos, "#3498DB"),
                card("Resolvidos", resolvidos, "#27AE60"),
                card("Total", todos.size(), "#34495E"));
    }

    private void abrirDetalhe(PedidoSuporte p) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Pedido de " + (p.getUtilizador() != null ? p.getUtilizador() : "—"));
        d.setWidth("620px");

        Span meta = new Span((p.getStudio() != null ? p.getStudio() + "  ·  " : "")
                + (p.getTipo() != null ? p.getTipo() + "  ·  " : "")
                + (p.getDataCriacao() != null ? DataUtil.formatar(p.getDataCriacao()) : "")
                + (p.getEmailUtilizador() != null ? "  ·  " + p.getEmailUtilizador() : "  ·  (sem email)"));
        meta.getStyle().set("font-size", "12px").set("color", "#888");

        Span assunto = new Span(p.getAssunto());
        assunto.getStyle().set("font-weight", "700").set("font-size", "15px");

        Pre descr = new Pre(p.getDescricao() != null ? p.getDescricao() : "");
        descr.getStyle().set("white-space", "pre-wrap").set("background", "#f7f7f9").set("padding", "10px")
                .set("border-radius", "8px").set("font-size", "13px").set("margin", "6px 0");

        VerticalLayout conteudo = new VerticalLayout(meta, assunto, descr);
        conteudo.setPadding(false);
        conteudo.setSpacing(false);

        if (p.isRespondido()) {
            Span jaResp = new Span("Resposta enviada em "
                    + (p.getDataResposta() != null ? DataUtil.formatar(p.getDataResposta()) : "—") + ":");
            jaResp.getStyle().set("font-size", "12px").set("color", "#3498DB").set("font-weight", "700")
                    .set("margin-top", "8px");
            Pre respAntiga = new Pre(p.getResposta());
            respAntiga.getStyle().set("white-space", "pre-wrap").set("background", "#eef6fd")
                    .set("padding", "10px").set("border-radius", "8px").set("font-size", "13px");
            conteudo.add(jaResp, respAntiga);
        }

        TextArea resposta = new TextArea(p.isRespondido() ? "Nova resposta" : "Resposta");
        resposta.setWidthFull();
        resposta.setMinHeight("120px");
        resposta.setPlaceholder(p.getEmailUtilizador() == null
                ? "Este pedido não tem email do utilizador — a resposta fica só registada."
                : "Escreve a resposta ao cliente…");

        Checkbox resolver = new Checkbox("Marcar como resolvido");
        resolver.setValue(!p.isResolvido() && p.isRespondido());

        Button enviar = new Button("Enviar resposta", VaadinIcon.PAPERPLANE.create(), e -> {
            if (resposta.getValue() == null || resposta.getValue().isBlank()) {
                Notification.show("Escreve a resposta.").addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            boolean enviado = suporteService.responder(p.getId(), resposta.getValue(), resolver.getValue());
            Notification.show(enviado ? "Resposta enviada ao cliente."
                    : "Resposta registada (sem email do utilizador).", 3500, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            d.close();
            recarregar();
        });
        enviar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button marcar = new Button(p.isResolvido() ? "Reabrir" : "Só marcar resolvido", e -> {
            p.setResolvido(!p.isResolvido());
            repo.save(p);
            d.close();
            recarregar();
        });
        marcar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        conteudo.add(resposta, resolver);
        d.add(conteudo);
        d.getFooter().add(marcar, new Button("Fechar", e -> d.close()), enviar);
        d.open();
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
