package pt.studioflow.view;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;

import pt.studioflow.model.Modalidade;
import pt.studioflow.model.Professor;
import pt.studioflow.repository.ModalidadeRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.service.ModalidadeService;

public class ModalidadeForm extends FormLayout {
    private final TextField codigo = new TextField("Código");
    private final TextField descricao = new TextField("Descrição");
    private final ComboBox<Professor> comboProf = new ComboBox<>("Professor Responsável");

    private final Checkbox ativo = new Checkbox("Ativo");

    private final Image imagePreview = new Image();
    private final Dialog dialog = new Dialog();
    private byte[] fotoByteArray;

    private Modalidade modalidade;
    private Modalidade novaModalidade;
    private final ModalidadeRepository modalidadeRepository;

    private final ModalidadeService modalidadeService;

    private final ProfessorRepository professorRepository;

    @Autowired
    private ModalidadeView modalidadeView;

    private Consumer<Modalidade> saveListener;

    public ModalidadeForm(ModalidadeRepository modalidadeRepository, ModalidadeView modalidadeView,
            ModalidadeService modalidadeService, ProfessorRepository professorRepository) {

        this.saveListener = saveListener;
        this.modalidadeRepository = modalidadeRepository;
        this.modalidadeView = modalidadeView;
        this.modalidadeService = modalidadeService;
        this.professorRepository = professorRepository;

        this.novaModalidade = new Modalidade();

        // popula combo
        comboProf.setItemLabelGenerator(Professor::getNome);
        pt.studioflow.model.Studio _studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        comboProf.setItems(_studio != null ? professorRepository.findAllByStudio(_studio) : professorRepository.findAll());
        comboProf.setAllowCustomValue(false); // só permite selecionar
        comboProf.setWidthFull();

        this.add(codigo, descricao, comboProf, ativo);

        dialog.add(this);

    }

    public void eliminarModalidade(Modalidade modalidade) {
        modalidadeService.deleteById(modalidade.getId());
    }

    public void abrirFormulario(Modalidade modalidade) {

        Dialog formDialog = new Dialog();
        formDialog.setHeaderTitle(modalidade == null ? "Nova Modalidade" : "Ver/Editar Modalidade");

        formDialog.setWidth("800");
        // formDialog.setHeight("800");
        // Botão de fechar (cruz no canto superior direito)
        Button closeButton = new Button(VaadinIcon.CLOSE.create(), event -> formDialog.close());
        closeButton.addThemeName("tertiary"); // Remove borda e fundo

        // Adicionar botão ao header da modal
        formDialog.getHeader().add(closeButton);

        if (modalidade != null) {
            obtemDadosModalidade(modalidade);
        } else {
            emptyFields();
            novaModalidade = new Modalidade();
        }

        // TODO criar método para validações!!

        /*
         * // Crie um Binder para a validação
         * Binder<Aluno> binder = new Binder<>(Aluno.class);
         * 
         * // Torne o campo obrigatório
         * binder.forField(numeroSocio)
         * .asRequired("Nº de Sócio é obrigatório") // Mensagem personalizada de erro
         * .bind("Aluno::numeroSocio");
         * 
         */
        this.add(codigo, descricao, ativo);

        // Adicionar botões de salvar
        Button saveButton = new Button("Guardar", event -> {

            if (modalidade != null) {
                guardaDadosModalidade(modalidade);
            } else {
                guardaDadosModalidade(novaModalidade);
            }

            formDialog.close();
            Notification.show("Dados guardados com sucesso!");
            this.modalidadeView.grid.getDataProvider().refreshAll();
        });

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancelar", event -> formDialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout buttonsLayout = new HorizontalLayout(saveButton, cancelButton);

        // Layout para os botões
        buttonsLayout.setWidthFull(); // Para ocupar toda a largura do Dialog
        buttonsLayout.getStyle().set("margin-top", "30px");
        buttonsLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        // Adicionar ao Dialog
        dialog.getFooter().add(buttonsLayout);
        formDialog.add(this, buttonsLayout);
        formDialog.open();
    }

    public void abrirNovoFormulario(Modalidade modalidade) {

        Dialog formDialog = new Dialog();
        formDialog.setHeaderTitle(modalidade == null ? "Nova Modalidade" : "Ver/Editar Modalidade");
        formDialog.setWidth("800");
        // formDialog.setHeight("800");
        // Botão de fechar (cruz no canto superior direito)
        Button closeButton = new Button(VaadinIcon.CLOSE.create(), event -> formDialog.close());
        closeButton.addThemeName("tertiary"); // Remove borda e fundo

        // Adicionar botão ao header da modal
        formDialog.getHeader().add(closeButton);

        emptyFields();
        novaModalidade = new Modalidade();

        // TODO criar método para validações!!

        /*
         * // Crie um Binder para a validação
         * Binder<Aluno> binder = new Binder<>(Aluno.class);
         * 
         * // Torne o campo obrigatório
         * binder.forField(numeroSocio)
         * .asRequired("Nº de Sócio é obrigatório") // Mensagem personalizada de erro
         * .bind("Aluno::numeroSocio");
         * 
         */
        this.add(codigo, descricao, ativo);

        // Adicionar botões de salvar
        Button saveButton = new Button("Guardar", event -> {

            if (modalidade != null) {
                guardaDadosModalidade(modalidade);
            } else {
                guardaDadosModalidade(novaModalidade);
            }

            Notification notification = new Notification("Modalidade guardada");
            notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS); // verde
            notification.setDuration(3000); // 3 segundos
            notification.setPosition(Notification.Position.TOP_END);
            notification.open();

            formDialog.close();

            this.modalidadeView.grid.getDataProvider().refreshAll();
        });

        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelButton = new Button("Cancelar", event -> formDialog.close());
        cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout buttonsLayout = new HorizontalLayout(saveButton, cancelButton);

        // Layout para os botões
        buttonsLayout.setWidthFull(); // Para ocupar toda a largura do Dialog
        buttonsLayout.getStyle().set("margin-top", "30px");
        buttonsLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        // Adicionar ao Dialog
        dialog.getFooter().add(buttonsLayout);
        formDialog.add(this, buttonsLayout);
        formDialog.open();
    }

    private void emptyFields() {
        codigo.clear();
        descricao.clear();
        ativo.setValue(false);
        comboProf.clear();
        fotoByteArray = null;
        imagePreview.setSrc("");
    }

    private void obtemDadosModalidade(Modalidade modalidade) {

        codigo.setValue(modalidade.getCodigo());
        descricao.setValue(modalidade.getDescricao());
        ativo.setValue(modalidade.isAtivo());
        imagePreview.setSrc(getImageSrc(modalidade.getIcon()));
        if (modalidade.getProfResponsavel() != null) {
            pt.studioflow.model.Studio studioCtx = pt.studioflow.config.TenantContext.getCurrentStudio();
            comboProf.setValue(
                    (studioCtx != null ? professorRepository.findAllByStudio(studioCtx) : professorRepository.findAll())
                            .stream()
                            .filter(p -> p.getNome().equals(modalidade.getProfResponsavel()))
                            .findFirst()
                            .orElse(null));
        } else {
            comboProf.clear();
        }
    }

    private void guardaDadosModalidade(Modalidade modalidade) {

        modalidade.setCodigo(codigo.getValue());
        modalidade.setDescricao(descricao.getValue());
        modalidade.setAtivo(ativo.getValue());
        modalidade.setIcon(fotoByteArray);
        modalidade.setProfResponsavel(comboProf.getValue() != null ? comboProf.getValue().getNome() : "");
        if (modalidade.getStudio() == null) {
            modalidade.setStudio(pt.studioflow.config.TenantContext.getCurrentStudio());
        }

        modalidadeService.save(modalidade);

        this.modalidadeView.grid.getDataProvider().refreshAll();
    }

    // Converter InputStream em byte[]
    private byte[] convertToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        return outputStream.toByteArray();
    }

    // Converter byte[] para Base64 e exibir como imagem
    public String getImageSrc(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0)
            return "";
        String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
        return "data:image/jpeg;base64," + base64;
    }
}
