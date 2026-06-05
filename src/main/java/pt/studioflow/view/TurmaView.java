package pt.studioflow.view;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

@Route(value = "turma", layout = MainLayout.class)
@PageTitle("Turmas | CoreoFlow")
@RolesAllowed("ADMIN")
public class TurmaView extends VerticalLayout {

    private final TurmaRepository turmaRepository;
    private final SalaRepository salaRepository;
    private final ProfessorRepository professorRepository;
    private final ModalidadeRepository modalidadeRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;

    public final Grid<Turma> grid = new Grid<>(Turma.class, false);
    private final TurmaForm turmaForm;

    public TurmaView(TurmaRepository turmaRepository, ModalidadeRepository modalidadeRepository,
            SalaRepository salaRepository, ProfessorRepository professorRepository,
            AlunoTurmaRepository alunoTurmaRepository) {
        this.turmaRepository = turmaRepository;
        this.modalidadeRepository = modalidadeRepository;
        this.salaRepository = salaRepository;
        this.professorRepository = professorRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.turmaForm = new TurmaForm(turmaRepository, modalidadeRepository, this, salaRepository,
                professorRepository);

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "#f5f7fa");

        configurarGrid();

        Div tituloWrapper = new Div();
        tituloWrapper.getStyle().set("padding", "20px 20px 0 20px");
        H2 titulo = new H2("Configurar Turmas");
        titulo.getStyle().set("margin", "0");
        tituloWrapper.add(titulo);

        add(tituloWrapper, criarToolbar(), grid);
        updateList();
    }

    private Component criarToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setPadding(true);
        toolbar.getStyle().set("background", "white").set("border-bottom", "1px solid #e0e0e0").set("padding",
                "12px 20px");
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
                            || (t.getProfessor() != null && t.getProfessor().getNome().toLowerCase().contains(q))
                            || (t.getCodigo() != null && t.getCodigo().toLowerCase().contains(q)))
                    .toList());
        });

        HorizontalLayout right = new HorizontalLayout();
        right.setSpacing(true);

        Button addButton = ViewUtils.botaoNovo("Nova Turma", e -> turmaForm.abrirNovoFormulario(null));

        right.add(pesquisa, addButton);
        toolbar.add(right);
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return toolbar;
    }

    private void configurarGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.getStyle().set("flex-grow", "1").set("margin", "12px 16px");

        grid.addColumn(Turma::getCodigo).setHeader("Código").setWidth("80px").setFlexGrow(0).setSortable(true);

        grid.addComponentColumn(t -> {
            VerticalLayout vl = new VerticalLayout();
            vl.setPadding(false);
            vl.setSpacing(false);
            Span desc = new Span(t.getDescricao());
            desc.getStyle().set("font-weight", "600");
            Span modal = new Span(t.getModalidade() != null ? t.getModalidade().getDescricao() : "");
            modal.getStyle().set("font-size", "0.78rem").set("color", "#888");
            vl.add(desc, modal);
            return vl;
        }).setHeader("Turma").setFlexGrow(2).setSortable(true).setComparator(Turma::getDescricao);

        grid.addComponentColumn(t -> {
            if (t.getProfessor() == null)
                return new Span("—");
            Span s = new Span(t.getProfessor().getNome());
            s.getStyle().set("background", "#ede7f6").set("color", "#6d1b7b")
                    .set("padding", "3px 10px").set("border-radius", "12px").set("font-size", "0.85rem");
            return s;
        }).setHeader("Professor").setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(t -> {
            int n = alunoTurmaRepository.findByTurma(t).size();
            Span badge = new Span(n + " alunos");
            String cor = n == 0 ? "#9e9e9e" : n < 5 ? "#e65100" : "#1976d2";
            badge.getStyle().set("color", cor).set("font-weight", "600").set("font-size", "0.85rem");
            return badge;
        }).setHeader("Alunos").setWidth("90px").setFlexGrow(0);

        grid.addComponentColumn(turma -> {
            if (turma.getWhatsappGroupLink() != null && !turma.getWhatsappGroupLink().isEmpty()) {
                Anchor anchor = new Anchor(turma.getWhatsappGroupLink(), "");
                Icon icon = VaadinIcon.CHAT.create();
                icon.getStyle().set("color", "#25D366");
                anchor.add(icon);
                anchor.setTarget("_blank");
                return (Component) anchor;
            }
            return (Component) new Span("—");
        }).setHeader("WhatsApp").setTextAlign(ColumnTextAlign.CENTER).setWidth("90px").setFlexGrow(0);

        grid.addComponentColumn(turma -> {
            Span badge = turma.isAtivo()
                    ? criarBadge("Ativa", "#e8f5e9", "#2e7d32")
                    : criarBadge("Inativa", "#ffebee", "#c62828");
            return badge;
        }).setHeader("Estado").setWidth("90px").setFlexGrow(0);

        grid.addComponentColumn(turma -> {
            Button edit = new Button("Editar", new Icon(VaadinIcon.EDIT));
            edit.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            edit.addClickListener(e -> {
                try {
                    Turma completa = turmaRepository.findByIdCompleto(turma.getId());
                    turmaForm.abrirFormulario(completa);
                } catch (Exception ex) {
                    Notification.show("Erro ao carregar turma: " + ex.getMessage())
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });

            Button delete = new Button(new Icon(VaadinIcon.TRASH));
            delete.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            delete.addClickListener(e -> confirmarEliminacao(turma));

            return new HorizontalLayout(edit, delete);
        }).setHeader("Ações").setAutoWidth(true);
    }

    private Span criarBadge(String texto, String bg, String cor) {
        Span s = new Span(texto);
        s.getStyle().set("background", bg).set("color", cor)
                .set("padding", "3px 10px").set("border-radius", "12px")
                .set("font-size", "0.8rem").set("font-weight", "600");
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

    private List<Turma> getTurma