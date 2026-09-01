package pt.studioflow.view;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Aula;
import pt.studioflow.model.Presenca;
import pt.studioflow.model.Turma;
import pt.studioflow.model.User;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.PresencaRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.UserRepository;
import pt.studioflow.service.EmailService;
import pt.studioflow.service.PdfService;
import pt.studioflow.service.TurmaService;
import pt.studioflow.view.PresencasView.AlunoPresenca;

@Route(value = "presencas", layout = MainLayout.class)
@PageTitle("Folha de Presenças | CoreoFlow")
@RolesAllowed({ "ADMIN", "PROF" })
@JavaScript("https://cdn.jsdelivr.net/npm/chart.js")
public class PresencasView extends VerticalLayout {

    private final TurmaRepository turmaRepository;
    private final PresencaRepository presencaRepository;
    private final TurmaService turmaService;
    private final AlunoRepository alunoRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final UserRepository userRepository;
    private final PdfService pdfService;

    private final EmailService mailService;

    private ComboBox<Turma> turmaCombo;
    private ComboBox<YearMonth> mesCombo;
    private Grid<AlunoPresenca> grid = new Grid<>();
    private Turma turmaSelecionada;
    private YearMonth mesSelecionado;
    private final Map<Long, Map<LocalDate, Boolean>> presencasCache = new HashMap<>();
    private Div chartContainer;

    // Modo "Todos": galeria de cards com o gráfico de presenças do mês atual
    // de cada turma. É o ecrã que aparece por defeito ao entrar na view.
    private Div cardsContainer;
    private Button btnExperimental;
    private Button btnGuardar;
    private final Turma TODAS = criarSentinelaTodas();

    private static Turma criarSentinelaTodas() {
        Turma t = new Turma();
        t.setDescricao("📊 Todas as turmas");
        return t;
    }

    public PresencasView(TurmaRepository turmaRepository,
            PresencaRepository presencaRepository,
            TurmaService turmaService,
            AlunoRepository alunoRepository,
            AlunoTurmaRepository alunoTurmaRepository,
            UserRepository userRepository,
            PdfService pdfService,
            EmailService mailService) {
        this.turmaRepository = turmaRepository;
        this.presencaRepository = presencaRepository;
        this.turmaService = turmaService;
        this.alunoRepository = alunoRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.userRepository = userRepository;
        this.pdfService = pdfService;
        this.mailService = mailService;

        // Alterado para permitir scroll infinito na página
        setSizeFull();
        setHeight("auto");
        setPadding(true);
        setSpacing(false);

        injectStyles();
        configurarUI();
        configurarEventos();
    }

    private void injectStyles() {
        String styles = "vaadin-grid::part(dia-aula) { background-color: #d1f7f1 !important; color: #006d75 !important; font-weight: bold; } "
                + "vaadin-grid::part(fim-de-semana) { background-color: #f5f5f5 !important; color: #bfbfbf; } "
                + ".chart-card { background: white; border-radius: 15px; padding: 20px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); border: 1px solid #eee; margin-top: 20px; } "
                + ".count-badge { background: #16a085; color: white; border-radius: 50%; padding: 2px 6px; font-size: 0.7rem; font-weight: bold; margin-top: 2px; } "
                // CSS para esconder o gráfico em mobile e ajustar o scroll
                + "@media (max-width: 800px) { "
                + "  #chart-container { display: none !important; } "
                + "  .toolbar-responsiva { flex-direction: column; align-items: stretch !important; } "
                + "}";

        UI.getCurrent().getElement().executeJs(
                "const style = document.createElement('style'); style.textContent = $0; document.head.appendChild(style);",
                styles);
    }

    private void configurarUI() {
        H2 title = new H2("Folha de Presenças");

        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.END);
        toolbar.getStyle().set("margin-bottom", "20px");
        toolbar.addClassName("toolbar-responsiva");

        turmaCombo = new ComboBox<>("Turma");
        turmaCombo.setPlaceholder("Escolha a turma...");
        turmaCombo.setWidth("250px");
        carregarTurmasPermitidas();

        mesCombo = new ComboBox<>("Mês");
        mesCombo.setItems(YearMonth.now().minusMonths(1), YearMonth.now(), YearMonth.now().plusMonths(1));
        mesCombo.setValue(YearMonth.now());
        mesCombo.setWidth("180px");

        btnExperimental = new Button("Aula Experimental", VaadinIcon.MAGIC.create(),
                e -> abrirDialogExperimental());
        btnExperimental.addThemeVariants(ButtonVariant.LUMO_CONTRAST);

        btnGuardar = new Button("Guardar", VaadinIcon.DATABASE.create(), e -> guardarPresencas());
        btnGuardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

        toolbar.add(turmaCombo, mesCombo, btnExperimental, btnGuardar);

        grid.addThemeVariants(GridVariant.LUMO_COLUMN_BORDERS, GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        // Ajustado para scroll infinito (a grid cresce com os dados)
        grid.setAllRowsVisible(true);
        grid.setHeight("auto");

        chartContainer = new Div();
        chartContainer.setId("chart-container");
        chartContainer.addClassName("chart-card");
        chartContainer.setHeight("280px");

        cardsContainer = new Div();
        cardsContainer.setId("cards-container");
        cardsContainer.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(320px, 1fr))")
                .set("gap", "20px")
                .set("width", "100%")
                .set("margin-top", "10px");

        add(title, toolbar, grid, chartContainer, cardsContainer);
    }

    private void abrirDialogExperimental() {
        if (turmaSelecionada == null) {
            Notification.show("Selecione uma turma primeiro!").addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Registar Aula Experimental");

        VerticalLayout layout = new VerticalLayout();
        TextField txtNome = new TextField("Nome do Candidato");
        TextField txtTel = new TextField("Telemóvel");
        txtNome.setWidthFull();
        txtTel.setWidthFull();
        layout.add(txtNome, txtTel);

        Button btnConfirmar = new Button("Adicionar à Turma", e -> {
            if (txtNome.getValue().trim().isEmpty())
                return;

            Aluno novo = new Aluno();
            novo.setNomeCompleto(txtNome.getValue());
            novo.setTelemovel(txtTel.getValue());
            novo.setStatus(AlunoStatus.EXPERIMENTAL);
            novo.setAtivo(false);
            novo = alunoRepository.save(novo);

            AlunoTurma at = new AlunoTurma();
            at.setAluno(novo);
            at.setTurma(turmaSelecionada);
            at.setAulasPorSemana(1);
            alunoTurmaRepository.save(at);

            Date agora = new Date();

            mailService.enviarEmailNotificacaoExperimental(novo, turmaSelecionada);

            Notification.show("Adicionado com sucesso!");
            dialog.close();
            carregarTudo();
        });
        btnConfirmar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(new Button("Cancelar", i -> dialog.close()), btnConfirmar);
        dialog.add(layout);
        dialog.open();
    }

    private void atualizarColunasGrid() {
        grid.removeAllColumns();
        if (turmaSelecionada == null)
            return;

        Set<DayOfWeek> diasDeAula = turmaSelecionada.getAulas().stream()
                .map(Aula::getDia).collect(Collectors.toSet());

        grid.addColumn(new ComponentRenderer<>(ap -> {
            Span s = new Span(formatarNomeCurto(ap.getAluno().getNomeCompleto()));
            s.getStyle().set("font-weight", "600");
            if (ap.getAluno().getStatus() == AlunoStatus.EXPERIMENTAL) {
                s.getStyle().set("color", "#FF8C00");
                s.setText(s.getText() + " (Exp.)");
            }
            return s;
        })).setHeader("Aluno").setFrozen(true).setWidth("180px").setFlexGrow(0);

        for (int dia = 1; dia <= mesSelecionado.lengthOfMonth(); dia++) {
            final LocalDate data = mesSelecionado.atDay(dia);
            final DayOfWeek dow = data.getDayOfWeek();
            final boolean temAula = diasDeAula.contains(dow);

            VerticalLayout headerLayout = new VerticalLayout(new Span(String.valueOf(dia)), new Span(traduzirDia(dow)));
            headerLayout.setSpacing(false);
            headerLayout.setPadding(false);
            headerLayout.setAlignItems(Alignment.CENTER);

            Span countBadge = new Span();
            countBadge.addClassName("count-badge");
            countBadge.setVisible(false);
            headerLayout.add(countBadge);

            grid.addComponentColumn(ap -> {
                Checkbox cb = new Checkbox(ap.getPresencas().getOrDefault(data, false));
                cb.addValueChangeListener(ev -> {
                    ap.getPresencas().put(data, ev.getValue());
                    atualizarFeedbackVisual();
                });
                return cb;
            }).setHeader(headerLayout).setWidth("65px").setFlexGrow(0)
                    .setPartNameGenerator(item -> {
                        if (temAula)
                            return "dia-aula";
                        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY)
                            return "fim-de-semana";
                        return null;
                    });
        }
        carregarItensGrid();
    }

    private void carregarItensGrid() {
        List<AlunoPresenca> lista = turmaService.getAlunosDaTurma(turmaSelecionada).stream()
                .sorted(Comparator.comparing(Aluno::getNomeCompleto, String.CASE_INSENSITIVE_ORDER)) // <-- Ordena os
                                                                                                     // alunos primeiro
                .map(aluno -> {
                    AlunoPresenca ap = new AlunoPresenca(aluno);
                    for (int d = 1; d <= mesSelecionado.lengthOfMonth(); d++) {
                        LocalDate dRef = mesSelecionado.atDay(d);
                        ap.getPresencas().put(dRef,
                                presencasCache.getOrDefault(aluno.getId(), Map.of()).getOrDefault(dRef, false));
                    }
                    return ap;
                }).collect(Collectors.toList());

        grid.setItems(lista);
        atualizarContadoresCabecalho();
    }

    private void carregarTudo() {
        if (turmaCombo.getValue() == null || turmaCombo.getValue() == TODAS) {
            entrarModoTodos();
            return;
        }
        entrarModoTurma();
        if (mesCombo.getValue() == null)
            return;
        turmaSelecionada = turmaRepository.findByIdCompleto(turmaCombo.getValue().getId());
        mesSelecionado = mesCombo.getValue();
        carregarPresencasCache();
        atualizarColunasGrid();
        atualizarGrafico();
    }

    private void entrarModoTurma() {
        grid.setVisible(true);
        chartContainer.setVisible(true);
        mesCombo.setVisible(true);
        btnExperimental.setVisible(true);
        btnGuardar.setVisible(true);
        cardsContainer.setVisible(false);
    }

    private void entrarModoTodos() {
        grid.setVisible(false);
        chartContainer.setVisible(false);
        mesCombo.setVisible(false);
        btnExperimental.setVisible(false);
        btnGuardar.setVisible(false);
        cardsContainer.setVisible(true);
        renderCardsTodasTurmas();
    }

    /**
     * Modo "Todos": um card por turma (estilo galeria da Comunicação) com o
     * gráfico de presenças do mês atual, à semelhança do gráfico da folha
     * individual. É o ecrã por defeito ao entrar na view.
     */
    private void renderCardsTodasTurmas() {
        cardsContainer.removeAll();
        YearMonth mes = YearMonth.now();
        LocalDate ini = mes.atDay(1);
        LocalDate fim = mes.atEndOfMonth();

        List<Turma> turmas = turmasParaCards();
        if (turmas.isEmpty()) {
            Span vazio = new Span("Sem turmas para mostrar.");
            vazio.getStyle().set("color", "#888");
            cardsContainer.add(vazio);
            return;
        }

        List<String> desenhos = new ArrayList<>();
        for (Turma base : turmas) {
            Turma turma = turmaRepository.findByIdCompleto(base.getId());

            Map<Integer, Long> porDia = new java.util.TreeMap<>();
            presencaRepository.findByTurmaAndDataBetween(turma, ini, fim).stream()
                    .filter(Presenca::isPresente)
                    .forEach(p -> porDia.merge(p.getData().getDayOfMonth(), 1L, Long::sum));
            long total = porDia.values().stream().mapToLong(Long::longValue).sum();

            String wrapId = "pres-wrap-" + turma.getId();

            Div card = new Div();
            card.addClassName("chart-card");
            card.getStyle().set("margin-top", "0");

            Span header = new Span(turma.getDescricao());
            header.getStyle().set("font-weight", "700").set("display", "block").set("margin-bottom", "6px");

            Div canvasWrap = new Div();
            canvasWrap.setId(wrapId);
            canvasWrap.setHeight("190px");

            Span sub = new Span(total > 0 ? total + " presenças marcadas este mês" : "Sem presenças marcadas este mês");
            sub.getStyle().set("font-size", "0.8rem").set("color", "#888");

            card.add(header, canvasWrap, sub);
            cardsContainer.add(card);

            List<String> pontos = new ArrayList<>();
            for (int d = 1; d <= mes.lengthOfMonth(); d++) {
                long c = porDia.getOrDefault(d, 0L);
                if (c > 0)
                    pontos.add("{x:'" + d + "',y:" + c + "}");
            }
            desenhos.add("(function(){var w=document.getElementById('" + wrapId + "');if(!w)return;"
                    + "var el=w.querySelector('canvas');if(!el){el=document.createElement('canvas');w.appendChild(el);}"
                    + "window.__presCards=window.__presCards||{};"
                    + "if(window.__presCards['" + wrapId + "'])window.__presCards['" + wrapId + "'].destroy();"
                    + "window.__presCards['" + wrapId + "']=new Chart(el.getContext('2d'),{type:'line',"
                    + "data:{datasets:[{label:'Presenças',data:[" + String.join(",", pontos) + "],"
                    + "borderColor:'#16a085',backgroundColor:'rgba(22,160,133,0.1)',fill:true,tension:0.3}]},"
                    + "options:{responsive:true,maintainAspectRatio:false,scales:{y:{beginAtZero:true,ticks:{stepSize:1}}}}});})();");
        }

        if (!desenhos.isEmpty()) {
            String script = "(function go(){if(typeof Chart==='undefined'){setTimeout(go,120);return;}"
                    + String.join("", desenhos) + "})();";
            UI.getCurrent().getPage().executeJs(script);
        }
    }

    private void carregarPresencasCache() {
        presencasCache.clear();
        presencaRepository
                .findByTurmaAndDataBetween(turmaSelecionada, mesSelecionado.atDay(1), mesSelecionado.atEndOfMonth())
                .forEach(p -> presencasCache.computeIfAbsent(p.getAluno().getId(), k -> new HashMap<>())
                        .put(p.getData(), p.isPresente()));
    }

    private void guardarPresencas() {
        if (turmaSelecionada == null)
            return;
        grid.getListDataView().getItems().forEach(ap -> {
            ap.getPresencas().forEach((data, presente) -> {
                Presenca p = presencaRepository.findByAlunoAndTurmaAndData(ap.getAluno(), turmaSelecionada, data)
                        .orElseGet(Presenca::new);
                p.setAluno(ap.getAluno());
                p.setTurma(turmaSelecionada);
                p.setData(data);
                p.setPresente(presente);
                presencaRepository.save(p);
            });
        });
        Notification.show("Guardado com sucesso!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void carregarTurmasPermitidas() {
        pt.studioflow.model.Studio _s = pt.studioflow.config.TenantContext.getCurrentStudio();
        List<Turma> turmasAll = _s != null ? turmaRepository.findAllByStudio(_s) : turmaRepository.findAllComplete();
        List<Turma> turmas = isAdmin() ? turmasAll
                : turmasAll.stream()
                        .filter(t -> t.getProfessor() != null && normalizar(t.getProfessor().getNome())
                                .contains(normalizar(getFirstNameFromDatabase())))
                        .collect(Collectors.toList());

        List<Turma> comTodas = new ArrayList<>();
        comTodas.add(TODAS);
        comTodas.addAll(turmas);
        turmaCombo.setItems(comTodas);
        turmaCombo.setItemLabelGenerator(Turma::getDescricao);
        turmaCombo.setValue(TODAS);
    }

    private List<Turma> turmasParaCards() {
        return turmaCombo.getListDataView().getItems()
                .filter(t -> t != TODAS)
                .collect(Collectors.toList());
    }

    private void atualizarFeedbackVisual() {
        atualizarGrafico();
        atualizarContadoresCabecalho();
    }

    private void atualizarContadoresCabecalho() {
        List<AlunoPresenca> items = grid.getListDataView().getItems().collect(Collectors.toList());
        for (int dia = 1; dia <= mesSelecionado.lengthOfMonth(); dia++) {
            LocalDate data = mesSelecionado.atDay(dia);
            long totalDia = items.stream().filter(ap -> ap.getPresencas().getOrDefault(data, false)).count();
            if (dia < grid.getColumns().size()) {
                Component header = grid.getColumns().get(dia).getHeaderComponent();
                if (header instanceof VerticalLayout vl) {
                    vl.getChildren().filter(c -> c instanceof Span && ((Span) c).hasClassName("count-badge"))
                            .map(c -> (Span) c).forEach(s -> {
                                s.setText(String.valueOf(totalDia));
                                s.setVisible(totalDia > 0);
                            });
                }
            }
        }
    }

    private void atualizarGrafico() {
        if (mesSelecionado == null || turmaSelecionada == null)
            return;
        List<String> pontosJs = new ArrayList<>();
        List<AlunoPresenca> items = grid.getListDataView().getItems().collect(Collectors.toList());
        for (int dia = 1; dia <= mesSelecionado.lengthOfMonth(); dia++) {
            LocalDate data = mesSelecionado.atDay(dia);
            long count = items.stream().filter(ap -> ap.getPresencas().getOrDefault(data, false)).count();
            if (count > 0)
                pontosJs.add("{x: '" + dia + "', y: " + count + "}");
        }
        String script = "var container = document.getElementById('chart-container'); if (container && container.offsetWidth > 0) { var canvas = container.querySelector('canvas'); if (!canvas) { canvas = document.createElement('canvas'); container.appendChild(canvas); } var ctx = canvas.getContext('2d'); if (window.myChart) { window.myChart.destroy(); } window.myChart = new Chart(ctx, { type: 'line', data: { datasets: [{ label: 'Presenças', data: "
                + pontosJs
                + ", borderColor: '#16a085', backgroundColor: 'rgba(22,160,133,0.1)', fill: true, tension: 0.3 }] }, options: { responsive: true, maintainAspectRatio: false, scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } } } }); }";
        UI.getCurrent().getPage().executeJs(script);
    }

    private void configurarEventos() {
        turmaCombo.addValueChangeListener(e -> carregarTudo());
        mesCombo.addValueChangeListener(e -> carregarTudo());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Render inicial: por defeito entra no modo "Todos". Feito no onAttach
        // para o Chart.js e o DOM já estarem disponíveis ao correr o executeJs.
        carregarTudo();
    }

    private String getFirstNameFromDatabase() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return userRepository.findByPrincipalName(a.getName()).map(User::getFirstName).orElse("");
    }

    private String normalizar(String t) {
        return t == null ? ""
                : Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String traduzirDia(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "Seg";
            case TUESDAY -> "Ter";
            case WEDNESDAY -> "Qua";
            case THURSDAY -> "Qui";
            case FRIDAY -> "Sex";
            case SATURDAY -> "Sáb";
            case SUNDAY -> "Dom";
        };
    }

    private String formatarNomeCurto(String n) {
        if (n == null || !n.contains(" "))
            return n;
        String[] p = n.trim().split("\\s+");
        return p[0] + " " + p[p.length - 1];
    }

    public static class AlunoPresenca {
        private final Aluno aluno;
        private final Map<LocalDate, Boolean> presencas = new HashMap<>();

        public AlunoPresenca(Aluno aluno) {
            this.aluno = aluno;
        }

        public Aluno getAluno() {
            return aluno;
        }

        public Map<LocalDate, Boolean> getPresencas() {
            return presencas;
        }
    }
}
