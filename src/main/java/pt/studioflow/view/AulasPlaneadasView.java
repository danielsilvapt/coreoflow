package pt.studioflow.view;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aula;
import pt.studioflow.model.MarcacaoSala;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Sala;
import pt.studioflow.model.Studio;
import pt.studioflow.model.SumarioAula;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AulaRepository;
import pt.studioflow.repository.MarcacaoSalaRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.SumarioAulaRepository;
import pt.studioflow.repository.TurmaRepository;

/**
 * Vista de administração para consultar o planeamento de aulas de todos os professores e
 * turmas: expande as aulas regulares e as marcações pontuais aprovadas num intervalo, cruza
 * com o {@link SumarioAula} de cada ocorrência e mostra o estado (por planear / planeada /
 * sumário enviado), com filtros por período, professor, turma, estado e pesquisa livre.
 */
@PageTitle("Aulas Planeadas | CoreoFlow")
@Route(value = "aulas-planeadas", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AulasPlaneadasView extends VerticalLayout {

    private static final Locale PT = new Locale("pt", "PT");
    private static final DateTimeFormatter FMT_DIA = DateTimeFormatter.ofPattern("EEE, d MMM", PT);
    private static final DateTimeFormatter FMT_DIA_LONGO = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM yyyy", PT);
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final String P_SEMANA = "Esta semana";
    private static final String P_PROX_SEMANA = "Próxima semana";
    private static final String P_MES = "Este mês";
    private static final String P_PROX_MES = "Próximo mês";
    private static final String P_PERSONALIZADO = "Personalizado";

    private final AulaRepository aulaRepository;
    private final MarcacaoSalaRepository marcacaoRepository;
    private final SumarioAulaRepository sumarioRepository;
    private final ProfessorRepository professorRepository;
    private final TurmaRepository turmaRepository;

    private final ComboBox<String> comboPeriodo = new ComboBox<>("Período");
    private final DatePicker dpInicio = new DatePicker("De");
    private final DatePicker dpFim = new DatePicker("Até");
    private final ComboBox<Professor> comboProf = new ComboBox<>("Professor");
    private final ComboBox<Turma> comboTurma = new ComboBox<>("Turma");
    private final ComboBox<Estado> comboEstado = new ComboBox<>("Estado");
    private final TextField txtPesquisa = new TextField();

    private final Div statsContainer = new Div();
    private final Div resumoContainer = new Div();
    private final Details resumoDetails = new Details();
    private final Grid<Ocorrencia> grid = new Grid<>();

    private final Map<String, Professor> profPorNome = new HashMap<>();
    private List<Ocorrencia> ocorrenciasPeriodo = new ArrayList<>();
    private boolean ajustandoDatas = false;

    public AulasPlaneadasView(AulaRepository aulaRepository, MarcacaoSalaRepository marcacaoRepository,
            SumarioAulaRepository sumarioRepository, ProfessorRepository professorRepository,
            TurmaRepository turmaRepository) {
        this.aulaRepository = aulaRepository;
        this.marcacaoRepository = marcacaoRepository;
        this.sumarioRepository = sumarioRepository;
        this.professorRepository = professorRepository;
        this.turmaRepository = turmaRepository;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        addClassName("cf-page");

        H2 titulo = new H2("Aulas Planeadas");
        Span sub = new Span("Planeamento de todas as turmas e professores num só sítio.");
        sub.addClassName("cf-page-subtitle");
        Div cabecalho = new Div(new Div(titulo, sub));
        cabecalho.addClassName("cf-page-header");
        add(cabecalho);

        add(criarBarraFiltros());

        statsContainer.addClassName("cf-stats");
        add(statsContainer);

        resumoContainer.getStyle().set("display", "flex").set("flex-wrap", "wrap").set("gap", "10px")
                .set("padding-top", "6px");
        resumoDetails.setSummaryText("Resumo por professor");
        resumoDetails.setOpened(true);
        resumoDetails.add(resumoContainer);
        resumoDetails.addClassName("cf-card");
        resumoDetails.setWidthFull();
        add(resumoDetails);

        configurarGrid();
        add(grid);
        expand(grid);

        popularFiltrosEstaticos();
        comboPeriodo.setValue(P_MES); // dispara aplicarPeriodo() -> recarregar()
    }

    // ================= BARRA DE FILTROS =================

    private Div criarBarraFiltros() {
        comboPeriodo.setItems(P_SEMANA, P_PROX_SEMANA, P_MES, P_PROX_MES, P_PERSONALIZADO);
        comboPeriodo.setWidth("170px");
        comboPeriodo.setAllowCustomValue(false);
        comboPeriodo.addValueChangeListener(e -> aplicarPeriodo());

        dpInicio.setLocale(PT);
        dpFim.setLocale(PT);
        dpInicio.setWidth("150px");
        dpFim.setWidth("150px");
        dpInicio.addValueChangeListener(e -> onDataManualAlterada());
        dpFim.addValueChangeListener(e -> onDataManualAlterada());

        comboProf.setItemLabelGenerator(Professor::getNome);
        comboProf.setPlaceholder("Todos");
        comboProf.setClearButtonVisible(true);
        comboProf.setWidth("200px");
        comboProf.addValueChangeListener(e -> aplicarFiltros());

        comboTurma.setItemLabelGenerator(Turma::getDescricao);
        comboTurma.setPlaceholder("Todas");
        comboTurma.setClearButtonVisible(true);
        comboTurma.setWidth("220px");
        comboTurma.addValueChangeListener(e -> aplicarFiltros());

        comboEstado.setItems(Estado.values());
        comboEstado.setItemLabelGenerator(est -> est.icone + " " + est.label);
        comboEstado.setPlaceholder("Todos");
        comboEstado.setClearButtonVisible(true);
        comboEstado.setWidth("180px");
        comboEstado.addValueChangeListener(e -> aplicarFiltros());

        txtPesquisa.setPlaceholder("Pesquisar turma, sala...");
        txtPesquisa.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        txtPesquisa.setClearButtonVisible(true);
        txtPesquisa.setValueChangeMode(ValueChangeMode.LAZY);
        txtPesquisa.setWidth("240px");
        txtPesquisa.getStyle().set("align-self", "flex-end");
        txtPesquisa.addValueChangeListener(e -> aplicarFiltros());

        Button limpar = new Button("Limpar filtros", new Icon(VaadinIcon.ERASER), e -> limparFiltros());
        limpar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        limpar.getStyle().set("align-self", "flex-end");

        HorizontalLayout linha = new HorizontalLayout(comboPeriodo, dpInicio, dpFim, comboProf, comboTurma,
                comboEstado, txtPesquisa, limpar);
        linha.setAlignItems(FlexComponent.Alignment.END);
        linha.getStyle().set("flex-wrap", "wrap").set("gap", "12px");

        Div card = new Div(linha);
        card.addClassName("cf-card");
        card.setWidthFull();
        return card;
    }

    private void popularFiltrosEstaticos() {
        Studio studio = TenantContext.getCurrentStudio();
        List<Professor> profs = (studio != null ? professorRepository.findAllByStudio(studio)
                : professorRepository.findAll()).stream()
                .filter(p -> p.getNome() != null && !p.getNome().isBlank())
                .sorted(Comparator.comparing(Professor::getNome, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        comboProf.setItems(profs);
        profPorNome.clear();
        profs.forEach(p -> profPorNome.put(normalizar(p.getNome()), p));

        comboTurma.setItems(studio != null ? turmaRepository.findAllByStudio(studio) : turmaRepository.findAll());
    }

    private void limparFiltros() {
        comboProf.clear();
        comboTurma.clear();
        comboEstado.clear();
        txtPesquisa.clear();
        comboPeriodo.setValue(P_MES);
    }

    // ================= PERÍODO =================

    private void aplicarPeriodo() {
        String p = comboPeriodo.getValue();
        if (p == null) {
            return;
        }
        boolean personalizado = P_PERSONALIZADO.equals(p);
        dpInicio.setReadOnly(!personalizado);
        dpFim.setReadOnly(!personalizado);

        if (!personalizado) {
            LocalDate hoje = LocalDate.now();
            LocalDate ini;
            LocalDate fim;
            switch (p) {
                case P_SEMANA -> {
                    ini = hoje.with(DayOfWeek.MONDAY);
                    fim = ini.plusDays(6);
                }
                case P_PROX_SEMANA -> {
                    ini = hoje.with(DayOfWeek.MONDAY).plusWeeks(1);
                    fim = ini.plusDays(6);
                }
                case P_PROX_MES -> {
                    YearMonth ym = YearMonth.from(hoje).plusMonths(1);
                    ini = ym.atDay(1);
                    fim = ym.atEndOfMonth();
                }
                default -> { // P_MES
                    YearMonth ym = YearMonth.from(hoje);
                    ini = ym.atDay(1);
                    fim = ym.atEndOfMonth();
                }
            }
            ajustandoDatas = true;
            dpInicio.setValue(ini);
            dpFim.setValue(fim);
            ajustandoDatas = false;
        }
        recarregar();
    }

    private void onDataManualAlterada() {
        if (ajustandoDatas) {
            return;
        }
        if (!P_PERSONALIZADO.equals(comboPeriodo.getValue())) {
            comboPeriodo.setValue(P_PERSONALIZADO);
            return;
        }
        recarregar();
    }

    // ================= CARREGAMENTO =================

    private void recarregar() {
        LocalDate ini = dpInicio.getValue();
        LocalDate fim = dpFim.getValue();
        if (ini == null || fim == null || fim.isBefore(ini)) {
            ocorrenciasPeriodo = new ArrayList<>();
        } else {
            ocorrenciasPeriodo = gerarOcorrencias(ini, fim);
        }
        aplicarFiltros();
    }

    private List<Ocorrencia> gerarOcorrencias(LocalDate inicio, LocalDate fim) {
        Studio studio = TenantContext.getCurrentStudio();

        Map<String, SumarioAula> idx = new HashMap<>();
        for (SumarioAula s : (studio != null ? sumarioRepository.findByStudioAndDataBetween(studio, inicio, fim)
                : sumarioRepository.findByDataBetween(inicio, fim))) {
            if (s.getTurma() != null && s.getData() != null) {
                idx.put(chave(s.getTurma().getId(), s.getData(), s.getHoraInicio()), s);
            }
        }

        List<Ocorrencia> lista = new ArrayList<>();

        List<Aula> aulas = (studio != null ? aulaRepository.findByStudioComHorario(studio)
                : aulaRepository.findAllComHorario()).stream()
                .filter(a -> a.getTurma() != null && a.getDia() != null)
                .toList();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) {
            final LocalDate dia = d;
            for (Aula a : aulas) {
                if (a.getDia() != dia.getDayOfWeek()) {
                    continue;
                }
                Turma t = a.getTurma();
                String prof = a.getProfessorEfetivo() != null ? a.getProfessorEfetivo().getNome() : "—";
                SumarioAula s = idx.get(chave(t.getId(), dia, a.getHoraInicio()));
                lista.add(new Ocorrencia(dia, a.getHoraInicio(), a.getHoraFim(), t, prof, a.getSala(),
                        "REGULAR", s));
            }
        }

        for (MarcacaoSala m : (studio != null ? marcacaoRepository.findByStudioAndDataBetween(studio, inicio, fim)
                : marcacaoRepository.findByDataBetween(inicio, fim))) {
            if (!"APROVADO".equalsIgnoreCase(m.getStatus()) || m.getTurma() == null || m.getData() == null) {
                continue;
            }
            SumarioAula s = idx.get(chave(m.getTurma().getId(), m.getData(), m.getHoraInicio()));
            String prof = m.getProfessor() != null && !m.getProfessor().isBlank() ? m.getProfessor() : "—";
            String tipo = m.getTipo() != null && !m.getTipo().isBlank() ? m.getTipo() : "PONTUAL";
            lista.add(new Ocorrencia(m.getData(), m.getHoraInicio(), m.getHoraFim(), m.getTurma(), prof,
                    m.getSala(), tipo, s));
        }
        return lista;
    }

    private static String chave(Long turmaId, LocalDate data, LocalTime hora) {
        return turmaId + "|" + data + "|" + (hora == null ? "" : hora);
    }

    // ================= FILTROS + RENDER =================

    private void aplicarFiltros() {
        Professor prof = comboProf.getValue();
        Turma turma = comboTurma.getValue();
        Estado estado = comboEstado.getValue();
        String texto = normalizar(txtPesquisa.getValue());
        String profNorm = prof != null ? normalizar(prof.getNome()) : null;

        List<Ocorrencia> filtradas = ocorrenciasPeriodo.stream()
                .filter(o -> profNorm == null || normalizar(o.professorNome()).equals(profNorm)
                        || normalizar(o.professorNome()).contains(profNorm))
                .filter(o -> turma == null || (o.turma() != null && turma.getId().equals(o.turma().getId())))
                .filter(o -> estado == null || Estado.de(o.sumario()) == estado)
                .filter(o -> texto.isBlank() || correspondeTexto(o, texto))
                .sorted(Comparator.comparing(Ocorrencia::data)
                        .thenComparing(o -> o.inicio() == null ? LocalTime.MIN : o.inicio()))
                .collect(Collectors.toList());

        atualizarStats(filtradas);
        atualizarResumoPorProfessor();
        grid.setItems(filtradas);
        if (!grid.getColumns().isEmpty()) {
            grid.sort(GridSortOrder.asc(grid.getColumns().get(0)).build());
        }
    }

    private boolean correspondeTexto(Ocorrencia o, String texto) {
        if (o.turma() != null && normalizar(o.turma().getDescricao()).contains(texto)) {
            return true;
        }
        if (normalizar(o.professorNome()).contains(texto)) {
            return true;
        }
        return o.sala() != null && normalizar(o.sala().getNome()).contains(texto);
    }

    private void atualizarStats(List<Ocorrencia> lista) {
        long total = lista.size();
        long porPlanear = lista.stream().filter(o -> Estado.de(o.sumario()) == Estado.NAO_PLANEADA).count();
        long planeadas = total - porPlanear;
        long enviadas = lista.stream().filter(o -> Estado.de(o.sumario()) == Estado.ENVIADA).count();

        statsContainer.removeAll();
        statsContainer.add(
                ViewUtils.statCard("Aulas no período", new Span(String.valueOf(total)), null, VaadinIcon.CALENDAR),
                ViewUtils.statCard("Por planear", new Span(String.valueOf(porPlanear)), "#e67e22", VaadinIcon.HOURGLASS),
                ViewUtils.statCard("Planeadas", new Span(String.valueOf(planeadas)), "#4a90e2", VaadinIcon.EDIT),
                ViewUtils.statCard("Sumários enviados", new Span(String.valueOf(enviadas)), "#27ae60", VaadinIcon.CHECK));

        ProgressBar pb = new ProgressBar();
        pb.setValue(total > 0 ? (double) planeadas / total : 0);
        pb.setWidthFull();
        Span pctValor = new Span(total > 0 ? Math.round(100.0 * planeadas / total) + "%" : "—");
        Div progresso = ViewUtils.statCard("Progresso", pctValor, "#4a90e2", VaadinIcon.TRENDING_UP);
        progresso.add(pb);
        statsContainer.add(progresso);
    }

    private void atualizarResumoPorProfessor() {
        Map<String, int[]> porProf = new LinkedHashMap<>(); // nome -> [planeadas, total]
        for (Ocorrencia o : ocorrenciasPeriodo) {
            int[] acc = porProf.computeIfAbsent(o.professorNome(), k -> new int[2]);
            acc[1]++;
            if (Estado.de(o.sumario()) != Estado.NAO_PLANEADA) {
                acc[0]++;
            }
        }

        resumoContainer.removeAll();
        if (porProf.isEmpty()) {
            Span vazio = new Span("Sem aulas no período selecionado.");
            vazio.addClassNames("cf-muted", "cf-text-s");
            resumoContainer.add(vazio);
            return;
        }

        porProf.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> resumoContainer.add(criarCardProf(e.getKey(), e.getValue()[0], e.getValue()[1])));
    }

    private Div criarCardProf(String nome, int planeadas, int total) {
        double frac = total > 0 ? (double) planeadas / total : 1;
        String tom = frac >= 0.85 ? "success" : frac >= 0.5 ? "warning" : "error";

        Span titulo = new Span(nome);
        titulo.addClassNames("cf-cell-title", "cf-text-s");
        Span numeros = new Span(planeadas + "/" + total + " planeadas");
        numeros.addClassNames("cf-secondary", "cf-text-xs");

        ProgressBar pb = new ProgressBar();
        pb.setValue(frac);
        pb.setWidth("150px");

        Div card = new Div(titulo, numeros, pb);
        card.addClassNames("cf-subcard", "cf-progress-card", "cf-progress-card--" + tom);
        card.getElement().setAttribute("title", "Filtrar por " + nome);
        card.addClickListener(e -> comboProf.setValue(profPorNome.get(normalizar(nome))));
        return card;
    }

    // ================= GRID =================

    private void configurarGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER,
                GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setWidthFull();
        grid.setMultiSort(true);

        grid.addColumn(o -> capitalizar(o.data().format(FMT_DIA)))
                .setHeader("Data").setAutoWidth(true).setFlexGrow(0)
                .setComparator(Comparator.comparing(Ocorrencia::data)
                        .thenComparing(o -> o.inicio() == null ? LocalTime.MIN : o.inicio()));

        grid.addColumn(o -> horaTexto(o.inicio()) + " – " + horaTexto(o.fim()))
                .setHeader("Hora").setAutoWidth(true).setFlexGrow(0)
                .setComparator(Comparator.comparing(o -> o.inicio() == null ? LocalTime.MIN : o.inicio()));

        grid.addColumn(Ocorrencia::professorNome)
                .setHeader("Professor").setAutoWidth(true)
                .setComparator(Comparator.comparing(o -> normalizar(o.professorNome())));

        grid.addComponentColumn(this::criarCelulaTurma)
                .setHeader("Turma").setAutoWidth(true)
                .setComparator(Comparator.comparing(o -> normalizar(o.turma() != null ? o.turma().getDescricao() : "")));

        grid.addColumn(o -> o.sala() != null ? o.sala().getNome() : "—")
                .setHeader("Sala").setAutoWidth(true).setFlexGrow(0);

        grid.addComponentColumn(o -> criarBadgeTipo(o.tipo()))
                .setHeader("Tipo").setAutoWidth(true).setFlexGrow(0)
                .setComparator(Comparator.comparing(Ocorrencia::tipo));

        grid.addComponentColumn(o -> criarBadgeEstado(Estado.de(o.sumario())))
                .setHeader("Estado").setAutoWidth(true).setFlexGrow(0)
                .setComparator(Comparator.comparing(o -> Estado.de(o.sumario()).ordinal()));

        grid.addComponentColumn(o -> {
            Button ver = new Button("Ver", new Icon(VaadinIcon.EYE));
            ver.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            ver.addClickListener(e -> abrirDetalhe(o));
            return ver;
        }).setHeader("").setAutoWidth(true).setFlexGrow(0);
    }

    private HorizontalLayout criarCelulaTurma(Ocorrencia o) {
        String cor = o.turma() != null && o.turma().getCor() != null && !o.turma().getCor().isBlank()
                ? o.turma().getCor() : "#94a3b8";
        Div dot = new Div();
        dot.addClassName("cf-dot");
        dot.getStyle().set("background", cor);
        Span nome = new Span(o.turma() != null ? o.turma().getDescricao() : "—");
        HorizontalLayout hl = new HorizontalLayout(dot, nome);
        hl.setAlignItems(FlexComponent.Alignment.CENTER);
        hl.setSpacing(true);
        hl.setPadding(false);
        return hl;
    }

    private Span criarBadgeTipo(String tipo) {
        boolean regular = "REGULAR".equalsIgnoreCase(tipo);
        Span badge = new Span(regular ? "Regular" : capitalizar(tipo.toLowerCase()));
        badge.addClassNames("cf-pill", regular ? "cf-pill--violet" : "cf-pill--warning");
        return badge;
    }

    private Span criarBadgeEstado(Estado estado) {
        Span badge = new Span(estado.icone + " " + estado.label);
        badge.addClassNames("cf-pill", "cf-pill--" + estado.tom);
        return badge;
    }

    // ================= DETALHE =================

    private void abrirDetalhe(Ocorrencia o) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(o.turma() != null ? o.turma().getDescricao() : "Aula");
        dialog.setWidth("94vw");
        dialog.setMaxWidth("480px");

        VerticalLayout conteudo = new VerticalLayout();
        conteudo.setPadding(false);
        conteudo.setSpacing(false);
        conteudo.getStyle().set("gap", "12px");

        Span info = new Span(capitalizar(o.data().format(FMT_DIA_LONGO)) + "  ·  "
                + horaTexto(o.inicio()) + " – " + horaTexto(o.fim())
                + (o.sala() != null ? "  ·  " + o.sala().getNome() : ""));
        info.addClassNames("cf-secondary", "cf-text-s", "cf-semibold");
        conteudo.add(info);

        HorizontalLayout badges = new HorizontalLayout(new Span("👤 " + o.professorNome()),
                criarBadgeTipo(o.tipo()), criarBadgeEstado(Estado.de(o.sumario())));
        badges.setAlignItems(FlexComponent.Alignment.CENTER);
        badges.getStyle().set("flex-wrap", "wrap").set("gap", "8px");
        conteudo.add(badges);

        SumarioAula s = o.sumario();
        boolean temPlano = s != null && s.getPlaneamento() != null && !s.getPlaneamento().isBlank();
        boolean temSumario = s != null && s.getSumario() != null && !s.getSumario().isBlank();

        if (temPlano) {
            conteudo.add(criarSeccao("📝 Plano da aula", s.getPlaneamento()));
        }
        if (temSumario) {
            String tit = "✅ Sumário";
            if (s.isEnviado() && s.getDataEnvio() != null) {
                tit += " (enviado em " + s.getDataEnvio()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + ")";
            }
            conteudo.add(criarSeccao(tit, s.getSumario()));
        }
        if (!temPlano && !temSumario) {
            Span vazio = new Span("Esta aula ainda não está planeada.");
            vazio.addClassName("cf-empty");
            conteudo.add(vazio);
        }

        dialog.add(conteudo);
        dialog.getFooter().add(new Button("Fechar", e -> dialog.close()));
        dialog.open();
    }

    private Div criarSeccao(String titulo, String texto) {
        Div bloco = new Div();
        bloco.addClassName("cf-subcard");
        Span t = new Span(titulo);
        t.addClassName("cf-subcard-title");
        Div corpo = new Div();
        corpo.setText(texto);
        corpo.addClassName("cf-pre-wrap");
        bloco.add(t, corpo);
        return bloco;
    }

    // ================= AUXILIARES =================

    private static String horaTexto(LocalTime hora) {
        return hora == null ? "--:--" : hora.format(FMT_HORA);
    }

    private static String capitalizar(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    // ================= TIPOS =================

    private enum Estado {
        NAO_PLANEADA("⏳", "Por planear", "neutral"),
        PLANEADA("📝", "Planeada", "info"),
        ENVIADA("✅", "Sumário enviado", "success");

        final String icone;
        final String label;
        /** Tom semântico do tema (cf-pill--tom). */
        final String tom;

        Estado(String icone, String label, String tom) {
            this.icone = icone;
            this.label = label;
            this.tom = tom;
        }

        static Estado de(SumarioAula s) {
            if (s == null) {
                return NAO_PLANEADA;
            }
            if (s.isEnviado()) {
                return ENVIADA;
            }
            boolean temConteudo = (s.getPlaneamento() != null && !s.getPlaneamento().isBlank())
                    || (s.getSumario() != null && !s.getSumario().isBlank());
            return temConteudo ? PLANEADA : NAO_PLANEADA;
        }
    }

    private record Ocorrencia(LocalDate data, LocalTime inicio, LocalTime fim, Turma turma,
            String professorNome, Sala sala, String tipo, SumarioAula sumario) {
    }
}
