package pt.studioflow.view;

import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dnd.GridDropMode;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Turma;
import pt.studioflow.service.AlunoTurmaService;
import pt.studioflow.service.MensalidadeService;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.TurmaRepository;

@PageTitle("Afetar Alunos | CoreoFlow")
@Route(value = "turmas-alunos", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class TurmaAlunosView extends VerticalLayout {

    private final TurmaRepository turmaRepository;
    private final AlunoRepository alunoRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final AlunoTurmaService alunoTurmaService;
    private final MensalidadeService mensalidadeService;

    private ComboBox<Turma> turmaCombo;
    private TextField searchAluno;
    private Grid<Aluno> gridDisponiveis;
    private Grid<Aluno> gridTurma;

    // Variável para rastrear a frequência selecionada no cartão antes do
    // arrastamento
    private int frequenciaSelecionada = 2;

    public TurmaAlunosView(TurmaRepository turmaRepository, AlunoRepository alunoRepository,
            AlunoTurmaRepository alunoTurmaRepository, AlunoTurmaService alunoTurmaService,
            MensalidadeService mensalidadeService) {

        this.turmaRepository = turmaRepository;
        this.alunoRepository = alunoRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.alunoTurmaService = alunoTurmaService;
        this.mensalidadeService = mensalidadeService;

        setSizeFull();
        setSpacing(false);
        setPadding(false);

        injectCustomStyles();
        H2 titulo = new H2("Afetar Alunos às Turmas");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);
        configurarHeader();

        HorizontalLayout mainContent = new HorizontalLayout();
        mainContent.setSizeFull();

        configurarGridDisponiveis();
        configurarGridTurma();

        mainContent.add(criarCardSection("Alunos Disponíveis", VaadinIcon.USERS, gridDisponiveis, "#1ABC9C"),
                criarCardSection("Alunos na Turma", VaadinIcon.ACADEMY_CAP, gridTurma, "#FF5D13"));

        add(mainContent);
        expand(mainContent);
    }


    private void injectCustomStyles() {
        String styles = ".grid-modern { background: transparent !important; border: none !important; }" +
                ".aluno-card { " +
                "  background: white; border-radius: 12px; padding: 10px 15px; " +
                "  box-shadow: 0 2px 8px rgba(0,0,0,0.05); border: 1px solid #eee; " +
                "  display: flex; align-items: center; gap: 12px; width: 100%; transition: all 0.2s; " +
                "} " +
                ".aluno-card:hover { border-color: #FF5D13; background: #fffcfb; }" +
                ".avatar-circle { " +
                "  width: 35px; height: 35px; border-radius: 50%; background: #FF5D13; " +
                "  display: flex; align-items: center; justify-content: center; color: white; font-weight: bold; " +
                "} " +
                ".section-container { " +
                "  background: rgba(255,255,255,0.6); backdrop-filter: blur(10px); " +
                "  border-radius: 16px; padding: 15px; border: 1px solid #e0e0e0; " +
                "}";

        UI.getCurrent().getElement().executeJs(
                "const style = document.createElement('style'); style.textContent = $0; document.head.appendChild(style);",
                styles);
    }

    private void configurarHeader() {

        turmaCombo = new ComboBox<>("Turma Destino");
        pt.studioflow.model.Studio _s = pt.studioflow.config.TenantContext.getCurrentStudio();
        turmaCombo.setItems(_s != null ? turmaRepository.findAllByStudio(_s) : turmaRepository.findAll());
        turmaCombo.setItemLabelGenerator(Turma::getDescricao);
        turmaCombo.setWidth("300px");
        turmaCombo.addValueChangeListener(e -> atualizarGrids());

        searchAluno = new TextField("Pesquisa Rápida");
        searchAluno.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchAluno.setPlaceholder("Filtrar alunos...");
        searchAluno.setValueChangeMode(ValueChangeMode.LAZY);
        searchAluno.addValueChangeListener(e -> atualizarGrids());

        HorizontalLayout header = new HorizontalLayout(turmaCombo, searchAluno);
        header.setAlignItems(Alignment.END);
        header.setSpacing(true);
        
        add(header);
    }

    private void configurarGridDisponiveis() {
        gridDisponiveis = new Grid<>();
        gridDisponiveis.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_NO_BORDER,
                com.vaadin.flow.component.grid.GridVariant.LUMO_NO_ROW_BORDERS);
        gridDisponiveis.addClassName("grid-modern");

        gridDisponiveis.addComponentColumn(this::criarAlunoCardDisponivel);

        // Ativar Arrastamento
        gridDisponiveis.setRowsDraggable(true);
        gridDisponiveis.addDragStartListener(e -> {
            if (!e.getDraggedItems().isEmpty()) {
                gridDisponiveis.asSingleSelect().setValue(e.getDraggedItems().get(0));
            }
        });
    }

    private void configurarGridTurma() {
        gridTurma = new Grid<>();
        gridTurma.addThemeVariants(com.vaadin.flow.component.grid.GridVariant.LUMO_NO_BORDER,
                com.vaadin.flow.component.grid.GridVariant.LUMO_NO_ROW_BORDERS);
        gridTurma.addClassName("grid-modern");

        gridTurma.addComponentColumn(aluno -> {
            HorizontalLayout card = new HorizontalLayout();
            card.addClassName("aluno-card");

            Div avatar = new Div();
            avatar.addClassName("avatar-circle");
            avatar.setText(aluno.getNomeCompleto().substring(0, 1).toUpperCase());

            Span nome = new Span(aluno.getNomeCompleto());
            nome.getStyle().set("font-weight", "600");

            Button remove = new Button(VaadinIcon.TRASH.create(), e -> removerAluno(aluno));
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            remove.getStyle().set("margin-left", "auto");

            card.add(avatar, nome, remove);
            card.setAlignItems(Alignment.CENTER);
            return card;
        });

        // Configurar Receção (Drop)
        gridTurma.setDropMode(GridDropMode.ON_GRID);
        gridTurma.addDropListener(e -> {
            Aluno selecionado = gridDisponiveis.asSingleSelect().getValue();
            if (selecionado != null) {
                adicionarAluno(selecionado, frequenciaSelecionada);
            }
        });
    }

    private HorizontalLayout criarAlunoCardDisponivel(Aluno aluno) {
        HorizontalLayout card = new HorizontalLayout();
        card.addClassName("aluno-card");

        Div avatar = new Div();
        avatar.addClassName("avatar-circle");
        avatar.setText(aluno.getNomeCompleto().substring(0, 1).toUpperCase());

        VerticalLayout info = new VerticalLayout(new Span(aluno.getNomeCompleto()));
        info.setPadding(false);
        info.setSpacing(false);

        // Seletor de Frequência (1x ou 2x)
        ComboBox<Integer> freq = new ComboBox<>();
        freq.setItems(1, 2);
        freq.setValue(2);
        freq.setWidth("70px");
        freq.addThemeName("small");
        freq.addValueChangeListener(e -> frequenciaSelecionada = e.getValue());

        // Impedir que o clique no seletor inicie o drag
        freq.getElement().addEventListener("mousedown", e -> {
        }).addEventData("event.stopPropagation()");

        card.add(avatar, info, freq);
        card.setAlignItems(Alignment.CENTER);
        card.expand(info);
        return card;
    }

    private VerticalLayout criarCardSection(String titulo, VaadinIcon icon, Grid<Aluno> grid, String cor) {
        VerticalLayout container = new VerticalLayout();
        container.addClassName("section-container");
        container.setSizeFull();

        Icon headerIcon = icon.create();
        headerIcon.setColor(cor);
        Span label = new Span(titulo);
        label.getStyle().set("font-weight", "bold").set("font-size", "1.1em");

        HorizontalLayout header = new HorizontalLayout(headerIcon, label);
        header.setAlignItems(Alignment.CENTER);

        container.add(header, grid);
        container.expand(grid);
        return container;
    }

    private void atualizarGrids() {
        Turma turma = turmaCombo.getValue();
        if (turma == null) {
            gridDisponiveis.setItems(List.of());
            gridTurma.setItems(List.of());
            return;
        }
        String filtro = searchAluno.getValue() == null ? "" : searchAluno.getValue().toLowerCase();
        List<Aluno> naTurma = alunoTurmaRepository.findByTurma(turma).stream().map(AlunoTurma::getAluno)
                .collect(Collectors.toList());
        pt.studioflow.model.Studio _studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        List<Aluno> disponiveis = (_studio != null ? alunoRepository.findAllByStudio(_studio) : alunoRepository.findAll()).stream()
                .filter(a -> a.getNomeCompleto().toLowerCase().contains(filtro))
                .filter(a -> !naTurma.contains(a)).collect(Collectors.toList());
        gridDisponiveis.setItems(disponiveis);
        gridTurma.setItems(naTurma);
    }

    private void adicionarAluno(Aluno aluno, int aulas) {
        Turma turma = turmaCombo.getValue();
        if (turma == null)
            return;
        if (!aluno.isAtivo()) {
            Notification.show("Inativo!").addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        AlunoTurma at = new AlunoTurma();
        at.setAluno(aluno);
        at.setTurma(turma);
        at.setAulasPorSemana(aulas);
        alunoTurmaRepository.save(at);
        mensalidadeService.gerarMensalidadesParaAluno(aluno, turma);
        atualizarGrids();
        Notification.show("Aluno Inscrito!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void removerAluno(Aluno aluno) {
        Turma turma = turmaCombo.getValue();
        if (turma == null) return;
        alunoTurmaService.remover(aluno, turma);
        atualizarGrids();
        Notification.show("Aluno removido!").addThemeVariants(NotificationVariant.LUMO_CONTRAST);
    }
}
