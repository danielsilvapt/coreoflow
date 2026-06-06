package pt.studioflow.view;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.TextStyle; // ADICIONADO
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale; // ADICIONADO
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.service.VendusApiService;

@PageTitle("Alunos | CoreoFlow")
@Route(value = "alunos/:alunoID?", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AlunoView extends VerticalLayout implements AfterNavigationObserver {

    private final AlunoRepository alunoRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final MensalidadeRepository mensalidadeRepository;
    private final Grid<Aluno> grid = new Grid<>(Aluno.class, false);
    private final AlunoForm alunoForm;

    private ListDataProvider<Aluno> dataProvider;
    private final Map<Long, Boolean> cacheDividas = new HashMap<>();
    private final Map<String, String> filtrosTexto = new HashMap<>();
    private String filtroAtivo = "Ativos";

    private final Span totalAlunosLabel = new Span("0");
    private final Span ativosLabel = new Span("0");
    private final Span dividasLabel = new Span("0");

    @Autowired
    private final VendusApiService vendusService;

    public AlunoView(AlunoRepository alunoRepository, MensalidadeRepository mensalidadeRepository,
            VendusApiService vendusService, AlunoTurmaRepository alunoTurmaRepository) {
        this.alunoRepository = alunoRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.mensalidadeRepository = mensalidadeRepository;
        this.vendusService = vendusService;
        this.alunoForm = new AlunoForm(alunoRepository, alunoTurmaRepository);
        this.alunoForm.setOnSaveCallback(this::updateList);

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        injectStyles();

        H2 titulo = new H2("Gestão de Alunos");
        titulo.getStyle().set("margin-top", "0");

        Button btnNovo = new Button("+ Novo Aluno", e -> {
            alunoForm.abrir(null);
            vincularAtualizacaoAoFechar();
        });
        btnNovo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout header = new HorizontalLayout(titulo, btnNovo);
        header.setWidthFull();
        header.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.BETWEEN);
        header.getStyle().set("padding", "15px 20px 0 20px");

        add(header, criarStatsCards(), grid);

        configureGrid();
        updateList();
    }

    private void injectStyles() {
        String styles = ".stat-card { background: white; padding: 20px; border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); border: 1px solid #eee; min-width: 200px; flex-grow: 1; }"
                +
                ".stat-title { color: #7f8c8d; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 1px; font-weight: 600; }"
                +
                ".stat-value { font-size: 1.6rem; font-weight: 800; display: block; margin-top: 5px; }" +
                ".action-btn { border-radius: 10px; padding: 8px; transition: all 0.2s; border: 1px solid rgba(0,0,0,0.05); box-shadow: 0 2px 4px rgba(0,0,0,0.05); }"
                +
                ".action-btn:hover { transform: translateY(-2px); box-shadow: 0 4px 8px rgba(0,0,0,0.1); }" +
                ".btn-edit { background: #E8F0FE; color: #1967D2; }" +
                ".btn-del { background: #FCE8E6; color: #D93025; }";

        UI.getCurrent().getElement().executeJs(
                "const style = document.createElement('style'); style.textContent = $0; document.head.appendChild(style);",
                styles);
    }

    private Component criarStatsCards() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.getStyle().set("margin", "15px 0");

        layout.add(
                criarCard("Registados", totalAlunosLabel, "#2D3436", VaadinIcon.USERS),
                criarCard("Ativos", ativosLabel, "#1E8E3E", VaadinIcon.USER_CHECK),
                criarCard("Em Dívida", dividasLabel, "#D93025", VaadinIcon.WARNING));
        return layout;
    }

    private Div criarCard(String titulo, Span val, String color, VaadinIcon icon) {
        Div card = new Div();
        card.addClassName("stat-card");
        Icon i = icon.create();
        i.setColor(color);
        i.getStyle().set("float", "right");
        Span t = new Span(titulo);
        t.addClassName("stat-title");
        val.addClassName("stat-value");
        val.getStyle().set("color", color);
        card.add(i, t, val);
        return card;
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);

        grid.addComponentColumn(aluno -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> {
                alunoForm.abrirFormulario(aluno);
                vincularAtualizacaoAoFechar();
            });
            editBtn.addClassNames("action-btn", "btn-edit");
            Button deleteBtn = new Button(VaadinIcon.TRASH.create(), e -> confirmarEliminacao(aluno));
            deleteBtn.addClassNames("action-btn", "btn-del");
            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("AÇÕES").setWidth("130px").setFlexGrow(0).setFrozen(true);

        grid.addColumn(new ComponentRenderer<>(aluno -> {
            HorizontalLayout row = new HorizontalLayout();
            row.setAlignItems(Alignment.CENTER);
            Image image;
            if (aluno.getFoto() != null && aluno.getFoto().length > 0) {
                StreamResource resource = new StreamResource("foto.png",
                        () -> new ByteArrayInputStream(aluno.getFoto()));
                image = new Image(resource, "");
            } else {
                image = new Image("images/avatar-default.png", "");
            }
            image.setWidth("40px");
            image.setHeight("40px");
            image.getStyle().set("border-radius", "50%").set("object-fit", "cover");
            VerticalLayout info = new VerticalLayout(new Span(aluno.getNomeCompleto()));
            info.setPadding(false);
            info.setSpacing(false);
            row.add(image, info);
            return row;
        })).setHeader("ALUNO").setSortable(true).setComparator(Comparator.comparing(Aluno::getNomeCompleto))
                .setFlexGrow(1);

        grid.addColumn(Aluno::getTelemovel).setHeader("TELEMÓVEL").setAutoWidth(true);

        grid.addComponentColumn(aluno -> {
            boolean temDivida = temDividaMaisDeUmMes(aluno);
            Span badge = new Span(temDivida ? "DÍVIDA" : "REGULAR");
            badge.getElement().getThemeList().add("badge " + (temDivida ? "error" : "success") + " pill");
            badge.addClickListener(e -> abrirModalMensalidades(aluno,
                    temDivida ? EstadoMensalidade.FATURADO : EstadoMensalidade.PAGO));
            return badge;
        }).setHeader("FINANCEIRO").setAutoWidth(true).setTextAlign(ColumnTextAlign.CENTER);

        grid.addComponentColumn(aluno -> {
            Icon icon = aluno.isAtivo() ? VaadinIcon.CHECK_CIRCLE.create() : VaadinIcon.CLOSE_CIRCLE.create();
            icon.setColor(aluno.isAtivo() ? "#1E8E3E" : "#D93025");
            return icon;
        }).setHeader("ESTADO").setWidth("100px").setTextAlign(ColumnTextAlign.CENTER);

        HeaderRow filterRow = grid.appendHeaderRow();
        TextField fNome = criarFiltroTexto("nome");
        fNome.setPlaceholder("Pesquisar...");
        filterRow.getCell(grid.getColumns().get(1)).setComponent(fNome);

        ComboBox<String> comboAtivo = new ComboBox<>();
        comboAtivo.setItems("Todos", "Ativos", "Não Ativos");
        comboAtivo.setValue("Ativos");
        comboAtivo.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
        comboAtivo.addValueChangeListener(e -> {
            filtroAtivo = e.getValue();
            aplicarFiltros();
        });
        filterRow.getCell(grid.getColumns().get(4)).setComponent(comboAtivo);
    }

    private TextField criarFiltroTexto(String campo) {
        TextField tf = new TextField();
        tf.addThemeVariants(TextFieldVariant.LUMO_SMALL);
        tf.setWidthFull();
        tf.setValueChangeMode(ValueChangeMode.EAGER);
        tf.addValueChangeListener(e -> {
            filtrosTexto.put(campo, e.getValue());
            aplicarFiltros();
        });
        return tf;
    }

    private void aplicarFiltros() {
        if (dataProvider == null)
            return;
        dataProvider.setFilter(aluno -> {
            boolean matchNome = filtrosTexto.getOrDefault("nome", "").isEmpty() ||
                    removerAcentos(aluno.getNomeCompleto().toLowerCase())
                            .contains(removerAcentos(filtrosTexto.get("nome").toLowerCase()));
            boolean matchAtivo = "Todos".equals(filtroAtivo)
                    || ("Ativos".equals(filtroAtivo) ? aluno.isAtivo() : !aluno.isAtivo());
            return matchNome && matchAtivo;
        });
        updateDashboard();
    }

    private void updateDashboard() {
        // CORREÇÃO: O Vaadin Flow usa esta forma para obter os itens filtrados do
        // DataProvider
        List<Aluno> filtrados = dataProvider.getItems().stream()
                .filter(aluno -> dataProvider.getFilter() == null || dataProvider.getFilter().test(aluno))
                .collect(Collectors.toList());

        totalAlunosLabel.setText(String.valueOf(filtrados.size()));
        ativosLabel.setText(String.valueOf(filtrados.stream().filter(Aluno::isAtivo).count()));
        dividasLabel.setText(String.valueOf(filtrados.stream().filter(this::temDividaMaisDeUmMes).count()));
    }

    public void updateList() {
        cacheDividas.clear();
        Studio studio = TenantContext.getCurrentStudio();
        List<Aluno> alunos = studio != null
                ? alunoRepository.findAllByStudio(studio)
                : alunoRepository.findAll();
        dataProvider = new ListDataProvider<>(alunos);
        grid.setDataProvider(dataProvider);
        aplicarFiltros();
    }

    private String removerAcentos(String texto) {
        if (texto == null)
            return "";
        String nfdNormalizedString = Normalizer.normalize(texto, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfdNormalizedString).replaceAll("");
    }

    private boolean temDividaMaisDeUmMes(Aluno aluno) {
        return cacheDividas.computeIfAbsent(aluno.getId(), id -> {
            List<Mensalidade> faturadas = mensalidadeRepository.findByAlunoAndEstado(aluno, EstadoMensalidade.FATURADO);
            LocalDate hoje = LocalDate.now();
            for (Mensalidade m : faturadas) {
                LocalDate dataLimite = LocalDate.of(m.getAno(), m.getMes(), 10);
                if (hoje.isAfter(dataLimite))
                    return true;
            }
            return false;
        });
    }

    private void abrirModalMensalidades(Aluno aluno, EstadoMensalidade estado) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(
                (estado == EstadoMensalidade.FATURADO ? "Dívidas" : "Pagamentos") + " - " + aluno.getNomeCompleto());
        Grid<Mensalidade> mGrid = new Grid<>(Mensalidade.class, false);
        mGrid.addColumn(m -> m.getMes().getDisplayName(TextStyle.FULL, new Locale("pt", "PT"))).setHeader("Mês");
        mGrid.addColumn(Mensalidade::getAno).setHeader("Ano");
        mGrid.addColumn(m -> String.format("%.2f€", m.getValor())).setHeader("Valor");
        mGrid.setItems(mensalidadeRepository.findByAlunoAndEstado(aluno, estado));
        dialog.add(mGrid);
        dialog.open();
    }

    private void confirmarEliminacao(Aluno aluno) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Eliminar");
        dialog.add("Apagar " + aluno.getNomeCompleto() + "?");
        Button confirm = new Button("Sim", e -> {
            alunoRepository.delete(aluno);
            updateList();
            dialog.close();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(new Button("Não", e -> dialog.close()), confirm);
        dialog.open();
    }

    private void abrirFormAluno(Aluno aluno) {
        alunoForm.abrirFormulario(aluno);
        vincularAtualizacaoAoFechar();
    }

    private void vincularAtualizacaoAoFechar() {
        this.getUI().ifPresent(ui -> ui.access(this::updateList));
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        Map<String, List<String>> params = event.getLocation().getQueryParameters().getParameters();
        if (params.containsKey("edit")) {
            try {
                Long id = Long.parseLong(params.get("edit").get(0));
                alunoRepository.findById(id).ifPresent(this::abrirFormAluno);
            } catch (NumberFormatException ignored) {}
        }
        updateList();
    }
}
