package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.model.User;
import pt.studioflow.model.VideoAula;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.UserRepository;
import pt.studioflow.repository.VideoAulaRepository;
import pt.studioflow.service.R2StorageService;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Área do professor para enviar vídeos de uma aula concreta (turma + data)
 * para o bucket R2 da plataforma. O aluno vê o resultado em
 * {@link PortalAlunoView} (separador Presenças, indicador de vídeo por data).
 */
@Route(value = "videos-aula", layout = MainLayout.class)
@PageTitle("Vídeos das Aulas | CoreoFlow")
@RolesAllowed({"ADMIN", "PROF"})
public class VideosAulaProfessorView extends VerticalLayout {

    private final TurmaRepository turmaRepo;
    private final VideoAulaRepository videoAulaRepo;
    private final UserRepository userRepo;
    private final R2StorageService storageService;

    private final ComboBox<Turma> turmaCombo = new ComboBox<>("Turma");
    private final DatePicker dataPicker = new DatePicker("Data");
    private final FlexLayout listaVideos = new FlexLayout();
    private final Span semTurmaAviso = new Span("Escolhe uma turma e uma data para ver/enviar vídeos.");
    private final Upload upload;
    private final FileBuffer buffer = new FileBuffer();

    public VideosAulaProfessorView(TurmaRepository turmaRepo, VideoAulaRepository videoAulaRepo,
            UserRepository userRepo, R2StorageService storageService) {
        this.turmaRepo = turmaRepo;
        this.videoAulaRepo = videoAulaRepo;
        this.userRepo = userRepo;
        this.storageService = storageService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new com.vaadin.flow.component.html.H2("Vídeos das Aulas"));

        turmaCombo.setItemLabelGenerator(Turma::getDescricao);
        turmaCombo.setWidth("280px");
        carregarTurmasPermitidas();

        dataPicker.setValue(LocalDate.now());
        dataPicker.setWidth("180px");

        turmaCombo.addValueChangeListener(e -> atualizarLista());
        dataPicker.addValueChangeListener(e -> atualizarLista());

        upload = new Upload(buffer);
        upload.setAcceptedFileTypes("video/mp4", "video/quicktime", "video/x-msvideo", "video/webm");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(300 * 1024 * 1024); // 300MB
        upload.setUploadButton(new Button("Escolher vídeo"));
        upload.setDropLabel(new Span("ou arrastar aqui (vídeo, máx 300MB)"));
        upload.addSucceededListener(this::enviarParaStorage);
        upload.addFailedListener(e -> Notification.show("Falha no upload: " + e.getReason().getMessage(), 5000,
                Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR));

        HorizontalLayout filtros = new HorizontalLayout(turmaCombo, dataPicker);
        filtros.setAlignItems(Alignment.END);

        semTurmaAviso.getStyle().set("color", "#888");

        listaVideos.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        listaVideos.getStyle().set("gap", "16px").set("margin-top", "8px");

        add(filtros, upload, semTurmaAviso, listaVideos);

        atualizarLista();
    }

    private void enviarParaStorage(com.vaadin.flow.component.upload.SucceededEvent event) {
        Turma turma = turmaCombo.getValue();
        LocalDate data = dataPicker.getValue();
        if (turma == null || data == null) {
            Notification.show("Escolhe primeiro a turma e a data.").addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }
        Studio studio = TenantContext.getCurrentStudio();
        try {
            String nomeOriginal = event.getFileName();
            String chave = "videos/" + (studio != null ? studio.getSlug() : "sem-estudio") + "/" + turma.getId()
                    + "/" + data + "/" + UUID.randomUUID() + "_" + nomeOriginal;

            storageService.upload(chave, event.getMIMEType(), buffer.getInputStream(), event.getContentLength());

            VideoAula v = new VideoAula();
            v.setTurma(turma);
            v.setData(data);
            v.setChaveArmazenamento(chave);
            v.setNomeFicheiro(nomeOriginal);
            v.setTamanhoBytes(event.getContentLength());
            v.setProfessor(turma.getProfessor());
            v.setStudio(studio);
            videoAulaRepo.save(v);

            Notification.show("Vídeo enviado com sucesso!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            atualizarLista();
        } catch (Exception ex) {
            Notification.show("Erro ao enviar vídeo: " + ex.getMessage(), 6000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void atualizarLista() {
        listaVideos.removeAll();
        Turma turma = turmaCombo.getValue();
        LocalDate data = dataPicker.getValue();

        boolean pronto = turma != null && data != null;
        upload.setVisible(pronto);
        semTurmaAviso.setVisible(!pronto);
        listaVideos.setVisible(pronto);
        if (!pronto) return;

        List<VideoAula> videos = videoAulaRepo.findByTurmaAndDataOrderByDataUploadDesc(turma, data);
        if (videos.isEmpty()) {
            listaVideos.add(new Span("Ainda não há vídeos para esta aula."));
            return;
        }
        videos.forEach(v -> listaVideos.add(criarCard(v)));
    }

    private Component criarCard(VideoAula video) {
        VerticalLayout card = new VerticalLayout();
        card.setWidth("200px");
        card.setPadding(true);
        card.setSpacing(false);
        card.setAlignItems(Alignment.CENTER);
        card.getStyle().set("border", "1px solid #e2e8f0").set("border-radius", "12px")
                .set("background", "white").set("box-shadow", "0 2px 6px rgba(0,0,0,0.08)");

        VerticalLayout thumb = new VerticalLayout();
        thumb.setWidthFull();
        thumb.setHeight("110px");
        thumb.setAlignItems(Alignment.CENTER);
        thumb.setJustifyContentMode(JustifyContentMode.CENTER);
        thumb.getStyle().set("background", "linear-gradient(135deg, #1e293b 0%, #334155 100%)")
                .set("border-radius", "8px").set("cursor", "pointer");
        com.vaadin.flow.component.icon.Icon playIcon = VaadinIcon.PLAY_CIRCLE.create();
        playIcon.setSize("36px");
        playIcon.setColor("white");
        thumb.add(playIcon);
        thumb.addClickListener(e -> abrirVideo(video));

        Span nome = new Span(video.getNomeFicheiro());
        nome.getStyle().set("font-size", "12px").set("font-weight", "600").set("margin-top", "8px")
                .set("text-align", "center").set("width", "100%").set("white-space", "nowrap")
                .set("text-overflow", "ellipsis").set("overflow", "hidden");

        Button descarregar = new Button("Descarregar", VaadinIcon.DOWNLOAD_ALT.create(), e -> baixarVideo(video));
        descarregar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button apagar = new Button("Apagar", VaadinIcon.TRASH.create(), e -> confirmarApagar(video));
        apagar.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        apagar.getStyle().set("margin-top", "4px");

        card.add(thumb, nome, descarregar, apagar);
        return card;
    }

    private void abrirVideo(VideoAula video) {
        String url = storageService.gerarUrlTemporario(video.getChaveArmazenamento(), Duration.ofHours(2));
        getUI().ifPresent(ui -> ui.getPage().open(url, "_blank"));
    }

    private void baixarVideo(VideoAula video) {
        String url = storageService.gerarUrlDownload(video.getChaveArmazenamento(), video.getNomeFicheiro(),
                Duration.ofHours(2));
        getUI().ifPresent(ui -> ui.getPage().open(url, "_blank"));
    }

    private void confirmarApagar(VideoAula video) {
        ConfirmDialog cd = new ConfirmDialog();
        cd.setHeader("Apagar vídeo?");
        cd.setText("\"" + video.getNomeFicheiro() + "\" vai ser apagado do armazenamento. Esta ação não pode ser revertida.");
        cd.setCancelable(true);
        cd.setConfirmText("Apagar");
        cd.setConfirmButtonTheme("error primary");
        cd.addConfirmListener(e -> {
            try {
                storageService.apagar(video.getChaveArmazenamento());
            } catch (Exception ex) {
                Notification.show("Aviso: não foi possível apagar do armazenamento (" + ex.getMessage() + "). "
                        + "O vídeo foi removido da lista na mesma.", 6000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
            videoAulaRepo.delete(video);
            atualizarLista();
        });
        cd.open();
    }

    private void carregarTurmasPermitidas() {
        Studio studio = TenantContext.getCurrentStudio();
        List<Turma> todas = studio != null ? turmaRepo.findAllByStudio(studio) : turmaRepo.findAllComplete();
        String primeiroNome = normalizar(getFirstNameFromDatabase());
        List<Turma> permitidas = isAdmin() ? todas
                : todas.stream()
                        .filter(t -> !primeiroNome.isBlank() && t.getTodosProfessores().stream()
                                .anyMatch(p -> p.getNome() != null
                                        && normalizar(p.getNome()).contains(primeiroNome)))
                        .collect(Collectors.toList());
        turmaCombo.setItems(permitidas);
    }

    private String getFirstNameFromDatabase() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "" : userRepo.findByPrincipalName(auth.getName()).map(User::getFirstName).orElse("");
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String normalizar(String t) {
        return t == null ? ""
                : Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
