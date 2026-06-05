package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.AvaliacaoAluno;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AvaliacaoAlunoRepository;
import pt.studioflow.repository.TurmaRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Route(value = "avaliacoes", layout = MainLayout.class)
@PageTitle("Avaliações | CoreoFlow")
@RolesAllowed({"ADMIN", "PROF"})
public class AvaliacoesView extends VerticalLayout {

    private static final List<String> COMPETENCIAS_PADRAO =
            List.of("Postura", "Ritmo", "Expressão", "Técnica", "Dedicação");

    private final AvaliacaoAlunoRepository avaliacaoRepo;
    private final AlunoRepository alunoRepo;
    private final TurmaRepository turmaRepo;

    private Grid<AvaliacaoAluno> grid;
    private ComboBox<Turma> filtroTurma;

    public AvaliacoesView(AvaliacaoAlunoRepository avaliacaoRepo,
                          AlunoRepository alunoRepo,
                          TurmaRepository turmaRepo) {
        this.avaliacaoRepo = avaliacaoRepo;
        this.alunoRepo = alunoRepo;
        this.turmaRepo = turmaRepo;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        add(criarToolbar(), criarGrid());
        atualizar();
    }

    private HorizontalLayout criarToolbar() {
        Studio s = TenantContext.getCurrentStudio();
        filtroTurma = new ComboBox<>("Turma");
        filtroTurma.setItems(s != null ? turmaRepo.findAllByStudio(s) : turmaRepo.findAll());
        filtroTurma.setItemLabelGenerator(t -> t.getDescricao() + " (" + t.getCodigo() + ")");
        filtroTurma.setClearButtonVisible(true);
        filtroTurma.setPlaceholder("Todas");
        filtroTurma.setWidth("240px");
        filtroTurma.addValueChangeListener(e -> atualizar());

        return ViewUtils.toolbar(filtroTurma,
                ViewUtils.botaoNovo("Nova Avaliação", e -> abrirDialog(null)));
    }

    private Grid<AvaliacaoAluno> criarGrid() {
        grid = new Grid<>(AvaliacaoAluno.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(a -> a.getDataAvaliacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .setHeader("Data").setAutoWidth(true).setSortable(true);
        grid.addColumn(a -> a.getAluno() != null ? a.getAluno().getNomeCompleto() : "—")
                .setHeader("Aluno").setAutoWidth(true).setSortable(true);
        grid.addColumn(a -> a.getTurma() != null ? a.getTurma().getDescricao() : "—")
                .setHeader("Turma").setAutoWidth(true);
        grid.addColumn(AvaliacaoAluno::getPeriodo).setHeader("Período").setAutoWidth(true);

        grid.addComponentColumn(a -> {
            String[] cfg = switch (a.getNivel()) {
                case INICIANTE  -> new String[]{"#fff3e0","#E67E22","Iniciante"};
                case INTERMEDIO -> new String[]{"#e3f2fd","#1976D2","Intermédio"};
                case AVANCADO   -> new String[]{"#ede7f6","#7B1FA2","Avançado"};
                case EXCELENTE  -> new String[]{"#e8f5e9","#27AE60","Excelente"};
            };
            Span badge = new Span(cfg[2]);
            badge.getStyle().set("background", cfg[0]).set("color", cfg[1])
                    .set("padding", "2px 8px").set("border-radius", "10px")
                    .set("font-size", "11px").set("font-weight", "600");
            return badge;
        }).setHeader("Nível").setAutoWidth(true);

        grid.addComponentColumn(a -> {
            String comp = a.getCompetencias();
            if (comp == null || comp.isBlank()) return new Span("—");
            Map<String, Integer> map = parseCompetencias(comp);
            double media = map.values().stream().mapToInt(i -> i).average().orElse(0);
            Span s = new Span("★ " + String.format("%.1f", media) + "/5");
            s.getStyle().set("font-weight", "700").set("color", "#F39C12");
            return s;
        }).setHeader("Média").setAutoWidth(true);

        grid.addComponentColumn(a -> {
            Button editar = new Button(VaadinIcon.EDIT.create(), e -> abrirDialog(a));
            editar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button del = new Button(VaadinIcon.TRASH.create(), e -> {
                avaliacaoRepo.delete(a);
                atualizar();
            });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return new HorizontalLayout(editar, del);
        }).setHeader("Ações").setAutoWidth(true);

        return grid;
    }

    private void atualizar() {
        Studio s = TenantContext.getCurrentStudio();
        Turma t = filtroTurma != null ? filtroTurma.getValue() : null;
        List<AvaliacaoAluno> items = t != null
                ? avaliacaoRepo.findByTurmaAndStudioOrderByDataAvaliacaoDesc(t, s)
                : (s != null ? avaliacaoRepo.findByStudioOrderByDataAvaliacaoDesc(s)
                             : avaliacaoRepo.findAll());
        grid.setItems(items);
    }

    private void abrirDialog(AvaliacaoAluno av) {
        boolean novo = av == null;
        AvaliacaoAluno avaliacao = novo ? new AvaliacaoAluno() : av;
        Studio studio = TenantContext.getCurrentStudio();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(novo ? "Nova Avaliação" : "Editar Avaliação");
        dialog.setWidth("520px");
        dialog.setMaxWidth("98vw");

        // Aluno
        ComboBox<Aluno> alunoCombo = new ComboBox<>("Aluno");
        List<Aluno> alunos = studio != null ? alunoRepo.findAllByStudio(studio) : alunoRepo.findAll();
        alunoCombo.setItems(alunos);
        alunoCombo.setItemLabelGenerator(Aluno::getNomeCompleto);
        alunoCombo.setWidthFull();
        if (avaliacao.getAluno() != null) alunoCombo.setValue(avaliacao.getAluno());

        // Turma
        ComboBox<Turma> turmaCombo = new ComboBox<>("Turma");
        List<Turma> turmas = studio != null ? turmaRepo.findAllByStudio(studio) : turmaRepo.findAll();
        turmaCombo.setItems(turmas);
        turmaCombo.setItemLabelGenerator(Turma::getDescricao);
        turmaCombo.setWidthFull();
        if (avaliacao.getTurma() != null) turmaCombo.setValue(avaliacao.getTurma());

        TextField periodo = new TextField("Período");
        periodo.setPlaceholder("ex: 2024/2025 · 1º Período");
        periodo.setWidthFull();
        periodo.setValue(avaliacao.getPeriodo() != null ? avaliacao.getPeriodo() : "");

        ComboBox<AvaliacaoAluno.Nivel> nivel = new ComboBox<>("Nível Geral");
        nivel.setItems(AvaliacaoAluno.Nivel.values());
        nivel.setItemLabelGenerator(n -> switch (n) {
            case INICIANTE -> "Iniciante"; case INTERMEDIO -> "Intermédio";
            case AVANCADO -> "Avançado"; case EXCELENTE -> "Excelente";
        });
        nivel.setValue(avaliacao.getNivel() != null ? avaliacao.getNivel() : AvaliacaoAluno.Nivel.INICIANTE);
        nivel.setWidthFull();

        // Competências — IntegerField 1-5 para cada
        Map<String, Integer> compAtual = parseCompetencias(
                avaliacao.getCompetencias() != null ? avaliacao.getCompetencias() : "");
        FormLayout compForm = new FormLayout();
        Map<String, IntegerField> compFields = new LinkedHashMap<>();
        for (String c : COMPETENCIAS_PADRAO) {
            IntegerField f = new IntegerField(c);
            f.setMin(1); f.setMax(5); f.setStep(1); f.setValue(compAtual.getOrDefault(c, 3));
            f.setWidthFull();
            compFields.put(c, f);
            compForm.add(f);
        }
        compForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 3));

        TextArea obs = new TextArea("Observações");
        obs.setWidthFull();
        obs.setMaxHeight("100px");
        obs.setValue(avaliacao.getObservacoes() != null ? avaliacao.getObservacoes() : "");

        VerticalLayout content = new VerticalLayout(
                new FormLayout(alunoCombo, turmaCombo, periodo, nivel),
                new Span("Competências (1 a 5 estrelas):"), compForm, obs);
        content.setPadding(false);

        Button guardar = new Button("Guardar", e -> {
            if (alunoCombo.getValue() == null || periodo.isEmpty()) {
                Notification.show("Aluno e Período são obrigatórios");
                return;
            }
            avaliacao.setAluno(alunoCombo.getValue());
            avaliacao.setTurma(turmaCombo.getValue());
            avaliacao.setPeriodo(periodo.getValue().trim());
            avaliacao.setNivel(nivel.getValue());
            avaliacao.setObservacoes(obs.getValue().trim());
            avaliacao.setDataAvaliacao(LocalDate.now());
            avaliacao.setStudio(studio);
            // Serializar competências
            StringBuilder sb = new StringBuilder();
            compFields.forEach((k, v) -> {
                if (sb.length() > 0) sb.append(",");
                sb.append(k).append(":").append(v.getValue() != null ? v.getValue() : 3);
            });
            avaliacao.setCompetencias(sb.toString());
            avaliacaoRepo.save(avaliacao);
            atualizar();
            dialog.close();
            Notification.show("Avaliação guardada").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }

    public static Map<String, Integer> parseCompetencias(String raw) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String part : raw.split(",")) {
            String[] kv = part.split(":", 2);
            if (kv.length == 2) {
                try { map.put(kv[0].trim(), Integer.parseInt(kv[1].trim())); }
                catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }
}
