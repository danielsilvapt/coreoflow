package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.OcorrenciaAula;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.OcorrenciaAulaRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.TurmaRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "plano-aulas", layout = MainLayout.class)
@PageTitle("Plano de Aulas | CoreoFlow")
@RolesAllowed({"ADMIN", "PROF"})
public class PlanoAulasView extends VerticalLayout {

    private final OcorrenciaAulaRepository ocorrenciaRepo;
    private final TurmaRepository turmaRepo;
    private final ProfessorRepository professorRepo;

    private Grid<OcorrenciaAula> grid;
    private DatePicker dataInicio, dataFim;

    public PlanoAulasView(OcorrenciaAulaRepository ocorrenciaRepo,
                          TurmaRepository turmaRepo,
                          ProfessorRepository professorRepo) {
        this.ocorrenciaRepo = ocorrenciaRepo;
        this.turmaRepo = turmaRepo;
        this.professorRepo = professorRepo;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        add(criarToolbar(), criarGrid());
        atualizar();
    }

    private HorizontalLayout criarToolbar() {
        dataInicio = new DatePicker("De");
        dataInicio.setValue(LocalDate.now().withDayOfMonth(1));
        dataInicio.setWidth("150px");
        dataInicio.addValueChangeListener(e -> atualizar());

        dataFim = new DatePicker("Até");
        dataFim.setValue(LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()));
        dataFim.setWidth("150px");
        dataFim.addValueChangeListener(e -> atualizar());

        HorizontalLayout filtros = new HorizontalLayout(dataInicio, dataFim);
        filtros.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.END);

        return ViewUtils.toolbar(filtros,
                ViewUtils.botaoNovo("Registar Ocorrência", e -> abrirDialog(null)));
    }

    private Grid<OcorrenciaAula> criarGrid() {
        grid = new Grid<>(OcorrenciaAula.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(o -> o.getData().format(DateTimeFormatter.ofPattern("EEE dd/MM/yyyy",
                        new java.util.Locale("pt"))))
                .setHeader("Data").setAutoWidth(true).setSortable(true);

        grid.addColumn(o -> o.getTurma().getDescricao()).setHeader("Turma").setFlexGrow(1);

        grid.addComponentColumn(o -> {
            String[] cfg = switch (o.getTipo()) {
                case SUBSTITUICAO -> new String[]{"#fff3e0", "#E67E22", "🔄 Substituição"};
                case CANCELAMENTO -> new String[]{"#fce4ec", "#C62828", "❌ Cancelamento"};
                case REPOSICAO    -> new String[]{"#e8f5e9", "#27AE60", "✅ Reposição"};
            };
            Span b = new Span(cfg[2]);
            b.getStyle().set("background", cfg[0]).set("color", cfg[1])
                    .set("padding", "2px 8px").set("border-radius", "10px")
                    .set("font-size", "11px").set("font-weight", "600");
            return b;
        }).setHeader("Tipo").setAutoWidth(true);

        grid.addColumn(o -> o.getProfessorSubstituto() != null
                ? o.getProfessorSubstituto().getNome() : "—")
                .setHeader("Prof. Substituto").setAutoWidth(true);

        grid.addColumn(o -> o.getMotivo() != null ? o.getMotivo() : "—")
                .setHeader("Motivo").setFlexGrow(1);

        grid.addComponentColumn(o -> {
            Span s = new Span(o.isNotificarAlunos() ? "✉️ Notificado" : "—");
            s.getStyle().set("font-size", "12px").set("color", "#888");
            return s;
        }).setHeader("Alunos").setAutoWidth(true);

        grid.addComponentColumn(o -> {
            Button editar = new Button(VaadinIcon.EDIT.create(), e -> abrirDialog(o));
            editar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button del = new Button(VaadinIcon.TRASH.create(), e -> { ocorrenciaRepo.delete(o); atualizar(); });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return new HorizontalLayout(editar, del);
        }).setHeader("Ações").setAutoWidth(true);

        return grid;
    }

    private void atualizar() {
        Studio s = TenantContext.getCurrentStudio();
        LocalDate ini = dataInicio.getValue() != null ? dataInicio.getValue() : LocalDate.now().withDayOfMonth(1);
        LocalDate fim = dataFim.getValue() != null ? dataFim.getValue() : ini.plusMonths(1);
        List<OcorrenciaAula> items = s != null
                ? ocorrenciaRepo.findByStudioAndDataBetweenOrderByDataAsc(s, ini, fim)
                : ocorrenciaRepo.findAll();
        grid.setItems(items);
    }

    private void abrirDialog(OcorrenciaAula oc) {
        boolean novo = oc == null;
        OcorrenciaAula ocorrencia = novo ? new OcorrenciaAula() : oc;
        Studio studio = TenantContext.getCurrentStudio();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(novo ? "Nova Ocorrência" : "Editar Ocorrência");
        dialog.setWidth("480px");

        ComboBox<Turma> turma = new ComboBox<>("Turma");
        turma.setItems(studio != null ? turmaRepo.findAllByStudio(studio) : turmaRepo.findAll());
        turma.setItemLabelGenerator(Turma::getDescricao);
        turma.setRequired(true);
        turma.setWidthFull();
        if (ocorrencia.getTurma() != null) turma.setValue(ocorrencia.getTurma());

        DatePicker data = new DatePicker("Data");
        data.setRequired(true);
        data.setWidthFull();
        if (ocorrencia.getData() != null) data.setValue(ocorrencia.getData());

        ComboBox<OcorrenciaAula.Tipo> tipo = new ComboBox<>("Tipo");
        tipo.setItems(OcorrenciaAula.Tipo.values());
        tipo.setItemLabelGenerator(t -> switch (t) {
            case SUBSTITUICAO -> "Substituição de Professor";
            case CANCELAMENTO -> "Cancelamento";
            case REPOSICAO    -> "Reposição";
        });
        tipo.setValue(ocorrencia.getTipo() != null ? ocorrencia.getTipo() : OcorrenciaAula.Tipo.SUBSTITUICAO);
        tipo.setWidthFull();

        ComboBox<Professor> profSubstituto = new ComboBox<>("Professor Substituto");
        profSubstituto.setItems(professorRepo.findAll());
        profSubstituto.setItemLabelGenerator(Professor::getNome);
        profSubstituto.setWidthFull();
        profSubstituto.setVisible(true);
        if (ocorrencia.getProfessorSubstituto() != null)
            profSubstituto.setValue(ocorrencia.getProfessorSubstituto());
        tipo.addValueChangeListener(e ->
                profSubstituto.setVisible(e.getValue() == OcorrenciaAula.Tipo.SUBSTITUICAO));

        DatePicker dataOriginal = new DatePicker("Data da Aula Cancelada (para reposição)");
        dataOriginal.setWidthFull();
        dataOriginal.setVisible(ocorrencia.getTipo() == OcorrenciaAula.Tipo.REPOSICAO);
        if (ocorrencia.getDataOriginalCancelada() != null)
            dataOriginal.setValue(ocorrencia.getDataOriginalCancelada());
        tipo.addValueChangeListener(e ->
                dataOriginal.setVisible(e.getValue() == OcorrenciaAula.Tipo.REPOSICAO));

        TextArea motivo = new TextArea("Motivo / Observações");
        motivo.setWidthFull();
        motivo.setValue(ocorrencia.getMotivo() != null ? ocorrencia.getMotivo() : "");

        Checkbox notificar = new Checkbox("Notificar alunos por email");
        notificar.setValue(ocorrencia.isNotificarAlunos());

        VerticalLayout content = new VerticalLayout(
                new FormLayout(turma, data, tipo, profSubstituto, dataOriginal), motivo, notificar);
        content.setPadding(false);

        Button guardar = new Button("Guardar", e -> {
            if (turma.getValue() == null || data.getValue() == null || tipo.getValue() == null) {
                Notification.show("Turma, Data e Tipo são obrigatórios"); return;
            }
            ocorrencia.setTurma(turma.getValue());
            ocorrencia.setData(data.getValue());
            ocorrencia.setTipo(tipo.getValue());
            ocorrencia.setProfessorSubstituto(profSubstituto.getValue());
            ocorrencia.setDataOriginalCancelada(dataOriginal.getValue());
            ocorrencia.setMotivo(motivo.getValue().trim());
            ocorrencia.setNotificarAlunos(notificar.getValue());
            ocorrencia.setStudio(studio);
            ocorrenciaRepo.save(ocorrencia);
            atualizar();
            dialog.close();
            Notification.show("Guardado").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }
}
