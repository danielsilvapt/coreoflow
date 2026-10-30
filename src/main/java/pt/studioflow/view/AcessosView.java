package pt.studioflow.view;

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
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.data.domain.PageRequest;
import pt.studioflow.model.RegistoAuditoria;
import pt.studioflow.model.RegistoAuditoria.Tipo;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.RegistoAuditoriaRepository;
import pt.studioflow.repository.RegistoAuditoriaRepository.ResumoUtilizador;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.util.DataUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Auditoria de acessos para o superadmin: quem entrou, de onde, em que estúdio,
 * que páginas abriu e que dados criou/alterou/removeu.
 */
@Route(value = "admin/acessos", layout = MainLayout.class)
@PageTitle("Acessos & Auditoria | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class AcessosView extends VerticalLayout {

    /** Opção do filtro de estúdio: todos, só plataforma (sem estúdio) ou um estúdio concreto. */
    private record OpcaoEstudio(String label, Long studioId, boolean plataforma) {
    }

    private static final OpcaoEstudio TODOS = new OpcaoEstudio("Todos", null, false);
    private static final OpcaoEstudio PLATAFORMA = new OpcaoEstudio("Plataforma (superadmin)", null, true);
    private static final EnumSet<Tipo> TIPOS_DADOS = EnumSet.of(Tipo.CRIACAO, Tipo.ALTERACAO, Tipo.REMOCAO);

    private final RegistoAuditoriaRepository repo;
    private final StudioRepository studioRepository;

    private final ComboBox<OpcaoEstudio> estudio = new ComboBox<>("Estúdio");
    private final ComboBox<String> periodo = new ComboBox<>("Período");
    private final ComboBox<Tipo> tipo = new ComboBox<>("Tipo");
    private final TextField utilizador = new TextField("Utilizador");
    private final TextField pesquisa = new TextField("Pesquisar");
    private final HorizontalLayout cards = new HorizontalLayout();
    private final Grid<RegistoAuditoria> gridAtividade = new Grid<>();
    private final Grid<ResumoUtilizador> gridUtilizadores = new Grid<>();
    private final Tabs tabs = new Tabs();
    private final Tab tabAtividade = new Tab(VaadinIcon.LIST.create(), new Span("Atividade"));
    private final Tab tabUtilizadores = new Tab(VaadinIcon.USERS.create(), new Span("Por utilizador"));

    public AcessosView(RegistoAuditoriaRepository repo, StudioRepository studioRepository) {
        this.repo = repo;
        this.studioRepository = studioRepository;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Acessos & Auditoria");
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

        estudio.setItemLabelGenerator(OpcaoEstudio::label);
        estudio.setWidth("220px");
        periodo.setItems("Últimas 24h", "Últimos 7 dias", "Últimos 30 dias", "Últimos 90 dias", "Tudo");
        periodo.setValue("Últimos 7 dias");
        periodo.setWidth("170px");
        tipo.setItems(Tipo.values());
        tipo.setItemLabelGenerator(Tipo::getLabel);
        tipo.setPlaceholder("Todos");
        tipo.setClearButtonVisible(true);
        tipo.setWidth("170px");
        utilizador.setPlaceholder("username…");
        utilizador.setClearButtonVisible(true);
        utilizador.setValueChangeMode(ValueChangeMode.LAZY);
        utilizador.setWidth("180px");
        pesquisa.setPlaceholder("ação, entidade ou IP…");
        pesquisa.setClearButtonVisible(true);
        pesquisa.setValueChangeMode(ValueChangeMode.LAZY);
        pesquisa.setWidth("240px");

        estudio.addValueChangeListener(e -> recarregar());
        periodo.addValueChangeListener(e -> recarregar());
        tipo.addValueChangeListener(e -> recarregar());
        utilizador.addValueChangeListener(e -> recarregar());
        pesquisa.addValueChangeListener(e -> recarregar());

        HorizontalLayout filtros = new HorizontalLayout(estudio, periodo, tipo, utilizador, pesquisa);
        filtros.setAlignItems(FlexComponent.Alignment.END);
        filtros.getStyle().set("flex-wrap", "wrap");
        add(filtros);

        tabs.add(tabAtividade, tabUtilizadores);
        tabs.addSelectedChangeListener(e -> mostrarTab());
        add(tabs);

        configurarGridAtividade();
        configurarGridUtilizadores();
        add(gridAtividade, gridUtilizadores);
        expand(gridAtividade, gridUtilizadores);

        recarregarFiltroEstudios();
        mostrarTab();
    }

    private void configurarGridAtividade() {
        gridAtividade.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        gridAtividade.setSizeFull();

        gridAtividade.addColumn(r -> r.getData() != null ? DataUtil.formatar(r.getData()) : "")
                .setHeader("Data").setAutoWidth(true).setFlexGrow(0);
        gridAtividade.addComponentColumn(r -> badge(r.getTipo()))
                .setHeader("Tipo").setAutoWidth(true).setFlexGrow(0);
        gridAtividade.addColumn(RegistoAuditoria::getEstudioApresentacao)
                .setHeader("Estúdio").setAutoWidth(true).setFlexGrow(0);
        gridAtividade.addComponentColumn(r -> {
            Span u = new Span(r.getUtilizador() != null ? r.getUtilizador() : "");
            if (r.isPasswordMestra()) {
                Span m = new Span(" 🔑");
                m.getElement().setAttribute("title", "Login com password mestra");
                return new Span(u, m);
            }
            return u;
        }).setHeader("Utilizador").setAutoWidth(true).setFlexGrow(0);
        gridAtividade.addColumn(RegistoAuditoria::getPapel).setHeader("Papel").setAutoWidth(true).setFlexGrow(0);
        gridAtividade.addColumn(RegistoAuditoria::getDescricao).setHeader("Ação").setFlexGrow(1);
        gridAtividade.addColumn(RegistoAuditoria::getIp).setHeader("IP").setAutoWidth(true).setFlexGrow(0);

        gridAtividade.setItemDetailsRenderer(new ComponentRenderer<>(r -> {
            VerticalLayout d = new VerticalLayout();
            d.setPadding(false);
            d.setSpacing(false);
            String meta = (r.getEntidade() != null ? r.getEntidade() + " #" + r.getEntidadeId() + "  ·  " : "")
                    + "estúdio " + r.getEstudioApresentacao()
                    + (r.getIp() != null ? "  ·  IP " + r.getIp() : "")
                    + (r.isPasswordMestra() ? "  ·  password mestra" : "");
            Span m = new Span(meta);
            m.getStyle().set("font-size", "12px").set("color", "#888");
            Span texto = new Span(r.getDescricao() != null ? r.getDescricao() : "");
            texto.getStyle().set("white-space", "pre-wrap").set("font-size", "13px")
                    .set("background", "#f7f7f9").set("padding", "8px").set("border-radius", "6px")
                    .set("margin-top", "6px").set("width", "100%");
            d.add(m, texto);
            return d;
        }));
    }

    private void configurarGridUtilizadores() {
        gridUtilizadores.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        gridUtilizadores.setSizeFull();

        gridUtilizadores.addColumn(r -> r.studioNome() != null ? r.studioNome() : "Plataforma")
                .setHeader("Estúdio").setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing((ResumoUtilizador r) -> r.studioNome() != null ? r.studioNome() : ""));
        gridUtilizadores.addColumn(ResumoUtilizador::utilizador).setHeader("Utilizador").setAutoWidth(true)
                .setSortable(true).setComparator(Comparator.comparing(ResumoUtilizador::utilizador));
        gridUtilizadores.addColumn(ResumoUtilizador::papel).setHeader("Papel").setAutoWidth(true);
        gridUtilizadores.addColumn(r -> r.ultimoLogin() != null ? DataUtil.formatar(r.ultimoLogin()) : "—")
                .setHeader("Último login").setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(ResumoUtilizador::ultimoLogin,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        gridUtilizadores.addColumn(r -> r.ultimaAtividade() != null ? DataUtil.formatar(r.ultimaAtividade()) : "—")
                .setHeader("Última atividade").setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(ResumoUtilizador::ultimaAtividade,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        gridUtilizadores.addColumn(ResumoUtilizador::logins).setHeader("Logins").setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(ResumoUtilizador::logins));
        gridUtilizadores.addColumn(ResumoUtilizador::alteracoes).setHeader("Alterações de dados").setAutoWidth(true)
                .setSortable(true).setComparator(Comparator.comparing(ResumoUtilizador::alteracoes));

        // Clicar num utilizador abre a atividade dele
        gridUtilizadores.addItemClickListener(e -> {
            ResumoUtilizador r = e.getItem();
            estudio.setValue(estudio.getListDataView().getItems()
                    .filter(o -> r.studioId() == null ? o.plataforma() : r.studioId().equals(o.studioId()))
                    .findFirst().orElse(TODOS));
            tipo.clear();
            pesquisa.clear();
            utilizador.setValue(r.utilizador());
            tabs.setSelectedTab(tabAtividade);
        });
    }

    private void mostrarTab() {
        boolean atividade = tabs.getSelectedTab() == tabAtividade;
        gridAtividade.setVisible(atividade);
        gridUtilizadores.setVisible(!atividade);
        tipo.setVisible(atividade);
        utilizador.setVisible(atividade);
        pesquisa.setVisible(atividade);
        recarregar();
    }

    private void recarregarFiltroEstudios() {
        OpcaoEstudio atual = estudio.getValue();
        List<OpcaoEstudio> opcoes = new ArrayList<>();
        opcoes.add(TODOS);
        opcoes.add(PLATAFORMA);
        studioRepository.findAll().stream()
                .sorted(Comparator.comparing(s -> s.getNome() != null ? s.getNome() : ""))
                .forEach((Studio s) -> opcoes.add(new OpcaoEstudio(s.getNome(), s.getId(), false)));
        estudio.setItems(opcoes);
        estudio.setValue(atual != null && opcoes.contains(atual) ? atual : TODOS);
    }

    private LocalDateTime desde() {
        return switch (periodo.getValue() == null ? "" : periodo.getValue()) {
            case "Últimas 24h" -> LocalDateTime.now().minusHours(24);
            case "Últimos 7 dias" -> LocalDateTime.now().minusDays(7);
            case "Últimos 30 dias" -> LocalDateTime.now().minusDays(30);
            case "Últimos 90 dias" -> LocalDateTime.now().minusDays(90);
            default -> null;
        };
    }

    private void recarregar() {
        OpcaoEstudio e = estudio.getValue() != null ? estudio.getValue() : TODOS;
        LocalDateTime desde = desde();

        if (tabs.getSelectedTab() == tabAtividade) {
            Tipo t = tipo.getValue();
            String u = vazioParaNull(utilizador.getValue());
            String p = vazioParaNull(pesquisa.getValue());
            // Carregamento lazy: só busca à BD as páginas que o grid mostra
            gridAtividade.setItems(q -> repo.pesquisar(t, e.studioId(), e.plataforma(), u, p, desde,
                    PageRequest.of(q.getPage(), q.getPageSize())).stream());
        } else {
            gridUtilizadores.setItems(repo.resumoPorUtilizador(
                    desde != null ? desde : LocalDateTime.of(2000, 1, 1, 0, 0),
                    e.studioId(), e.plataforma(), Tipo.LOGIN, Tipo.LOGIN_FALHADO, TIPOS_DADOS));
        }

        cards.removeAll();
        LocalDateTime h24 = LocalDateTime.now().minusHours(24);
        cards.add(
                card("Logins (24h)", repo.countByTipoAndDataAfter(Tipo.LOGIN, h24), "#27AE60"),
                card("Utilizadores distintos (24h)", repo.contarUtilizadoresComLogin(Tipo.LOGIN, h24), "#4A90E2"),
                card("Logins falhados (24h)", repo.countByTipoAndDataAfter(Tipo.LOGIN_FALHADO, h24), "#E74C3C"),
                card("Alterações de dados (24h)", repo.countByTipoInAndDataAfter(TIPOS_DADOS, h24), "#F39C12"));
    }

    private static String vazioParaNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static Component badge(Tipo t) {
        Span s = new Span(t != null ? t.getLabel() : "");
        s.getStyle().set("background", t != null ? t.getCor() : "#999").set("color", "white")
                .set("padding", "1px 8px").set("border-radius", "10px")
                .set("font-size", "11px").set("font-weight", "700").set("white-space", "nowrap");
        return s;
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
