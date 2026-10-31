package pt.studioflow.view;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import pt.studioflow.repository.AlunoTurmaRepository;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.ModalidadeRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.SalaRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.TurmaService;

@Route(value = "turma", layout = MainLayout.class)
@PageTitle("Turmas | CoreoFlow")
@RolesAllowed("ADMIN")
public class TurmaView extends VerticalLayout {

    private final TurmaRepository turmaRepository;
    private final SalaRepository salaRepository;
    private final ProfessorRepository professorRepository;
    private final ModalidadeRepository modalidadeRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final TurmaService turmaService;

    public final Grid<Turma> grid = new Grid<>(Turma.class, false);
    private final TurmaForm turmaForm;

    public TurmaView(TurmaRepository turmaRepository, ModalidadeRepository modalidadeRepository,
            SalaRepository salaRepository, ProfessorRepository professorRepository,
            AlunoTurmaRepository alunoTurmaRepository, TurmaService turmaService) {
        this.turmaRepository = turmaRepository;
        this.modalidadeRepository = modalidadeRepository;
        this.salaRepository = salaRepository;
        this.professorRepository = professorRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.turmaService = turmaService;
        this.turmaForm = new TurmaForm(turmaRepository, modalidadeRepository, this, salaRepository,
                professorRepository, turmaService);

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        ViewUtils.injetarEstiloBotoesAcao();
        configurarGrid();

        Div tituloWrapper = new Div();
        tituloWrapper.addClassNames("cf-page-header", "cf-page-header-padded");
        H2 titulo = new H2("Configurar Turmas");
        tituloWrapper.add(titulo);

        add(tituloWrapper, criarToolbar(), grid);
        updateList();
    }

    private Component criarToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.addClassName("cf-toolbar");
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        TextField pesquisa = new TextField();
        pesquisa.setPlaceholder("🔍 Pesquisar turma ou professor...");
        pesquisa.setWidth("300px");
        pesquisa.setClearButtonVisible(true);
        pesquisa.setValueChangeMode(ValueChangeMode.EAGER);
        pesquisa.addValueChangeListener(e -> {
            String q = e.getValue().toLowerCase();
            grid.setItems(getTurmasDoStudio().stream()
                    .filter(t -> t.getDescricao().toLowerCase().contains(q)
                            || t.getTodosProfessores().stream()
                                    .anyMatch(p -> p.getNome() != null && p.getNome().toLowerCase().contains(q))
                            || (t.getCodigo() != null && t.getCodigo().toLowerCase().contains(q)))
                    .toList());
        });

        HorizontalLayout right = new HorizontalLayout();
        right.setSpacing(true);
        right.addClassName("cf-wrap");

        Button addButton = ViewUtils.botaoNovo("Nova Turma", e -> turmaForm.abrirNovoFormulario(null));

        right.add(pesquisa, addButton);
        toolbar.add(right);
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return toolbar;
    }

    private void configurarGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.getStyle().set("flex-grow", "1");
        grid.addClassName("cf-grid-inset");

        grid.addComponentColumn(turma -> {
            Button edit = ViewUtils.botaoEditar(e -> {
                try {
                    Turma completa = turmaRepository.findByIdCompleto(turma.getId());
                    turmaForm.abrirFormulario(completa);
                } catch (Exception ex) {
                    Notification.show("Erro ao carregar turma: " + ex.getMessage())
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });

            Button delete = ViewUtils.botaoEliminar(e -> confirmarEliminacao(turma));

            return new HorizontalLayout(edit, delete);
        }).setHeader("Ações").setAutoWidth(true);

        grid.addColumn(Turma::getCodigo).setHeader("Código").setWidth("80px").setFlexGrow(0).setSortable(true);

        grid.addComponentColumn(t -> {
            VerticalLayout vl = new VerticalLayout();
            vl.setPadding(false);
            vl.setSpacing(false);
            Span desc = new Span(t.getDescricao());
            desc.addClassName("cf-cell-title");
            Span modal = new Span(t.getModalidade() != null ? t.getModalidade().getDescricao() : "");
            modal.addClassName("cf-cell-sub");
            vl.add(desc, modal);
            return vl;
        }).setHeader("Turma").setFlexGrow(2).setSortable(true).setComparator(Turma::getDescricao);

        grid.addComponentColumn(t -> {
            if (t.getProfessor() == null && t.getCoProfessores().isEmpty())
                return new Span("—");
            com.vaadin.flow.component.orderedlayout.VerticalLayout vl =
                    new com.vaadin.flow.component.orderedlayout.VerticalLayout();
            vl.setPadding(false);
            vl.setSpacing(false);
            if (t.getProfessor() != null) {
                Span s = new Span(t.getProfessor().getNome());
                s.addClassNames("cf-pill", "cf-pill--violet");
                vl.add(s);
            }
            if (!t.getCoProfessores().isEmpty()) {
                String extra = t.getCoProfessores().stream()
                        .map(pt.studioflow.model.Professor::getNome)
                        .collect(java.util.stream.Collectors.joining(", "));
                Span co = new Span("+ " + extra);
                co.addClassName("cf-cell-sub");
                co.getStyle().set("margin-top", "2px");
                vl.add(co);
            }
            return vl;
        }).setHeader("Professor(es)").setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(t -> {
            int n = alunoTurmaRepository.findByTurma(t).size();
            Span badge = new Span(n + " alunos");
            String tom = n == 0 ? "cf-tone-muted" : n < 5 ? "cf-tone-warning" : "cf-tone-info";
            badge.addClassNames(tom, "cf-semibold", "cf-text-s");
            return badge;
        }).setHeader("Alunos").setWidth("90px").setFlexGrow(0);

        grid.addComponentColumn(turma -> {
            if (turma.getWhatsappGroupLink() != null && !turma.getWhatsappGroupLink().isEmpty()) {
                Anchor anchor = new Anchor(turma.getWhatsappGroupLink(), "");
                Icon icon = VaadinIcon.CHAT.create();
                icon.addClassName("cf-whatsapp");
                anchor.getElement().setAttribute("aria-label", "Grupo de WhatsApp");
                anchor.add(icon);
                anchor.setTarget("_blank");
                return (Component) anchor;
            }
            return (Component) new Span("—");
        }).setHeader("WhatsApp").setTextAlign(ColumnTextAlign.CENTER).setWidth("90px").setFlexGrow(0);

        grid.addComponentColumn(turma -> {
            Span badge = turma.isAtivo()
                    ? criarBadge("Ativa", "success")
                    : criarBadge("Inativa", "error");
            return badge;
        }).setHeader("Estado").setWidth("90px").setFlexGrow(0);
    }

    /** Pastilha de estado; "tom" = success | error | warning | info | neutral. */
    private Span criarBadge(String texto, String tom) {
        Span s = new Span(texto);
        s.addClassNames("cf-pill", "cf-pill--" + tom);
        return s;
    }

    private void confirmarEliminacao(Turma turma) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Remover Turma");
        dialog.setText(
                "Tens a certeza que queres remover \"" + turma.getDescricao() + "\"? Esta ação não pode ser desfeita.");
        dialog.setCancelable(true);
        dialog.setCancelText("Cancelar");
        dialog.setConfirmText("Remover");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            turmaForm.eliminarTurma(turma);
            updateList();
            Notification.show("Turma removida.", 2000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        });
        dialog.open();
    }

    private List<Turma> getTurmasDoStudio() {
        Studio studio = TenantContext.getCurrentStudio();
        return studio != null ? turmaRepository.findAllByStudio(studio) : turmaRepository.findAll();
    }

    public void updateList() {
        grid.setItems(getTurmasDoStudio());
    }
}
