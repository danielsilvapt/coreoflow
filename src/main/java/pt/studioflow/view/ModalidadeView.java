package pt.studioflow.view;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.ModalidadeRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.service.ModalidadeService;

@PageTitle("Modalidades | CoreoFlow")
@Route(value = "modalidade", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class ModalidadeView extends VerticalLayout {

    @Autowired
    private final ModalidadeRepository modalidadeRepository;

    @Autowired
    private final ProfessorRepository professorRepository;

    private final ModalidadeService modalidadeService;

    private List<Modalidade> listaModalidades = new ArrayList<>();
    public final Grid<Modalidade> grid = new Grid<>(Modalidade.class);
    private final ModalidadeForm modalidadeForm;
    private ListDataProvider<Modalidade> dataProvider;

    public ModalidadeView(ModalidadeRepository modalidadeRepository, ModalidadeService modalidadeService,
            ProfessorRepository professorRepository) {
        this.modalidadeRepository = modalidadeRepository;
        this.modalidadeService = modalidadeService;
        this.professorRepository = professorRepository;
        this.modalidadeForm = new ModalidadeForm(modalidadeRepository, this, modalidadeService, professorRepository);

        this.setSizeFull();
        setPadding(false);
        setSpacing(false);

        ListDataProvider<Modalidade> dataProvider = new ListDataProvider<>(listaModalidades);
        grid.setItems(listaModalidades);
        grid.setDataProvider(dataProvider);

        H2 titulo = new H2("Configurar Modalidades");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        updateList();
        configureGrid();
        add(grid);
    }


    private void configureGrid() {

        grid.setHeight("2000");

        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        // Configurar o DataProvider para permitir filtragem
        dataProvider = new ListDataProvider<>(listaModalidades);
        grid.setDataProvider(dataProvider);

        // Adicionar os componentes à view
        add(grid);

        /*
         * grid.addColumn(new ComponentRenderer<>(aluno -> {
         * Image image = new Image(alunoForm.getImageSrc(aluno.getFoto()), "Foto");
         * image.setWidth("50px");
         * image.setHeight("50px");
         * return image;
         * })).setHeader("Foto");
         */

        Button addButton = ViewUtils.botaoNovo("Nova Modalidade",
                e -> modalidadeForm.abrirNovoFormulario(null));

        add(ViewUtils.toolbar(addButton), grid);

        grid.setColumns("codigo", "descricao", "profResponsavel");

        Grid.Column<Modalidade> colAtivo = grid.addColumn(new ComponentRenderer<>(modalidade -> {
            if (modalidade.isAtivo()) {
                Icon checkIcon = VaadinIcon.CHECK.create();
                checkIcon.getStyle().set("color", "green");
                return checkIcon;
            } else {
                Icon checkIcon = VaadinIcon.CLOSE.create();
                checkIcon.getStyle().set("color", "red");
                return checkIcon;
            }
        })).setHeader("Ativo");

        Grid.Column<Modalidade> colAcoes = grid.addComponentColumn(modalidade -> {
            Button editar = new Button(new Icon(VaadinIcon.EDIT));
            editar.getElement().setProperty("title", "Editar");
            editar.addClickListener(e -> modalidadeForm.abrirFormulario(modalidade));

            Button remover = new Button(new Icon(VaadinIcon.TRASH));
            remover.getElement().setProperty("title", "Remover");
            remover.addThemeVariants();
            remover.addClickListener(e -> {
                // Criar o diálogo de confirmação
                Dialog confirmDialog = new Dialog();
                confirmDialog.setHeaderTitle("Tem certeza?");

                // Definir o conteúdo do diálogo
                confirmDialog.add("Deseja realmente eliminar esta modalidade?");

                Button confirmButton = new Button("Sim", event -> {
                    // Ação de eliminar modalidade
                    modalidadeForm.eliminarModalidade(modalidade);
                    Notification.show("Modalidade eliminada com sucesso!");
                    confirmDialog.close();
                    this.grid.getDataProvider().refreshAll();
                    updateList();
                });

                Button cancelButton = new Button("Não", event -> confirmDialog.close());

                confirmDialog.getFooter().add(confirmButton, cancelButton);

                // Exibir o diálogo
                confirmDialog.open();
            });

            HorizontalLayout actions = new HorizontalLayout(editar, remover);
            actions.setSpacing(true);
            return actions;
        }).setHeader("Ações").setAutoWidth(true).setFlexGrow(0);

        grid.setColumnOrder(colAcoes, grid.getColumnByKey("codigo"), grid.getColumnByKey("descricao"),
                grid.getColumnByKey("profResponsavel"), colAtivo);
    }

    public void updateList() {
        pt.studioflow.model.Studio studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        ListDataProvider<Modalidade> provider = (ListDataProvider<Modalidade>) grid.getDataProvider();
        provider.getItems().clear();
        List<pt.studioflow.model.Modalidade> lista = studio != null
                ? modalidadeRepository.findByStudio(studio)
                : modalidadeRepository.findAll();
        provider.getItems().clear();
        provider.getItems().addAll(lista);
        provider.refreshAll();
    }
}
