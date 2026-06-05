package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import pt.studioflow.model.Aluno;
import pt.studioflow.repository.AlunoRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.springframework.data.domain.Sort;

@Route("carregar-foto")
@AnonymousAllowed
public class CarregarFotoAlunoView extends VerticalLayout {

    private final AlunoRepository alunoRepository;

    private ComboBox<Aluno> comboAluno;
    private byte[] fotoBytesTemporaria = null;
    private Div previewAvatar;

    public CarregarFotoAlunoView(AlunoRepository alunoRepository) {
        this.alunoRepository = alunoRepository;

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);
        getStyle().set("background-color", "#f8f9fa");

        // --- CARD PRINCIPAL ---
        VerticalLayout card = new VerticalLayout();
        card.setMaxWidth("500px");
        card.setWidth("100%");
        card.getStyle()
                .set("background", "white")
                .set("padding", "2.5em")
                .set("border-radius", "15px")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.1)");

        // Cabeçalho simples
        H2 titulo = new H2("Atualizar Foto do Aluno");
        Span subtitulo = new Span("Selecione o aluno e carregue a fotografia oficial.");
        subtitulo.getStyle().set("color", "#6c757d").set("margin-bottom", "1.5em");
        card.add(titulo, subtitulo);

        // --- 1. SELEÇÃO DO ALUNO ---
        comboAluno = new ComboBox<>("Escolha o Aluno");
        comboAluno.setWidthFull();
        // Carrega todos os alunos da base de dados
        comboAluno.setItems(alunoRepository.findAll(Sort.by(Sort.Direction.ASC, "nomeCompleto")));
        // Mostra o Nome e o NIF para os pais não se enganarem se houver nomes repetidos
        comboAluno.setItemLabelGenerator(
                aluno -> aluno.getNomeCompleto() + " (NIF: " + aluno.getNumeroContribuinte() + ")");

        // Se mudarem de aluno, limpa a foto anterior do formulário
        comboAluno.addValueChangeListener(event -> limparFormulario());
        card.add(comboAluno);

        // --- 2. ZONA DE PREVIEW (CÍRCULO) ---
        previewAvatar = new Div(new Icon(VaadinIcon.USER));
        previewAvatar.getStyle()
                .set("background", "#e9ecef")
                .set("width", "120px")
                .set("height", "120px")
                .set("border-radius", "50%")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("overflow", "hidden")
                .set("border", "3px solid #ced4da")
                .set("margin", "0 auto");

        // --- 3. COMPONENTE DE UPLOAD ---
        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("image/jpeg", "image/png");
        upload.setMaxFileSize(4 * 1024 * 1024); // Limite de 4MB
        upload.setWidthFull();

        Button btnUpload = new Button("Escolher Imagem...", new Icon(VaadinIcon.CAMERA));
        upload.setUploadButton(btnUpload);
        upload.setDropLabel(new Span("Ou arraste a foto para aqui"));

        // Evento quando o upload termina
        upload.addSucceededListener(event -> {
            try {
                fotoBytesTemporaria = buffer.getInputStream().readAllBytes();

                StreamResource resource = new StreamResource("preview.png",
                        () -> new ByteArrayInputStream(fotoBytesTemporaria));
                Image imgPreview = new Image(resource, "Preview");
                imgPreview.getStyle().set("width", "100%").set("height", "100%").set("object-fit", "cover");

                previewAvatar.removeAll();
                previewAvatar.add(imgPreview);
            } catch (IOException e) {
                Notification.show("Erro ao ler o ficheiro.").addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        // Evento quando removem o ficheiro clicando no "X"
        upload.getElement().addEventListener("file-remove", event -> {
            fotoBytesTemporaria = null;
            previewAvatar.removeAll();
            previewAvatar.add(new Icon(VaadinIcon.USER));
        });

        // Agrupar Preview e Upload numa linha bonita
        HorizontalLayout zonaFoto = new HorizontalLayout(previewAvatar, upload);
        zonaFoto.setWidthFull();
        zonaFoto.setAlignItems(Alignment.CENTER);
        zonaFoto.setSpacing(true);
        zonaFoto.getStyle().set("margin-top", "1em").set("margin-bottom", "1.5em");
        card.add(zonaFoto);

        // --- 4. BOTÃO GUARDAR ---
        Button btnGuardar = new Button("Gravar Foto no Sistema");
        btnGuardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        btnGuardar.setWidthFull();
        btnGuardar.setHeight("50px");
        btnGuardar.addClickListener(e -> guardarFoto(upload));
        card.add(btnGuardar);

        add(card);
    }

    private void guardarFoto(Upload upload) {
        Aluno alunoSelecionado = comboAluno.getValue();

        if (alunoSelecionado == null) {
            Notification.show("Por favor, selecione primeiro o aluno.", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        if (fotoBytesTemporaria == null) {
            Notification.show("Por favor, faça o upload de uma foto primeiro.", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        try {
            // Associa os bytes ao aluno e grava no repositório
            alunoSelecionado.setFoto(fotoBytesTemporaria);
            alunoRepository.save(alunoSelecionado);

            Notification.show("Foto de " + alunoSelecionado.getNomeCompleto() + " atualizada com sucesso!",
                    4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            // Limpa o formulário para a próxima atualização
            comboAluno.clear();
            upload.clearFileList();
            limparFormulario();

        } catch (Exception ex) {
            Notification.show("Erro ao gravar na Base de Dados: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void limparFormulario() {
        fotoBytesTemporaria = null;
        previewAvatar.removeAll();
        previewAvatar.add(new Icon(VaadinIcon.USER));
    }
}