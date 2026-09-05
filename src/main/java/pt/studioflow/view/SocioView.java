package pt.studioflow.view;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.service.R2StorageService;

import java.time.Duration;

@PageTitle("Controlo de Sócios | CoreoFlow")
@Route(value = "socios", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class SocioView extends VerticalLayout {

    private final AlunoRepository alunoRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final R2StorageService storageService;
    private final Grid<Aluno> grid = new Grid<>(Aluno.class, false);
    private final AlunoForm socioForm;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yy");

    private ListDataProvider<Aluno> dataProvider;
    private final Map<String, String> filtrosTexto = new HashMap<>();
    private String filtroQuotas = "Todas";

    // Filtros independentes para os 4 estados combinados
    private String filtroSocioAtivo = "Todos";
    private String filtroAlunoAtivo = "Todos";

    private final Span totalSociosLabel = new Span("0");
    private final Span receitaAnualLabel = new Span("0.00€");
    private final Span quotasPendentesLabel = new Span("0");
    private final Span alunosInativosMasSociosLabel = new Span("0");

    public SocioView(AlunoRepository alunoRepository, AlunoTurmaRepository alunoTurmaRepository,
            R2StorageService storageService) {
        this.alunoRepository = alunoRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.storageService = storageService;
        this.socioForm = new AlunoForm(alunoRepository, alunoTurmaRepository, storageService);
        this.socioForm.setOnSaveCallback(this::updateList);

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        injectStyles();
        H2 titulo = new H2("Gestão de Sócios");
        titulo.getStyle().set("margin-top", "0");

        Button btnNovo = new Button("+ Novo Sócio", e -> {
            socioForm.abrir(null);
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

    /** Carrega do repositório os alunos marcados como sócio (isSocio()) do estúdio atual. */
    private void updateList() {
        Studio studio = TenantContext.getCurrentStudio();
        List<Aluno> socios = (studio != null ? alunoRepository.findAllByStudio(studio) : alunoRepository.findAll())
                .stream()
                .filter(Aluno::isSocio)
                .collect(Collectors.toList());
        dataProvider = new ListDataProvider<>(socios);
        grid.setDataProvider(dataProvider);
        aplicarFiltros();
    }

    private void injectStyles() {
        String styles = ".stat-card { background: white; padding: 20px; border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); border: 1px solid #eee; min-width: 200px; flex-grow: 1; }"
                + ".stat-title { color: #7f8c8d; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 1px; font-weight: 600; }"
                + ".stat-value { font-size: 1.6rem; font-weight: 800; display: block; margin-top: 5px; font-family: 'Urbanist'; }"
                + ".quota-badge { padding: 8px 12px; border-radius: 10px; font-size: 0.85rem; line-height: 1.4; display: flex; flex-direction: column; align-items: center; min-width: 140px; cursor: pointer; transition: all 0.2s; }"
                + ".quota-badge:hover { transform: scale(1.05); filter: brightness(0.95); }"
                + ".quota-paid { background: #E6FFFA; color: #2C7A7B; border: 1px solid #B2F5EA; }"
                + ".quota-expired { background: #FFF5F5; color: #C53030; border: 1px solid #FEB2B2; }"
                + ".status-badge { padding: 4px 10px; border-radius: 8px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; }"
                + ".status-ok { background: #E8F5E9; color: #2E7D32; border: 1px solid #C8E6C9; }"
                + ".status-off { background: #c50724; color: #ffffff; border: 1px solid #ffcdd2; }" // Corrigido para
                                                                                                    // vermelho pastel
                                                                                                    // acessível
                + ".status-aluno-off { background: #c50724; color: #ffffff; border: 1px solid #ffe0b2; }";

        UI.getCurrent().getElement().executeJs(
                "const s = document.createElement('style'); s.textContent = $0; document.head.appendChild(s);", styles);
    }

    private Component criarStatsCards() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.getStyle().set("margin", "15px 0");

        layout.add(
                criarCard("Nº Sócios", totalSociosLabel, "#2D3436", VaadinIcon.USERS),
                criarCard("Receita Quotas (Anual)", receitaAnualLabel, "#1E8E3E", VaadinIcon.MONEY),
                criarCard("Quotas Expiradas", quotasPendentesLabel, "#D93025", VaadinIcon.CALENDAR_CLOCK),
                criarCard("Sócios (Alunos Off)", alunosInativosMasSociosLabel, "#7f8c8d", VaadinIcon.USER_CLOCK));
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

        grid.addComponentColumn(socio -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create(), e -> socioForm.abrirFormulario(socio));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            return editBtn;
        }).setHeader("").setWidth("70px").setFlexGrow(0).setFrozen(true);

        grid.addColumn(Aluno::getNumeroSocio).setHeader("Nº").setSortable(true).setWidth("80px").setFlexGrow(0);

        grid.addColumn(new ComponentRenderer<>(socio -> {
            HorizontalLayout row = new HorizontalLayout();
            row.setAlignItems(Alignment.CENTER);

            Image img;
            if (socio.getFotoChave() != null && !socio.getFotoChave().isBlank()) {
                String url = storageService.gerarUrlTemporario(socio.getFotoChave(), Duration.ofHours(2));
                img = new Image(url, "Foto de " + socio.getNomeCompleto());
            } else {
                img = new Image("images/avatar-default.png", "Avatar padrão");
            }

            img.setWidth("35px");
            img.setHeight("35px");
            img.getStyle().set("border-radius", "50%").set("object-fit", "cover");

            Span name = new Span(socio.getNomeCompleto());
            row.add(img, name);
            return row;
        })).setHeader("NOME COMPLETO").setSortable(true).setFlexGrow(1);

        // COLUNA ESTADO QUOTAS COM MODAL
        grid.addComponentColumn(socio -> {
            boolean emDia = isQuotaEmDia(socio);
            Div badge = new Div();
            badge.addClassName("quota-badge");
            badge.addClassName(emDia ? "quota-paid" : "quota-expired");

            Span p = new Span("PAGO: "
                    + (socio.getDataQuotaPagamento() != null ? socio.getDataQuotaPagamento().format(fmt) : "---"));
            p.getStyle().set("font-size", "0.65rem").set("font-weight", "800");
            Span e = new Span("EXP: "
                    + (socio.getDataExpiracaoQuota() != null ? socio.getDataExpiracaoQuota().format(fmt) : "---"));

            badge.add(p, e);
            badge.addClickListener(event -> abrirModalPagamento(socio));
            return badge;
        }).setHeader("QUOTAS").setAutoWidth(true).setTextAlign(ColumnTextAlign.CENTER);

        // BADGES MODERNOS STATUS
        grid.addComponentColumn(socio -> {
            HorizontalLayout status = new HorizontalLayout();
            Span sBadge = new Span(socio.isSocioAtivo() ? "SÓCIO OK" : "SÓCIO OFF");
            sBadge.addClassName("status-badge");
            sBadge.addClassName(socio.isSocioAtivo() ? "status-ok" : "status-off");

            Span aBadge = new Span(socio.isAtivo() ? "ALUNO OK" : "ALUNO OFF");
            aBadge.addClassName("status-badge");
            aBadge.addClassName(socio.isAtivo() ? "status-ok" : "status-aluno-off");

            status.add(sBadge, aBadge);
            return status;
        }).setHeader("ESTADO ATUAL").setAutoWidth(true);

        // --- GERAÇÃO E ALINHAMENTO DA BARRA DE FILTROS ---
        HeaderRow filterRow = grid.appendHeaderRow();

        TextField fNum = criarFiltroTexto("num");
        fNum.setPlaceholder("Nº...");
        filterRow.getCell(grid.getColumns().get(1)).setComponent(fNum);

        TextField fNome = criarFiltroTexto("nome");
        fNome.setPlaceholder("Nome...");
        filterRow.getCell(grid.getColumns().get(2)).setComponent(fNome);

        ComboBox<String> qCombo = new ComboBox<>();
        qCombo.setItems("Todas", "Pagas", "Expiradas");
        qCombo.setValue("Todas");
        qCombo.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
        qCombo.setWidthFull();
        qCombo.addValueChangeListener(e -> {
            filtroQuotas = e.getValue() != null ? e.getValue() : "Todas";
            aplicarFiltros();
        });
        filterRow.getCell(grid.getColumns().get(3)).setComponent(qCombo);

        // Contentor perfeitamente alinhado com as duas badges da coluna "ESTADO ATUAL"
        HorizontalLayout statusFiltersLayout = new HorizontalLayout();
        statusFiltersLayout.setSpacing(true);
        statusFiltersLayout.setPadding(false);
        statusFiltersLayout.getStyle().set("padding-left", "5px");
        statusFiltersLayout.setWidthFull();

        ComboBox<String> socioStatusCombo = new ComboBox<>();
        socioStatusCombo.setItems("Todos", "Sócio OK", "Sócio OFF");
        socioStatusCombo.setValue("Todos");
        socioStatusCombo.setPlaceholder("Sócio...");
        socioStatusCombo.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
        socioStatusCombo.setWidth("110px");
        socioStatusCombo.getStyle().set("flex-shrink", "0");
        socioStatusCombo.addValueChangeListener(e -> {
            filtroSocioAtivo = e.getValue() != null ? e.getValue() : "Todos";
            aplicarFiltros();
        });

        ComboBox<String> alunoStatusCombo = new ComboBox<>();
        alunoStatusCombo.setItems("Todos", "Aluno OK", "Aluno OFF");
        alunoStatusCombo.setValue("Todos");
        alunoStatusCombo.setPlaceholder("Aluno...");
        alunoStatusCombo.addThemeVariants(ComboBoxVariant.LUMO_SMALL);
        alunoStatusCombo.setWidth("110px");
        alunoStatusCombo.getStyle().set("flex-shrink", "0");
        alunoStatusCombo.addValueChangeListener(e -> {
            filtroAlunoAtivo = e.getValue() != null ? e.getValue() : "Todos";
            aplicarFiltros();
        });

        statusFiltersLayout.add(socioStatusCombo, alunoStatusCombo);
        filterRow.getCell(grid.getColumns().get(4)).setComponent(statusFiltersLayout);
    }

    private void abrirModalPagamento(Aluno socio) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Registar Pagamento - " + socio.getNomeCompleto());

        VerticalLayout layout = new VerticalLayout();
        DatePicker dataPagamento = new DatePicker("Data de Pagamento");
        dataPagamento.setValue(LocalDate.now());
        dataPagamento.setWidthFull();

        Span info = new Span("Ao confirmar, a expiração será definida automaticamente para 1 ano após o pagamento.");
        info.getStyle().set("font-size", "0.85rem").set("color", "var(--lumo-secondary-text-color)");

        Button confirmar = new Button("Confirmar Pagamento", VaadinIcon.CHECK.create(), e -> {
            if (dataPagamento.getValue() != null) {
                socio.setDataQuotaPagamento(dataPagamento.getValue());
                socio.setDataExpiracaoQuota(dataPagamento.getValue().plusYears(1));
                alunoRepository.save(socio);
                Notification.show("Quota atualizada para " + socio.getNomeCompleto())
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                aplicarFiltros();
                dialog.close();
            }
        });
        confirmar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirmar.setWidthFull();

        layout.add(dataPagamento, info, confirmar);
        dialog.add(layout);
        dialog.open();
    }

    private TextField criarFiltroTexto(String campo) {
        TextField tf = new TextField();
        tf.addThemeVariants(TextFieldVariant.LUMO_SMALL);
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

        dataProvider.setFilter(s -> {
            boolean matchNum = filtrosTexto.getOrDefault("num", "").isEmpty()
                    || String.valueOf(s.getNumeroSocio()).contains(filtrosTexto.get("num"));

            boolean matchNome = filtrosTexto.getOrDefault("nome", "").isEmpty()
                    || Normalizer.normalize(s.getNomeCompleto().toLowerCase(), Normalizer.Form.NFD)
                            .contains(filtrosTexto.get("nome").toLowerCase());

            boolean matchQuotas = "Todas".equals(filtroQuotas)
                    || ("Pagas".equals(filtroQuotas) ? isQuotaEmDia(s) : !isQuotaEmDia(s));

            // Filtro Cruzado: Validação de Sócio Ativo / Inativo
            boolean matchSocio = "Todos".equals(filtroSocioAtivo)
                    || ("Sócio OK".equals(filtroSocioAtivo) && s.isSocioAtivo())
                    || ("Sócio OFF".equals(filtroSocioAtivo) && !s.isSocioAtivo());

            // Filtro Cruzado: Validação de Aluno Ativo / Inativo
            boolean matchAluno = "Todos".equals(filtroAlunoAtivo)
                    || ("Aluno OK".equals(filtroAlunoAtivo) && s.isAtivo())
                    || ("Aluno OFF".equals(filtroAlunoAtivo) && !s.isAtivo());

            return matchNum && matchNome && matchQuotas && matchSocio && matchAluno;
        });
        updateDashboard();
    }

    private void updateDashboard() {
        List<Aluno> filtrados = dataProvider.getItems().stream()
                .filter(s -> dataProvider.getFilter() == null || dataProvider.getFilter().test(s))
                .collect(Collectors.toList());
        long pagos = filtrados.stream().filter(this::isQuotaEmDia).count();
        totalSociosLabel.setText(String.valueOf(filtrados.size()));
        receitaAnualLabel.setText(String.format("%.2f€", pagos * 12.0));
        quotasPendentesLabel.setText(String.valueOf(filtrados.stream().filter(s -> !isQuotaEmDia(s)).count()));
        alunosInativosMasSociosLabel.setText(String.valueOf(filtrados.stream()
                .filter(s -> !s.isAtivo() && s.isSocio()).count()));
    }

    private boolean isQuotaEmDia(Aluno a) {
        return a.getDataExpiracaoQuota() != null && !a.getDataExpiracaoQuota().isBefore(java.time.LocalDate.now());
    }

    private void vincularAtualizacaoAoFechar() {
        this.getUI().ifPresent(ui -> ui.access(this::updateList));
    }
}
