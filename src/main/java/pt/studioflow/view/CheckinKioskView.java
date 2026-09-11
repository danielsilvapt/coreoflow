package pt.studioflow.view;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.MetodoRegistoPresenca;
import pt.studioflow.model.Presenca;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.PresencaRepository;
import pt.studioflow.service.AuthService;
import pt.studioflow.service.CheckinService;
import pt.studioflow.service.R2StorageService;
import pt.studioflow.service.TurmaService;

/**
 * Kiosk de checkin — página standalone (sem MainLayout/drawer) pensada para o
 * perfil PRESENÇA: uma pessoa ou um iPad fixo na sala que só sabe marcar
 * presenças. Lista as turmas "a acontecer agora" (dentro da janela de checkin
 * do estúdio) e, ao escolher uma, mostra o roster (matriculados + créditos de
 * aula avulsa válidos) para tocar e marcar presente.
 */
@Route("checkin")
@PageTitle("Checkin")
@RolesAllowed({ "PRESENCA", "ADMIN", "PROF" })
public class CheckinKioskView extends VerticalLayout {

    private final AuthService authService;
    private final CheckinService checkinService;
    private final TurmaService turmaService;
    private final PresencaRepository presencaRepository;
    private final R2StorageService storageService;

    private final VerticalLayout conteudo = new VerticalLayout();

    public CheckinKioskView(AuthService authService, CheckinService checkinService, TurmaService turmaService,
            PresencaRepository presencaRepository, R2StorageService storageService) {
        this.authService = authService;
        this.checkinService = checkinService;
        this.turmaService = turmaService;
        this.presencaRepository = presencaRepository;
        this.storageService = storageService;

        authService.initTenantContext();

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setPadding(true);
        getStyle().set("background-color", "#f8f9fa");

        conteudo.setWidth("min(520px, 96vw)");
        conteudo.setPadding(false);
        add(conteudo);

        mostrarListaTurmas();
    }

    private Studio studioAtual() {
        return authService.getCurrentStudio();
    }

    private void mostrarListaTurmas() {
        conteudo.removeAll();

        Studio studio = studioAtual();
        if (studio == null) {
            conteudo.add(new H2("Sem estúdio associado a esta conta."));
            return;
        }

        H2 titulo = new H2("Checkin");
        titulo.getStyle().set("margin-bottom", "0");
        Span subtitulo = new Span("Toca na turma a acontecer agora");
        subtitulo.getStyle().set("color", "#7f8c8d");
        Button atualizar = new Button("Atualizar", VaadinIcon.REFRESH.create(), e -> mostrarListaTurmas());
        atualizar.getStyle().set("align-self", "flex-end");

        conteudo.add(titulo, subtitulo, atualizar);

        List<Turma> turmas = checkinService.listarTurmasNaJanelaAgora(studio, LocalDateTime.now()).stream()
                .sorted(Comparator.comparing(Turma::getDescricao, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        if (turmas.isEmpty()) {
            Span vazio = new Span("Nenhuma turma dentro da janela de checkin neste momento.");
            vazio.getStyle().set("color", "#888").set("margin-top", "16px");
            conteudo.add(vazio);
            return;
        }

        for (Turma turma : turmas) {
            Button botao = new Button(turma.getDescricao() + (turma.getModalidade() != null
                    ? " — " + turma.getModalidade().getDescricao() : ""));
            botao.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_CONTRAST);
            botao.setWidthFull();
            botao.getStyle().set("height", "56px").set("margin-top", "10px").set("font-weight", "700");
            botao.addClickListener(e -> mostrarRoster(turma));
            conteudo.add(botao);
        }
    }

    private void mostrarRoster(Turma turma) {
        conteudo.removeAll();

        Button voltar = new Button("← Turmas", e -> mostrarListaTurmas());
        H3 titulo = new H3(turma.getDescricao());
        titulo.getStyle().set("margin", "8px 0 0 0");
        conteudo.add(voltar, titulo);

        LocalDate hoje = LocalDate.now();
        Set<Aluno> roster = new LinkedHashSet<>();
        roster.addAll(turmaService.getAlunosDaTurma(turma));
        roster.addAll(checkinService.listarAvulsosElegiveisParaTurma(turma));

        List<Aluno> ordenado = roster.stream()
                .sorted(Comparator.comparing(Aluno::getNomeCompleto, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        if (ordenado.isEmpty()) {
            Span vazio = new Span("Sem alunos matriculados ou com crédito válido para esta turma.");
            vazio.getStyle().set("color", "#888").set("margin-top", "16px");
            conteudo.add(vazio);
            return;
        }

        FlexLayout grid = new FlexLayout();
        grid.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        grid.getStyle().set("gap", "12px").set("justify-content", "center").set("margin-top", "12px");
        conteudo.add(grid);

        for (Aluno aluno : ordenado) {
            grid.add(criarCardAluno(aluno, turma, hoje));
        }
    }

    private Div criarCardAluno(Aluno aluno, Turma turma, LocalDate hoje) {
        Div card = new Div();
        card.getStyle()
                .set("display", "flex").set("flex-direction", "column").set("align-items", "center")
                .set("gap", "6px").set("padding", "12px").set("border-radius", "12px")
                .set("box-shadow", "0 2px 5px rgba(0,0,0,0.1)").set("cursor", "pointer")
                .set("user-select", "none").set("width", "110px");

        Div avatar = new Div();
        avatar.getStyle()
                .set("width", "64px").set("height", "64px").set("border-radius", "50%").set("overflow", "hidden")
                .set("display", "flex").set("align-items", "center").set("justify-content", "center")
                .set("background", "#eee");
        if (aluno.getFotoChave() != null && !aluno.getFotoChave().isBlank()) {
            Image image = new Image(storageService.gerarUrlTemporario(aluno.getFotoChave(), Duration.ofHours(2)),
                    "Foto de " + aluno.getNomeCompleto());
            image.getStyle().set("width", "64px").set("height", "64px").set("object-fit", "cover")
                    .set("border-radius", "50%");
            avatar.add(image);
        } else {
            Icon icon = new Icon(VaadinIcon.USER);
            icon.getStyle().set("font-size", "32px").set("color", "#666");
            avatar.add(icon);
        }

        Span nome = new Span(aluno.getNomeCompleto());
        nome.getStyle().set("font-size", "0.82em").set("text-align", "center");

        Span estado = new Span();
        estado.getStyle().set("font-size", "0.75em").set("font-weight", "700");

        Presenca existente = presencaRepository.findByAlunoAndTurmaAndData(aluno, turma, hoje).orElse(null);
        boolean presente = existente != null && existente.isPresente();
        aplicarEstadoCard(card, estado, presente);

        card.add(avatar, nome, estado);
        card.addClickListener(e -> {
            try {
                if (presente && existente != null) {
                    checkinService.cancelarCheckin(existente);
                } else {
                    checkinService.registarCheckin(aluno, turma, MetodoRegistoPresenca.KIOSK_PRESENCA);
                }
                mostrarRoster(turma);
            } catch (IllegalStateException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        return card;
    }

    private void aplicarEstadoCard(Div card, Span estado, boolean presente) {
        if (presente) {
            card.getStyle().set("border", "2px solid #2ecc71").set("background", "#ebfaf0");
            estado.setText("✓ Presente");
            estado.getStyle().set("color", "#27ae60");
        } else {
            card.getStyle().set("border", "1px solid #e0e0e0").set("background", "#ffffff");
            estado.setText("Marcar");
            estado.getStyle().set("color", "#c0392b");
        }
    }
}
