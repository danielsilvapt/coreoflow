package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.Binder;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Sala;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.ModalidadeRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.SalaRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.TurmaService;
import pt.studioflow.view.component.ColorPickerField;

public class TurmaForm extends Dialog {

    private final TurmaRepository turmaRepository;
    private final TurmaService turmaService;
    private final TurmaView turmaView;

    private final Binder<Turma> binder = new BeanValidationBinder<>(Turma.class);

    // Campos do Formulário
    private TextField codigo = new TextField("Código");
    private TextField descricao = new TextField("Descrição");
    private ComboBox<Professor> professor = new ComboBox<>("Professor");
    private ComboBox<Modalidade> modalidade = new ComboBox<>("Modalidade");
    private ComboBox<Sala> sala = new ComboBox<>("Sala");

    // WHATSAPP
    private TextField whatsappGroupLink = new TextField("Link do Grupo WhatsApp");

    // NOVO CAMPO: ID GOOGLE DRIVE
    private TextField googleDriveFolderId = new TextField("ID da Pasta Google Drive");

    private Checkbox ativo = new Checkbox("Ativa");

    // Color picker nativo
    private final ColorPickerField cor = new ColorPickerField("Cor da Turma");

    private Turma turmaAtual;

    public TurmaForm(TurmaRepository turmaRepository, ModalidadeRepository modalidadeRepository,
            TurmaView turmaView, SalaRepository salaRepository,
            ProfessorRepository professorRepository, TurmaService turmaService) {
        this.turmaRepository = turmaRepository;
        this.turmaView = turmaView;
        this.turmaService = turmaService;

        setHeaderTitle("Configuração da Turma");

        // Configurar as ComboBoxes
        Studio studio = TenantContext.getCurrentStudio();
        professor.setItems(studio != null ? professorRepository.findAllByStudio(studio) : professorRepository.findAll());
        professor.setItemLabelGenerator(Professor::getNome);

        modalidade.setItems(studio != null ? modalidadeRepository.findAllByStudio(studio) : modalidadeRepository.findAll());
        modalidade.setItemLabelGenerator(Modalidade::getDescricao);

        sala.setItems(studio != null ? salaRepository.findAllByStudio(studio) : salaRepository.findAll());
        sala.setItemLabelGenerator(Sala::getNome);

        // Estilizar o campo WhatsApp
        whatsappGroupLink.setPlaceholder("https://chat.whatsapp.com/...");
        whatsappGroupLink.setPrefixComponent(new Icon(VaadinIcon.CHAT));
        whatsappGroupLink.setClearButtonVisible(true);

        // Estilizar o campo Google Drive
        googleDriveFolderId.setPlaceholder("ID alfanumérico da pasta (sem o link todo)");
        googleDriveFolderId.setPrefixComponent(new Icon(VaadinIcon.FOLDER_OPEN));
        googleDriveFolderId.setHelperText("Ex: 1AL0JmcqwjYW87jjoKoG2w...");
        googleDriveFolderId.setClearButtonVisible(true);

        // Configurar o Binder
        binder.bindInstanceFields(this);
        binder.bind(cor, Turma::getCor, Turma::setCor);

        // Montar o Layout
        FormLayout formLayout = new FormLayout();
        formLayout.add(codigo, descricao, professor, modalidade, sala, cor, ativo, whatsappGroupLink, googleDriveFolderId);

        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));

        // Colocamos os links de largura total para facilitar a visualização
        formLayout.setColspan(whatsappGroupLink, 2);
        formLayout.setColspan(googleDriveFolderId, 2);
        formLayout.setColspan(cor, 1);

        // Botões de Ação
        Button save = new Button("Guardar", e -> guardar());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancel = new Button("Cancelar", e -> close());

        getFooter().add(cancel, save);
        add(new VerticalLayout(formLayout));
    }

    public void abrirNovoFormulario(Turma turma) {
        if (turma == null) {
            this.turmaAtual = new Turma();
            this.turmaAtual.setAtivo(true);
        } else {
            this.turmaAtual = turma;
        }
        binder.setBean(this.turmaAtual);
        open();
    }

    public void abrirFormulario(Turma turma) {
        this.turmaAtual = turma;
        binder.setBean(this.turmaAtual);
        open();
    }

    private void guardar() {
        if (binder.validate().isOk()) {
            // Se o utilizador colar o link completo por engano, limpamos automaticamente
            String rawId = googleDriveFolderId.getValue();
            if (rawId != null && rawId.contains("folders/")) {
                String cleaned = rawId.substring(rawId.lastIndexOf("/") + 1).split("\\?")[0];
                turmaAtual.setGoogleDriveFolderId(cleaned);
            }

            if (turmaAtual.getStudio() == null) {
                turmaAtual.setStudio(TenantContext.getCurrentStudio());
            }
            turmaRepository.save(turmaAtual);
            turmaView.updateList();
            close();
        }
    }

    public void eliminarTurma(Turma turma) {
        turmaService.delete(turma);
    }
}
