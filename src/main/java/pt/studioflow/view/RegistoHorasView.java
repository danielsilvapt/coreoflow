package pt.studioflow.view;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Professor;
import pt.studioflow.model.RegistoHoras;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.model.User;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.RegistoHorasRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.UserRepository;
import pt.studioflow.service.RelatorioHorasService;

@Route(value = "registo-horas", layout = MainLayout.class)
@PageTitle("Registo de Horas | CoreoFlow")
@RolesAllowed({ "ADMIN", "PROF" })
public class RegistoHorasView extends VerticalLayout {

    private final RegistoHorasRepository registoHorasRepository;
    private final ProfessorRepository professorRepository;
    private final TurmaRepository turmaRepository;
    private final RelatorioHorasService relatorioService;
    private final UserRepository userRepository;

    private Grid<RegistoHoras> grid;
    private List<RegistoHoras> registos;
    private final DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("dd MMM (EEE)", new Locale("pt", "PT"));
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    private final Span labelTotalHoras = new Span("0.0h");
    private final Span labelTotalAulas = new Span("0");
    private final Span labelRegulares = new Span("0");
    private final Span labelEnsaios = new Span("0");
    private final Span labelPrivadas = new Span("0");

    private final ComboBox<Professor> professorFiltro = new ComboBox<>("Professor");
    private final ComboBox<String> mesFiltro = new ComboBox<>("Mês");
    private final ComboBox<String> anoFiltro = new ComboBox<>("Ano");

    private Dialog registoDialog;
    private ComboBox<Professor> profComboDialog = new ComboBox<>("Professor");
    private ComboBox<String> turmaComboDialog = new ComboBox<>("Turma");
    private ComboBox<String> tipoComboDialog = new ComboBox<>("Tipo");
    private DatePicker dataPicker = new DatePicker("Data");
    private TimePicker inicioPicker = new TimePicker("Início");
    private TimePicker fimPicker = new TimePicker("Fim");
    private TextArea observacoesArea = new TextArea("Observações");
    private RegistoHoras registoSendoEditado;

    public RegistoHorasView(RegistoHorasRepository registoHorasRepository,
            TurmaRepository turmaRepository,
            ProfessorRepository professorRepository,
            RelatorioHorasService relatorioService,
            UserRepository userRepository) {
        this.registoHorasRepository = registoHorasRepository;
        this.turmaRepository = turmaRepository;
        this.professorRepository = professorRepository;
        this.relatorioService = relatorioService;
        this.userRepository = userRepository;

        // CORREÇÃO MOBILE: Removemos setSizeFull para evitar que a altura fique "presa"
        // no viewport
        setWidthFull();
        setMinHeight("100%");
        setPadding(true);
        setSpacing(true);
        setClassName("view-container-scrollable");

        injectStyles();
        registos = new java.util.ArrayList<>(); // será carregado em carregarRegistos() abaixo

        add(criarHeader(), criarStatsCards(), criarToolbarFiltros());

        grid = configurarGrid();
        add(grid);

        registos = carregarRegistos();

        configurarDialogRegisto();
        configurarFiltrosIniciais();
    }

    private List<RegistoHoras> carregarRegistos() {
        Studio studio = TenantContext.getCurrentStudio();
        return studio != null
                ? registoHorasRepository.findAllByStudio(studio)
                : registoHorasRepository.findAll();
    }

    private void injectStyles() {
        String styles =
                // Permite scroll natural na página inteira no mobile
                ".view-container-scrollable { overflow-y: auto; height: auto !important; } " +
                        "vaadin-grid { height: auto !important; min-height: 400px; } " +

                        ".dashboard-card { background: white; border-radius: 12px; padding: 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); border: 1px solid #f0f0f0; min-width: 160px; flex: 1; position: relative; }"
                        + ".card-label { font-size: 0.7rem; color: #808080; font-weight: bold; text-transform: uppercase; letter-spacing: 0.05em; }"
                        + ".card-value { font-size: 1.5rem; font-weight: 700; margin-top: 4px; }"
                        + ".card-icon { color: #f5f5f5; position: absolute; right: 12px; top: 12px; font-size: 20px; }"
                        + ".filter-bar { background: rgba(255,255,255,0.6); backdrop-filter: blur(10px); padding: 15px; border-radius: 12px; margin: 15px 0; border: 1px solid #e0e0e0; }"
                        + ".badge-tipo { font-size: 0.65rem; font-weight: bold; padding: 2px 8px; border-radius: 6px; background: #f0f2f5; color: #4b5563; text-transform: uppercase; }"
                        + ".glass-btn { background: rgba(255, 255, 255, 0.4) !important; backdrop-filter: blur(10px) !important; -webkit-backdrop-filter: blur(10px) !important; "
                        + "border: 1px solid rgba(255, 255, 255, 0.5) !important; box-shadow: 0 4px 10px rgba(0,0,0,0.05) !important; border-radius: 10px !important; transition: all 0.2s; }"
                        + ".glass-btn:hover { background: rgba(255, 255, 255, 0.7) !important; transform: scale(1.05); }"
                        + ".time-stepper-container { background: #f8fafc; border-radius: 16px; padding: 20px; border: 1px solid #e2e8f0; margin: 10px 0; "
                        + "display: flex; flex-direction: column; align-items: center; justify-content: center; width: 100%; }"
                        + ".time-display { font-size: 1.5rem; font-weight: 800; color: #1e293b; min-width: 80px; text-align: center; font-family: monospace; }"
                        + ".stepper-btn { background: white; border: 1px solid #cbd5e1; border-radius: 12px; width: 48px; height: 48px; cursor: pointer; }"
                        + ".duration-chip { background: #e0f2fe; color: #0369a1; border-radius: 20px; padding: 6px 16px; font-size: 0.85rem; font-weight: 700; border: none; cursor: pointer; }"
                        + "@media (max-width: 800px) { "
                        + "  .header-container { flex-direction: column; align-items: stretch !important; gap: 15px; } "
                        + "  .cards-wrapper { display: grid !important; grid-template-columns: 1fr 1fr !important; gap: 10px !important; width: 100% !important; } "
                        + "  .dashboard-card:first-child { grid-column: span 2 !important; border-left: 5px solid #1967D2 !important; background: linear-gradient(135deg, #fff 0%, #f8f9fa 100%) !important; } "
                        + "  .dashboard-card { min-width: 0 !important; } "
                        + "  .toolbar-responsiva { flex-direction: column; align-items: stretch !important; gap: 10px; } "
                        + "}";

        UI.getCurrent().getElement().executeJs(
                "const style = document.createElement('style'); style.textContent = $0; document.head.appendChild(style);",
                styles);
    }

    private Component criarHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.addClassName("header-container");
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        H2 titulo = new H2("Registo de Atividade");
        titulo.getStyle().set("margin", "0");

        Button btnNovo = ViewUtils.botaoNovo("Novo Lançamento", e -> prepararNovoRegisto());
        btnNovo.getStyle().set("height", "50px");

        Button btnRel = new Button("Relatório", VaadinIcon.PAPERPLANE.create(), e -> enviarRelatorioManual());
        btnRel.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
        btnRel.setVisible(isAdmin());

        if (VaadinSession.getCurrent().getBrowser().isAndroid() || VaadinSession.getCurrent().getBrowser().isIPhone()) {
            btnNovo.setWidthFull();
        }

        header.add(titulo, new HorizontalLayout(btnRel, btnNovo));
        return header;
    }

    private Component criarStatsCards() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.addClassName("cards-wrapper");
        layout.setSpacing(true);

        layout.add(
                criarCard("Horas Acum.", labelTotalHoras, "#1967D2", VaadinIcon.CLOCK),
                criarCard("Ativ. Total", labelTotalAulas, "#FF5D13", VaadinIcon.CALENDAR),
                criarCard("Regulares", labelRegulares, "#2980b9", VaadinIcon.ACADEMY_CAP),
                criarCard("Ensaios", labelEnsaios, "#c0392b", VaadinIcon.MUSIC),
                criarCard("Privadas", labelPrivadas, "#8e44ad", VaadinIcon.USER_CHECK));
        return layout;
    }

    private Div criarCard(String titulo, Span val, String color, VaadinIcon icon) {
        Div card = new Div();
        card.addClassName("dashboard-card");
        Span t = new Span(titulo);
        t.addClassName("card-label");
        val.addClassName("card-value");
        val.getStyle().set("color", color);
        Icon i = icon.create();
        i.addClassName("card-icon");
        card.add(t, val, i);
        return card;
    }

    private Component criarToolbarFiltros() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.addClassNames("filter-bar", "toolbar-responsiva");
        layout.setWidthFull();
        layout.setAlignItems(Alignment.END);

        professorFiltro.setItemLabelGenerator(Professor::getNome);
        professorFiltro.addValueChangeListener(e -> aplicarFiltros());
        professorFiltro.setEnabled(isAdmin());

        mesFiltro.setItems("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro");
        mesFiltro.addValueChangeListener(e -> aplicarFiltros());

        anoFiltro.addValueChangeListener(e -> aplicarFiltros());
        atualizarAnosFiltro();

        layout.add(professorFiltro, mesFiltro, anoFiltro);
        return layout;
    }

    private Grid<RegistoHoras> configurarGrid() {
        grid = new Grid<>(RegistoHoras.class, false);
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        // CORREÇÃO: Allheight-by-rows permite que a grid cresça com o conteúdo e o
        // scroll seja feito no layout pai
        grid.setAllRowsVisible(true);

        grid.addComponentColumn(r -> {
            Button edit = new Button(VaadinIcon.EDIT.create(), e -> prepararEdicao(r));
            edit.addClassName("glass-btn");
            Button del = new Button(VaadinIcon.TRASH.create(), e -> {
                registoHorasRepository.delete(r);
                registos.remove(r);
                aplicarFiltros();
            });
            del.addClassNames("glass-btn");
            del.getStyle().set("color", "#e11d48");
            boolean pode = isAdmin() || normalizar(r.getProfessor()).contains(normalizar(getFirstNameFromDatabase()));
            edit.setEnabled(pode);
            del.setEnabled(pode);
            return new HorizontalLayout(edit, del);
        }).setHeader("Ações").setAutoWidth(true).setFlexGrow(0);

        grid.addColumn(new ComponentRenderer<>(r -> {
            VerticalLayout layout = new VerticalLayout();
            Span data = new Span(r.getInicio().format(dtFormatter));
            data.getStyle().set("font-weight", "bold").set("font-size", "0.9rem");
            Span horas = new Span(r.getInicio().format(timeFormatter) + " - " + r.getFim().format(timeFormatter));
            horas.getStyle().set("font-size", "0.75rem").set("color", "#7f8c8d");
            layout.add(data, horas);
            layout.setPadding(false);
            layout.setSpacing(false);
            return layout;
        })).setHeader("Data / Hora").setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(r -> {
            VerticalLayout l = new VerticalLayout();
            Span p = new Span(r.getProfessor());
            p.getStyle().set("font-weight", "600").set("font-size", "0.85rem");
            HorizontalLayout s = new HorizontalLayout();
            s.setAlignItems(Alignment.CENTER);
            Span t = new Span(r.getTurma());
            t.getStyle().set("font-size", "0.75rem").set("color", "#FF5D13");
            Span b = new Span(r.getTipoAtividade());
            b.addClassName("badge-tipo");
            s.add(t, b);
            l.add(p, s);
            l.setPadding(false);
            l.setSpacing(false);
            return l;
        })).setHeader("Professor / Detalhes").setFlexGrow(1);

        grid.addColumn(r -> String.format("%.2f h", calcularHoras(r)))
                .setHeader("Dur.").setAutoWidth(true).setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);

        return grid;
    }

    private void configurarDialogRegisto() {
        registoDialog = new Dialog();
        registoDialog.setHeaderTitle("Detalhes do Registo");
        registoDialog.setWidth("95%");
        registoDialog.setMaxWidth("480px");

        FormLayout form = new FormLayout();
        profComboDialog.setItemLabelGenerator(Professor::getNome);
        profComboDialog.addValueChangeListener(e -> {
            if (e.getValue() != null)
                carregarTurmasDialog(e.getValue());
        });
        turmaComboDialog.addValueChangeListener(e -> tentarAutoPreencherHoras());
        dataPicker.addValueChangeListener(e -> tentarAutoPreencherHoras());
        tipoComboDialog.setItems("Aula regular", "Ensaio", "Aula privada", "Workshop");

        VerticalLayout timeXpLayout = new VerticalLayout();
        timeXpLayout.setPadding(false);
        timeXpLayout.addClassName("time-stepper-container");
        timeXpLayout.setDefaultHorizontalComponentAlignment(Alignment.CENTER);

        HorizontalLayout inicioRow = criarStepperXPTO("Início Aula", inicioPicker);
        HorizontalLayout fimRow = criarStepperXPTO("Fim Aula", fimPicker);

        HorizontalLayout durationChips = new HorizontalLayout();
        durationChips.setWidthFull();
        durationChips.setJustifyContentMode(JustifyContentMode.CENTER);
        Button chip1h = new Button("1h", e -> {
            if (inicioPicker.getValue() != null)
                fimPicker.setValue(inicioPicker.getValue().plusHours(1));
        });
        chip1h.addClassName("duration-chip");
        Button chip1h30 = new Button("1h30", e -> {
            if (inicioPicker.getValue() != null)
                fimPicker.setValue(inicioPicker.getValue().plusMinutes(90));
        });
        chip1h30.addClassName("duration-chip");
        durationChips.add(new Span("Duração:"), chip1h, chip1h30);
        durationChips.setAlignItems(Alignment.CENTER);
        timeXpLayout.add(inicioRow, fimRow, durationChips);

        form.add(profComboDialog, turmaComboDialog, tipoComboDialog, dataPicker, timeXpLayout, observacoesArea);
        form.setColspan(timeXpLayout, 2);
        form.setColspan(observacoesArea, 2);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));

        Button btnGuardar = new Button("Confirmar", VaadinIcon.CHECK.create(), e -> salvarRegisto());
        btnGuardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        btnGuardar.setWidthFull();
        btnGuardar.getStyle().set("height", "55px");

        Button btnFechar = new Button("Cancelar", e -> registoDialog.close());
        btnFechar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        btnFechar.setWidthFull();

        registoDialog.add(new VerticalLayout(form, btnGuardar, btnFechar));
    }

    private HorizontalLayout criarStepperXPTO(String label, TimePicker picker) {
        picker.setStep(Duration.ofMinutes(15));
        picker.getStyle().set("display", "none");
        Span display = new Span("00:00");
        display.addClassName("time-display");
        picker.addValueChangeListener(e -> {
            if (e.getValue() != null)
                display.setText(e.getValue().toString());
        });
        Button bMinus = new Button(VaadinIcon.MINUS.create(), e -> {
            LocalTime v = picker.getValue() != null ? picker.getValue() : LocalTime.of(18, 0);
            picker.setValue(v.minusMinutes(15));
        });
        bMinus.addClassName("stepper-btn");
        Button bPlus = new Button(VaadinIcon.PLUS.create(), e -> {
            LocalTime v = picker.getValue() != null ? picker.getValue() : LocalTime.of(18, 0);
            picker.setValue(v.plusMinutes(15));
        });
        bPlus.addClassName("stepper-btn");
        VerticalLayout v = new VerticalLayout(new Span(label), new HorizontalLayout(bMinus, display, bPlus));
        v.setPadding(false);
        v.setSpacing(false);
        v.setAlignItems(Alignment.CENTER);
        HorizontalLayout wrap = new HorizontalLayout(v);
        wrap.setWidthFull();
        wrap.setJustifyContentMode(JustifyContentMode.CENTER);
        return wrap;
    }

    private List<Turma> getTurmasDoStudio() {
        Studio studio = TenantContext.getCurrentStudio();
        return studio != null ? turmaRepository.findAllByStudio(studio) : turmaRepository.findAllComplete();
    }

    private List<Professor> getProfessoresDoStudio() {
        Studio studio = TenantContext.getCurrentStudio();
        return studio != null ? professorRepository.findAllByStudio(studio) : professorRepository.findAll();
    }

    private void tentarAutoPreencherHoras() {
        if (registoSendoEditado != null)
            return;
        String tName = turmaComboDialog.getValue();
        LocalDate d = dataPicker.getValue();
        if (tName != null && d != null) {
            getTurmasDoStudio().stream().filter(t -> t.getDescricao().equals(tName)).findFirst()
                    .ifPresent(turma -> {
                        turma.getAulas().stream().filter(a -> a.getDia().equals(d.getDayOfWeek())).findFirst()
                                .ifPresent(aula -> {
                                    inicioPicker.setValue(aula.getHoraInicio());
                                    fimPicker.setValue(aula.getHoraFim() != null ? aula.getHoraFim()
                                            : aula.getHoraInicio().plusHours(1));
                                });
                    });
        }
    }

    private void carregarTurmasDialog(Professor prof) {
        List<String> t = getTurmasDoStudio().stream()
                .filter(x -> x.getProfessor() != null && x.getProfessor().getId().equals(prof.getId()))
                .map(Turma::getDescricao).sorted().collect(Collectors.toList());
        turmaComboDialog.setItems(t);
        turmaComboDialog.setEnabled(true);
    }

    private void salvarRegisto() {
        if (inicioPicker.getValue() == null || fimPicker.getValue() == null || turmaComboDialog.getValue() == null
                || profComboDialog.getValue() == null) {
            Notification.show("Faltam dados.").addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        RegistoHoras r = (registoSendoEditado != null) ? registoSendoEditado : new RegistoHoras();
        r.setProfessor(profComboDialog.getValue().getNome());
        r.setTurma(turmaComboDialog.getValue());
        r.setTipoAtividade(tipoComboDialog.getValue());
        r.setObservacoes(observacoesArea.getValue());
        LocalDate d = dataPicker.getValue();
        r.setInicio(LocalDateTime.of(d, inicioPicker.getValue()));
        r.setFim(LocalDateTime.of(d, fimPicker.getValue()));
        String m = d.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "PT"));
        r.setMes(m.substring(0, 1).toUpperCase() + m.substring(1).toLowerCase());
        r.setMesNumero(d.getMonthValue());
        r.setAno(d.getYear());
        if (r.getStudio() == null) {
            r.setStudio(TenantContext.getCurrentStudio());
        }
        registoHorasRepository.save(r);
        if (registoSendoEditado == null)
            registos.add(r);
        aplicarFiltros();
        registoDialog.close();
    }

    private void aplicarFiltros() {
        String pName = (professorFiltro.getValue() != null) ? professorFiltro.getValue().getNome() : "";
        String m = mesFiltro.getValue();
        String a = anoFiltro.getValue();
        String me = normalizar(getFirstNameFromDatabase());
        List<RegistoHoras> filtrados = registos.stream()
                .filter(r -> isAdmin() ? (pName.isEmpty() || r.getProfessor().equals(pName))
                        : normalizar(r.getProfessor()).contains(me))
                .filter(r -> m == null || r.getMes().equalsIgnoreCase(m))
                .filter(r -> a == null || String.valueOf(r.getAno()).equals(a))
                .sorted(Comparator.comparing(RegistoHoras::getInicio).reversed())
                .collect(Collectors.toList());
        grid.setItems(filtrados);
        double th = filtrados.stream().mapToDouble(this::calcularHoras).sum();
        labelTotalHoras.setText(String.format("%.2f h", th));
        labelTotalAulas.setText(String.valueOf(filtrados.size()));
        labelRegulares.setText(String.valueOf(
                filtrados.stream().filter(r -> "Aula regular".equalsIgnoreCase(r.getTipoAtividade())).count()));
        labelEnsaios.setText(String
                .valueOf(filtrados.stream().filter(r -> "Ensaio".equalsIgnoreCase(r.getTipoAtividade())).count()));
        labelPrivadas.setText(String.valueOf(
                filtrados.stream().filter(r -> "Aula privada".equalsIgnoreCase(r.getTipoAtividade())).count()));
    }

    private void configurarFiltrosIniciais() {
        LocalDate now = LocalDate.now();
        String m = now.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "PT"));
        mesFiltro.setValue(m.substring(0, 1).toUpperCase() + m.substring(1).toLowerCase());
        anoFiltro.setValue(String.valueOf(now.getYear()));
        List<Professor> ps = getProfessoresDoStudio();
        professorFiltro.setItems(ps);
        ps.stream().filter(x -> normalizar(x.getNome()).contains(normalizar(getFirstNameFromDatabase()))).findFirst()
                .ifPresent(professorFiltro::setValue);
        aplicarFiltros();
    }

    private String getFirstNameFromDatabase() {
        Object p = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String l = (p instanceof UserDetails) ? ((UserDetails) p).getUsername() : p.toString();
        return userRepository.findByPrincipalName(l).map(User::getFirstName).orElse("");
    }

    private String normalizar(String t) {
        return t == null ? ""
                : Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private void atualizarAnosFiltro() {
        List<String> as = registos.stream().map(r -> String.valueOf(r.getAno())).distinct()
                .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        if (as.isEmpty())
            as.add(String.valueOf(LocalDate.now().getYear()));
        anoFiltro.setItems(as);
    }

    private double calcularHoras(RegistoHoras r) {
        return Duration.between(r.getInicio(), r.getFim()).toMinutes() / 60.0;
    }

    private void enviarRelatorioManual() {
        try {
            relatorioService.gerarEEnviarRelatorioManual(LocalDate.now());
            Notification.show("Enviado!");
        } catch (Exception ex) {
        }
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN"));
    }

    private void prepararNovoRegisto() {
        registoSendoEditado = null;
        dataPicker.setValue(LocalDate.now());
        observacoesArea.clear();
        turmaComboDialog.clear();
        turmaComboDialog.setEnabled(false);
        inicioPicker.clear();
        fimPicker.clear();
        tipoComboDialog.clear();
        List<Professor> ps = getProfessoresDoStudio();
        profComboDialog.setItems(ps);
        ps.stream().filter(x -> normalizar(x.getNome()).contains(normalizar(getFirstNameFromDatabase()))).findFirst()
                .ifPresent(profComboDialog::setValue);
        registoDialog.open();
    }

    private void prepararEdicao(RegistoHoras r) {
        registoSendoEditado = r;
        dataPicker.setValue(r.getInicio().toLocalDate());
        inicioPicker.setValue(r.getInicio().toLocalTime());
        fimPicker.setValue(r.getFim().toLocalTime());
        observacoesArea.setValue(r.getObservacoes() != null ? r.getObservacoes() : "");
        tipoComboDialog.setValue(r.getTipoAtividade());
        List<Professor> ps = getProfessoresDoStudio();
        profComboDialog.setItems(ps);
        ps.stream().filter(x -> normalizar(x.getNome()).equals(normalizar(r.getProfessor()))).findFirst()
                .ifPresent(profComboDialog::setValue);
        carregarTurmasDialog(profComboDialog.getValue());
        turmaComboDialog.setValue(r.getTurma());
        registoDialog.open();
    }
}
