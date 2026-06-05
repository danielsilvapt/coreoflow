package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.DriveVideoDTO; // Idealmente renomear para DriveMediaDTO no futuro
import pt.studioflow.service.GoogleDriveService;
import pt.studioflow.service.TurmaService;

import java.util.List;

@Route(value = "alunos-videos", layout = MainLayout.class)
@PageTitle("Media da Turma | CoreoFlow")
@RolesAllowed("ALUNO")
public class AlunoVideosView extends VerticalLayout {

    private final GoogleDriveService driveService;
    private final TurmaService turmaService;

    public AlunoVideosView(GoogleDriveService driveService, TurmaService turmaService) {
        this.driveService = driveService;
        this.turmaService = turmaService;

        setSizeFull();
        setSpacing(true);
        setPadding(true);

        renderizarTurmas();
    }

    private void renderizarTurmas() {
        String emailLogado = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();

        List<pt.studioflow.model.Turma> turmas = turmaService.getTurmasPorAluno(emailLogado);

        if (turmas.isEmpty()) {
            add(new Span("Não foram encontradas turmas para o email: " + emailLogado));
            return;
        }

        turmas.forEach(turma -> {
            Details details = new Details();
            details.setSummaryText(turma.getDescricao());
            details.setWidthFull();

            details.addOpenedChangeListener(e -> {
                if (e.isOpened() && details.getContent().count() == 0) {
                    details.add(carregarMediaDaTurma(turma.getGoogleDriveFolderId()));
                }
            });
            add(details);
        });
    }

    private Component carregarMediaDaTurma(String folderId) {
        FlexLayout container = new FlexLayout();
        container.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        container.getStyle().set("gap", "20px");
        container.getStyle().set("padding", "var(--lumo-space-m)");

        // O serviço agora deve retornar todos os ficheiros (Vídeos e Imagens)
        List<DriveVideoDTO> arquivos = driveService.listarVideosDaPasta(folderId);

        if (arquivos.isEmpty()) {
            return new Span("Nenhum conteúdo disponível para esta turma.");
        }

        for (DriveVideoDTO arquivo : arquivos) {
            container.add(criarMediaCard(arquivo));
        }

        return container;
    }

    private Component criarMediaCard(DriveVideoDTO media) {
        VerticalLayout card = new VerticalLayout();
        card.setWidth("240px");
        card.setPadding(true);
        card.setSpacing(false);
        card.setAlignItems(Alignment.CENTER);

        // Estilo Base do Card
        card.getStyle().set("border", "1px solid #e2e8f0").set("border-radius", "12px")
                .set("background", "white").set("box-shadow", "0 4px 6px -1px rgba(0, 0, 0, 0.1)")
                .set("cursor", "pointer").set("transition", "all 0.3s ease").set("overflow", "hidden");

        // Área de Visualização (Placeholder ou Imagem)
        VerticalLayout visualArea = new VerticalLayout();
        visualArea.setWidthFull();
        visualArea.setHeight("135px");
        visualArea.setJustifyContentMode(JustifyContentMode.CENTER);
        visualArea.setAlignItems(Alignment.CENTER);
        visualArea.getStyle().set("border-radius", "8px").set("overflow", "hidden").set("padding", "0");

        // Lógica para detetar se é imagem ou vídeo
        boolean isImage = media.getNome().toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|webp)$");

        Icon actionIcon = new Icon(isImage ? VaadinIcon.PICTURE : VaadinIcon.PLAY_CIRCLE);
        actionIcon.setSize("40px");
        actionIcon.setColor("white");

        if (isImage) {
            // Se for imagem, tentamos mostrar a miniatura do Drive
            Image img = new Image(media.getThumbnailUrl() != null ? media.getThumbnailUrl() : "", "Preview");
            img.setWidthFull();
            img.setHeightFull();
            img.getStyle().set("object-fit", "cover");
            visualArea.add(img);
        } else {
            // Se for vídeo, mantemos o fundo gradiente e o ícone de Play
            visualArea.getStyle().set("background", "linear-gradient(135deg, #1e293b 0%, #334155 100%)");
            visualArea.add(actionIcon);
        }

        // Título
        Span titulo = new Span(media.getNome());
        titulo.getStyle().set("font-weight", "600").set("font-size", "14px").set("margin-top", "12px")
                .set("text-align", "center").set("width", "100%").set("white-space", "nowrap")
                .set("text-overflow", "ellipsis").set("overflow", "hidden");

        card.add(visualArea, titulo);

        // Hover Effects
        card.getElement().addEventListener("mouseenter", e -> {
            card.getStyle().set("transform", "translateY(-5px)");
            actionIcon.setColor("#3b82f6");
        });
        card.getElement().addEventListener("mouseleave", e -> {
            card.getStyle().set("transform", "translateY(0)");
            actionIcon.setColor("white");
        });

        // Clique: Abre o link do Google Drive (Modo Preview)
        card.addClickListener(e -> getUI().ifPresent(ui -> ui.getPage().open(media.getEmbedUrl(), "_blank")));

        return card;
    }
}