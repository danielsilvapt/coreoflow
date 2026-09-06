package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Presenca;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.model.User;
import pt.studioflow.repository.PresencaRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.UserRepository;
import pt.studioflow.service.R2StorageService;
import pt.studioflow.service.TurmaService;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Marcação de presenças de uma aula concreta (turma + data), com os alunos
 * apresentados em cards com foto — no espírito da galeria do menu Comunicação
 * ({@link TurmaComunicacaoView}). Complementa {@link PresencasView} (grelha
 * mensal) e grava na mesma tabela {@link Presenca}, por isso as marcas
 * aparecem nos dois ecrãs.
 */
@Route(value = "presencas-dia", layout = MainLayout.class)
@PageTitle("Presenças do Dia | CoreoFlow")
@RolesAllowed({"ADMIN", "PROF"})
public class PresencasDiaView extends VerticalLayout {

    private final TurmaRepository turmaRepository;
    private final PresencaRepository presencaRepository;
    private final TurmaService turmaService;
    private final UserRepository userRepository;
    private final R2StorageService storageService;

    private final ComboBox<Turma> turmaCombo = new ComboBox<>("Turma");
    private final DatePicker dataPicker = new DatePicker("Data");
    private final FlexLayout cardsContainer = new FlexLayout();
    private final Span aviso = new Span("Escolhe uma turma e uma data para marcar presenças.");
    private final Button btnTodosPresentes = new Button("Marcar todos presentes", VaadinIcon.CHECK.create());
    private final Button btnGuardar = new Button("Guardar", VaadinIcon.DATABASE.create());

    /** alunoId -> presente (estado editável antes de guardar). */
    private final Map<Long, Boolean> estado = new HashMap<>();
    private List<Aluno> alunosAtuais = new ArrayList<>();

    public PresencasDiaView(TurmaRepository turmaRepository, PresencaRepository presencaRepository,
            TurmaService turmaService, UserRepository userRepository, R2StorageService storageService) {
        this.turmaRepository = turmaRepository;
        this.presencaRepository = presencaRepository;
        this.turmaService = turmaService;
        this.userRepository = userRepository;
        this.storageService = storageService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Presenças do Dia");
        titulo.getStyle().set("margin-top", "0");

        turmaCombo.setItemLabelGenerator(Turma::getDescricao);
        turmaCombo.setWidth("280px");
        carregarTurmasPermitidas();

        dataPicker.setValue(LocalDate.now());
        dataPicker.setWidth("180px");

        btnTodosPresentes.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        btnGuardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);

        turmaCombo.addValueChangeListener(e -> atualizar());
        dataPicker.addValueChangeListener(e -> atualizar());
        btnTodosPresentes.addClickListener(e -> marcarTodos(true));
        btnGuardar.addClickListener(e -> guardar());

        HorizontalLayout toolbar = new HorizontalLayout(turmaCombo, dataPicker, btnTodosPresentes, btnGuardar);
        toolbar.setAlignItems(Alignment.END);
        toolbar.getStyle().set("flex-wrap", "wrap");

        aviso.getStyle().set("color", "#888");

        cardsContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        cardsContainer.getStyle().set("gap", "16px").set("margin-top", "8px");

        add(titulo, toolbar, aviso, cardsContainer);

        atualizar();
    }

    private void atualizar() {
        cardsContainer.removeAll();
        estado.clear();
        alunosAtuais = new ArrayList<>();

        Turma turma = turmaCombo.getValue();
        LocalDate data = dataPicker.getValue();
        boolean pronto = turma != null && data != null;

        btnTodosPresentes.setVisible(pronto);
        btnGuardar.setVisible(pronto);
        aviso.setVisible(!pronto);
        cardsContainer.setVisible(pronto);
        if (!pronto) return;

        alunosAtuais = turmaService.getAlunosDaTurma(turma).stream()
                .sorted(Comparator.comparing(Aluno::getNomeCompleto, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        if (alunosAtuais.isEmpty()) {
            cardsContainer.add(new Span("Esta turma não tem alunos."));
            return;
        }

        for (Aluno aluno : alunosAtuais) {
            boolean presente = presencaRepository.findByAlunoAndTurmaAndData(aluno, turma, data)
                    .map(Presenca::isPresente).orElse(false);
            estado.put(aluno.getId(), presente);
            cardsContainer.add(criarCard(aluno));
        }
    }

    private Component criarCard(Aluno aluno) {
        VerticalLayout card = new VerticalLayout();
        card.setWidth("180px");
        card.setPadding(true);
        card.setSpacing(false);
        card.setAlignItems(Alignment.CENTER);
        card.getStyle().set("border-radius", "12px").set("background", "#f9f9f9")
                .set("box-shadow", "0 2px 5px rgba(0,0,0,0.1)").set("cursor", "pointer")
                .set("transition", "border 0.15s, background 0.15s");

        Div avatar = new Div();
        avatar.getStyle().set("width", "80px").set("height", "80px").set("border-radius", "50%")
                .set("overflow", "hidden").set("display", "flex")
                .set("align-items", "center").set("justify-content", "center").set("background", "#e8e8e8");
        if (aluno.getFotoChave() != null && !aluno.getFotoChave().isBlank()) {
            Image img = new Image(storageService.gerarUrlTemporario(aluno.getFotoChave(), Duration.ofHours(2)),
                    "Foto de " + aluno.getNomeCompleto());
            img.getStyle().set("width", "80px").set("height", "80px").set("object-fit", "cover");
            avatar.add(img);
        } else {
            Icon icone = VaadinIcon.USER.create();
            icone.setSize("40px");
            icone.setColor("#666");
            avatar.add(icone);
        }

        Span nome = new Span(formatarNomeCurto(aluno.getNomeCompleto()));
        nome.getStyle().set("font-size", "0.9em").set("text-align", "center").set("font-weight", "600")
                .set("margin-top", "8px");

        Span badge = new Span();
        badge.getStyle().set("font-size", "0.8em").set("font-weight", "700").set("margin-top", "4px");

        card.add(avatar, nome, badge);
        aplicarEstadoVisual(card, badge, estado.getOrDefault(aluno.getId(), false));

        card.addClickListener(e -> {
            boolean novo = !estado.getOrDefault(aluno.getId(), false);
            estado.put(aluno.getId(), novo);
            aplicarEstadoVisual(card, badge, novo);
        });
        return card;
    }

    private void aplicarEstadoVisual(VerticalLayout card, Span badge, boolean presente) {
        if (presente) {
            card.getStyle().set("border", "2px solid #2ecc71").set("background", "#ebfaf0");
            badge.setText("✓ Presente");
            badge.getStyle().set("color", "#27AE60");
        } else {
            card.getStyle().set("border", "1px solid #e0e0e0").set("background", "#f9f9f9");
            badge.setText("✗ Falta");
            badge.getStyle().set("color", "#E74C3C");
        }
    }

    private void marcarTodos(boolean presente) {
        alunosAtuais.forEach(a -> estado.put(a.getId(), presente));
        cardsContainer.removeAll();
        alunosAtuais.forEach(a -> cardsContainer.add(criarCard(a)));
    }

    private void guardar() {
        Turma turma = turmaCombo.getValue();
        LocalDate data = dataPicker.getValue();
        if (turma == null || data == null) return;

        for (Aluno aluno : alunosAtuais) {
            boolean presente = estado.getOrDefault(aluno.getId(), false);
            Presenca p = presencaRepository.findByAlunoAndTurmaAndData(aluno, turma, data)
                    .orElseGet(Presenca::new);
            p.setAluno(aluno);
            p.setTurma(turma);
            p.setData(data);
            p.setPresente(presente);
            presencaRepository.save(p);
        }
        long total = estado.values().stream().filter(Boolean::booleanValue).count();
        Notification.show("Presenças guardadas — " + total + " de " + alunosAtuais.size()
                + " presentes em " + data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    // ---- Turmas visíveis ao utilizador (mesmo critério de PresencasView / VideosAulaProfessorView) ----

    private void carregarTurmasPermitidas() {
        Studio studio = TenantContext.getCurrentStudio();
        List<Turma> todas = studio != null ? turmaRepository.findAllByStudio(studio) : turmaRepository.findAllComplete();
        String primeiroNome = normalizar(getFirstNameFromDatabase());
        List<Turma> permitidas = isAdmin() ? todas
                : todas.stream()
                        .filter(t -> !primeiroNome.isBlank() && t.getTodosProfessores().stream()
                                .anyMatch(p -> p.getNome() != null
                                        && normalizar(p.getNome()).contains(primeiroNome)))
                        .collect(Collectors.toList());
        turmaCombo.setItems(permitidas.stream()
                .sorted(Comparator.comparing(Turma::getDescricao, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList()));
    }

    private String getFirstNameFromDatabase() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "" : userRepository.findByPrincipalName(auth.getName()).map(User::getFirstName).orElse("");
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String normalizar(String t) {
        return t == null ? ""
                : Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private String formatarNomeCurto(String n) {
        if (n == null || !n.contains(" ")) return n;
        String[] p = n.trim().split("\\s+");
        return p[0] + " " + p[p.length - 1];
    }
}
