package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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
import pt.studioflow.model.AulaExperimental;
import pt.studioflow.model.AulaExperimental.Estado;
import pt.studioflow.model.Studio;
import pt.studioflow.service.AulaExperimentalService;
import pt.studioflow.view.component.AulaExperimentalDialog;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Aulas experimentais: registadas pelo estúdio (telefone, balcão, mensagem) ou marcadas
 * online pelos interessados. Permite marcar novas, acompanhar o estado e passar quem
 * gostou para "Validar Inscrições".
 */
@Route(value = "aulas-experimentais", layout = MainLayout.class)
@PageTitle("Aulas Experimentais | CoreoFlow")
@RolesAllowed("ADMIN")
public class AulasExperimentaisView extends VerticalLayout {

    private static final Locale PT = Locale.forLanguageTag("pt-PT");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AulaExperimentalService service;
    private final Studio studio;
    private final Grid<AulaExperimental> grid = new Grid<>();
    private final Tabs filtro = new Tabs();
    private final Tab tabProximas = new Tab("Próximas");
    private final Tab tabHoje = new Tab("Hoje");
    private final Tab tabHistorico = new Tab("Histórico");
    private final Tab tabTodas = new Tab("Todas");
    private final TextField pesquisa = new TextField();
    private final Div stats = new Div();

    @org.springframework.beans.factory.annotation.Value("${app.base-url:https://app.coreoflow.me}")
    private String baseUrl;

    public AulasExperimentaisView(AulaExperimentalService service) {
        this.service = service;
        this.studio = TenantContext.getCurrentStudio();

        setSizeFull();
        setPadding(false);
        addClassName("cf-page");
        ViewUtils.injetarEstiloBotoesAcao();

        H2 titulo = new H2("Aulas Experimentais");
        Button nova = new Button("Marcar aula experimental", VaadinIcon.PLUS.create(), e -> abrirNova());
        nova.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button link = new Button("Link de marcação online", VaadinIcon.LINK.create(), e -> mostrarLink());
        HorizontalLayout acoes = new HorizontalLayout(link, nova);
        acoes.getStyle().set("flex-wrap", "wrap");
        HorizontalLayout header = new HorizontalLayout(titulo, acoes);
        header.addClassName("cf-page-header");
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        filtro.add(tabProximas, tabHoje, tabHistorico, tabTodas);
        filtro.addSelectedChangeListener(e -> atualizar());
        pesquisa.setPlaceholder("Procurar nome, telefone ou turma…");
        pesquisa.setPrefixComponent(VaadinIcon.SEARCH.create());
        pesquisa.setClearButtonVisible(true);
        pesquisa.setValueChangeMode(ValueChangeMode.LAZY);
        pesquisa.addValueChangeListener(e -> atualizar());
        pesquisa.setWidth("min(320px, 100%)");
        HorizontalLayout barra = new HorizontalLayout(filtro, pesquisa);
        barra.setWidthFull();
        barra.setAlignItems(Alignment.CENTER);
        barra.setJustifyContentMode(JustifyContentMode.BETWEEN);
        barra.getStyle().set("flex-wrap", "wrap");

        configurarGrid();
        add(header, stats, barra, grid);
        expand(grid);
        atualizar();
    }

    private void configurarGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addComponentColumn(this::colQuando).setHeader("Quando").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::colPessoa).setHeader("Interessado").setFlexGrow(2);
        grid.addColumn(a -> a.getTurma().getDescricao()).setHeader("Turma").setFlexGrow(1);
        grid.addComponentColumn(a -> pill(a.getOrigem().getLabel(),
                a.getOrigem() == AulaExperimental.Origem.ONLINE ? "violet" : "neutral"))
                .setHeader("Origem").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(a -> pill(a.getEstado().getLabel(), tom(a.getEstado())))
                .setHeader("Estado").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::colAcoes).setHeader("Ações").setAutoWidth(true).setFlexGrow(0);
        grid.setItemDetailsRenderer(new com.vaadin.flow.data.renderer.ComponentRenderer<>(a -> {
            Span s = new Span(a.getObservacoes() != null ? a.getObservacoes() : "Sem observações.");
            s.addClassName("cf-cell-sub");
            return s;
        }));
        grid.setDetailsVisibleOnClick(true);
        grid.setEmptyStateText("Sem aulas experimentais neste filtro.");
    }

    private Component colQuando(AulaExperimental a) {
        String dia = a.getData().getDayOfWeek().getDisplayName(TextStyle.SHORT, PT);
        Span d = new Span((a.getData().equals(LocalDate.now()) ? "Hoje" : dia + " " + a.getData().format(DATA)));
        d.getStyle().set("font-weight", "600");
        Span h = new Span(a.getHoraInicio() != null ? a.getHoraInicio().toString() : "");
        h.addClassName("cf-cell-sub");
        VerticalLayout v = new VerticalLayout(d, h);
        v.setPadding(false);
        v.setSpacing(false);
        return v;
    }

    private Component colPessoa(AulaExperimental a) {
        Span n = new Span(a.getNomeCompleto());
        n.getStyle().set("font-weight", "600");
        String contacto = String.join(" · ", java.util.stream.Stream.of(a.getTelemovel(), a.getEmail())
                .filter(x -> x != null && !x.isBlank()).toList());
        Span c = new Span(contacto);
        c.addClassName("cf-cell-sub");
        VerticalLayout v = new VerticalLayout(n, c);
        v.setPadding(false);
        v.setSpacing(false);
        return v;
    }

    private Component colAcoes(AulaExperimental a) {
        HorizontalLayout h = new HorizontalLayout();
        h.setSpacing(false);
        h.getStyle().set("gap", "4px");
        if (a.getTelemovel() != null && !a.getTelemovel().isBlank()) {
            String num = a.getTelemovel().replaceAll("\\D", "");
            if (num.length() == 9) num = "351" + num;
            String msg = "Olá " + a.getNomeCompleto().split(" ")[0] + "! Confirmamos a sua aula experimental de "
                    + a.getTurma().getDescricao() + " no dia " + a.getData().format(DATA)
                    + (a.getHoraInicio() != null ? " às " + a.getHoraInicio() : "") + ". Até já!";
            Anchor wa = new Anchor("https://wa.me/" + num + "?text="
                    + java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8), "");
            wa.setTarget("_blank");
            wa.add(ViewUtils.botaoAcao(VaadinIcon.COMMENT, "btn-view", "Confirmar por WhatsApp", e -> { }));
            h.add(wa);
        }
        if (a.getEstado() == Estado.MARCADA) {
            h.add(ViewUtils.botaoAcao(VaadinIcon.CHECK, "btn-view", "Veio à aula", e -> mudar(a, Estado.REALIZADA)));
            h.add(ViewUtils.botaoAcao(VaadinIcon.CLOSE_SMALL, "btn-warn", "Faltou", e -> mudar(a, Estado.FALTOU)));
            h.add(ViewUtils.botaoAcao(VaadinIcon.BAN, "btn-del", "Cancelar marcação", e -> mudar(a, Estado.CANCELADA)));
        }
        if (a.getEstado() == Estado.MARCADA || a.getEstado() == Estado.REALIZADA) {
            h.add(ViewUtils.botaoAcao(VaadinIcon.USER_CHECK, "btn-purple",
                    "Quer inscrever-se — passa para Validar Inscrições", e -> mudar(a, Estado.INSCRITO)));
        }
        return h;
    }

    private void mudar(AulaExperimental a, Estado estado) {
        service.mudarEstado(a, estado);
        Notification.show(estado == Estado.INSCRITO
                ? a.getNomeCompleto() + " passou para Validar Inscrições."
                : "Marcada como \"" + estado.getLabel() + "\".", 3000, Notification.Position.BOTTOM_START)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        atualizar();
    }

    private void abrirNova() {
        if (studio == null) return;
        new AulaExperimentalDialog(service, studio, ae -> atualizar()).open();
    }

    private void mostrarLink() {
        if (studio == null) return;
        String url = baseUrl + "/aula-experimental?studio=" + studio.getSlug();
        com.vaadin.flow.component.dialog.Dialog d = new com.vaadin.flow.component.dialog.Dialog();
        d.setHeaderTitle("Marcação online de aulas experimentais");
        TextField campo = new TextField();
        campo.setValue(url);
        campo.setReadOnly(true);
        campo.setWidthFull();
        Span info = new Span("Partilha este link no site, Instagram ou WhatsApp. Os interessados escolhem a turma "
                + "e o dia, e a marcação aparece aqui com a origem \"Marcação online\". A página de inscrição "
                + "também tem um botão para lá chegar.");
        info.addClassName("cf-cell-sub");
        Button copiar = new Button("Copiar", VaadinIcon.COPY.create(), e -> {
            campo.getElement().executeJs("navigator.clipboard.writeText($0)", url);
            Notification.show("Link copiado").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        copiar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Anchor abrir = new Anchor(url, "Abrir página");
        abrir.setTarget("_blank");
        VerticalLayout v = new VerticalLayout(info, campo, abrir);
        v.setPadding(false);
        d.add(v);
        d.setWidth("min(560px, 96vw)");
        d.getFooter().add(new Button("Fechar", e -> d.close()), copiar);
        d.open();
    }

    private void atualizar() {
        if (studio == null) return;
        List<AulaExperimental> todas = service.listar(studio);
        LocalDate hoje = LocalDate.now();
        Tab sel = filtro.getSelectedTab();
        List<AulaExperimental> lista = todas.stream().filter(a -> {
            if (sel == tabProximas) return a.getEstado() == Estado.MARCADA && !a.getData().isBefore(hoje);
            if (sel == tabHoje) return a.getData().equals(hoje);
            if (sel == tabHistorico) return a.getData().isBefore(hoje) || a.getEstado() != Estado.MARCADA;
            return true;
        }).filter(this::correspondePesquisa).toList();
        if (sel == tabProximas || sel == tabHoje) {
            lista = lista.stream().sorted(java.util.Comparator.comparing(AulaExperimental::getInicio)).toList();
        }
        grid.setItems(lista);

        long proximas = todas.stream().filter(a -> a.getEstado() == Estado.MARCADA && !a.getData().isBefore(hoje)).count();
        long hojeN = todas.stream().filter(a -> a.getEstado() == Estado.MARCADA && a.getData().equals(hoje)).count();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        List<AulaExperimental> mes = todas.stream().filter(a -> !a.getData().isBefore(inicioMes)
                && !a.getData().isAfter(hoje)).toList();
        long vieram = mes.stream().filter(a -> a.getEstado() == Estado.REALIZADA || a.getEstado() == Estado.INSCRITO).count();
        long inscritos = mes.stream().filter(a -> a.getEstado() == Estado.INSCRITO).count();
        stats.removeAll();
        stats.add(ViewUtils.statsRow(
                ViewUtils.statCardTom("Hoje", new Span(String.valueOf(hojeN)), "info", VaadinIcon.CALENDAR_CLOCK, true),
                ViewUtils.statCardTom("Por realizar", new Span(String.valueOf(proximas)), "violet", VaadinIcon.MAGIC, true),
                ViewUtils.statCardTom("Vieram este mês", new Span(String.valueOf(vieram)), "success", VaadinIcon.CHECK, true),
                ViewUtils.statCardTom("Inscreveram-se este mês", new Span(String.valueOf(inscritos)), "success",
                        VaadinIcon.USER_CHECK, true)));
    }

    private boolean correspondePesquisa(AulaExperimental a) {
        String q = norm(pesquisa.getValue());
        if (q.isEmpty()) return true;
        return norm(a.getNomeCompleto()).contains(q) || norm(a.getTelemovel()).contains(q)
                || norm(a.getEmail()).contains(q) || norm(a.getTurma().getDescricao()).contains(q);
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private static Span pill(String texto, String tom) {
        Span s = new Span(texto);
        s.addClassNames("cf-pill", "cf-pill--" + tom);
        return s;
    }

    private static String tom(Estado e) {
        return switch (e) {
            case MARCADA -> "info";
            case REALIZADA -> "success";
            case INSCRITO -> "violet";
            case FALTOU -> "warning";
            case CANCELADA -> "neutral";
        };
    }
}
