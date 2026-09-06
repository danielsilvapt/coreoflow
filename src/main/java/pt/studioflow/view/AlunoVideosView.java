package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Turma;
import pt.studioflow.model.VideoAula;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.VideoAulaRepository;
import pt.studioflow.service.AuthService;
import pt.studioflow.service.R2StorageService;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Área do aluno/encarregado: os vídeos que os professores enviaram para as
 * aulas das turmas do aluno selecionado. Lê do bucket R2
 * ({@link VideoAula} / {@link R2StorageService}) — a mesma fonte do upload em
 * {@link VideosAulaProfessorView}.
 */
@Route(value = "alunos-videos", layout = MainLayout.class)
@PageTitle("Vídeos das Aulas | CoreoFlow")
@RolesAllowed("ALUNO")
public class AlunoVideosView extends VerticalLayout {

    private static final Duration LINK_VALIDADE = Duration.ofHours(2);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final R2StorageService storageService;

    public AlunoVideosView(AuthService authService, TurmaRepository turmaRepository,
            VideoAulaRepository videoAulaRepository, R2StorageService storageService) {
        this.storageService = storageService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        add(new H2("Vídeos das Aulas"));

        Aluno aluno = authService.getAlunoSelecionado();
        if (aluno == null) {
            add(new Span("Perfil de aluno não encontrado. Contacta a secretaria."));
            return;
        }

        List<Turma> turmas = turmaRepository.findByAlunoId(aluno.getId());
        if (turmas.isEmpty()) {
            add(new Span(aluno.getNomeCompleto() + " ainda não está inscrito(a) em nenhuma turma."));
            return;
        }

        List<VideoAula> videos = videoAulaRepository.findByTurmaInOrderByDataUploadDesc(turmas);
        if (videos.isEmpty()) {
            add(new Span("Ainda não há vídeos das aulas de " + aluno.getNomeCompleto() + "."));
            return;
        }

        Map<Turma, List<VideoAula>> porTurma = videos.stream()
                .collect(Collectors.groupingBy(VideoAula::getTurma, LinkedHashMap::new, Collectors.toList()));

        porTurma.forEach((turma, lista) -> {
            Details details = new Details();
            details.setSummaryText(turma.getDescricao() + " · " + lista.size()
                    + (lista.size() == 1 ? " vídeo" : " vídeos"));
            details.setWidthFull();
            details.setOpened(porTurma.size() == 1);

            FlexLayout container = new FlexLayout();
            container.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            container.getStyle().set("gap", "16px").set("padding", "12px 0");
            lista.forEach(v -> container.add(criarCard(v)));

            details.add(container);
            add(details);
        });
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
        Icon playIcon = VaadinIcon.PLAY_CIRCLE.create();
        playIcon.setSize("36px");
        playIcon.setColor("white");
        thumb.add(playIcon);

        Anchor reproduzir = new Anchor(
                storageService.gerarUrlTemporario(video.getChaveArmazenamento(), LINK_VALIDADE), thumb);
        reproduzir.setTarget("_blank");
        reproduzir.setRouterIgnore(true);
        reproduzir.getStyle().set("width", "100%").set("text-decoration", "none");

        Span data = new Span("Aula de " + video.getData().format(DATA));
        data.getStyle().set("font-size", "12px").set("font-weight", "600").set("margin-top", "8px");

        Span nome = new Span(video.getNomeFicheiro());
        nome.getStyle().set("font-size", "11px").set("color", "#666").set("text-align", "center")
                .set("width", "100%").set("white-space", "nowrap").set("text-overflow", "ellipsis")
                .set("overflow", "hidden");

        Button descarregarBtn = new Button("Descarregar", VaadinIcon.DOWNLOAD_ALT.create());
        descarregarBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Anchor descarregar = new Anchor(
                storageService.gerarUrlDownload(video.getChaveArmazenamento(), video.getNomeFicheiro(), LINK_VALIDADE),
                descarregarBtn);
        descarregar.getElement().setAttribute("download", true);
        descarregar.getStyle().set("text-decoration", "none").set("margin-top", "4px");

        card.add(reproduzir, data, nome, descarregar);
        return card;
    }
}
