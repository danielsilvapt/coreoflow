package pt.studioflow.view;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.timepicker.TimePicker;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import java.time.format.DateTimeFormatter;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aula;
import pt.studioflow.model.MarcacaoSala;
import pt.studioflow.model.OcorrenciaAula;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Sala;
import pt.studioflow.model.Studio;
import pt.studioflow.model.SumarioAula;
import pt.studioflow.model.Turma;
import pt.studioflow.model.User;
import pt.studioflow.model.VideoAula;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AulaRepository;
import pt.studioflow.repository.InterrupcaoLetivaRepository;
import pt.studioflow.repository.MarcacaoSalaRepository;
import pt.studioflow.repository.OcorrenciaAulaRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.SalaRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.SumarioAulaRepository;
import pt.studioflow.repository.UserRepository;
import pt.studioflow.repository.VideoAulaRepository;
import pt.studioflow.service.EmailService;
import pt.studioflow.service.R2StorageService;
import pt.studioflow.service.TurmaService;
import pt.studioflow.util.DataUtil;

import java.time.LocalDateTime;
import java.util.UUID;

@PageTitle("Mapa de Salas | CoreoFlow")
@Route(value = "horario-salas", layout = MainLayout.class)
@RolesAllowed({ "ADMIN", "PROF" })
public class SalaScheduleView extends VerticalLayout {

    private final SalaRepository salaRepository;
    private final TurmaRepository turmaRepository;
    private final MarcacaoSalaRepository marcacaoRepository;
    private final AulaRepository aulaRepository;
    private final ProfessorRepository professorRepository;
    private final AlunoRepository alunoRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final OcorrenciaAulaRepository ocorrenciaAulaRepository;
    private final VideoAulaRepository videoAulaRepository;
    private final R2StorageService storageService;
    private final SumarioAulaRepository sumarioAulaRepository;
    private final TurmaService turmaService;
    private final InterrupcaoLetivaRepository interrupcaoRepository;

    /** Professor associado ao utilizador (null se admin ou não resolvido). */
    private Professor professorLogado;

    private final int HORA_INICIO = 9;
    private final int HORA_FIM = 23;
    private final int PIXELS_POR_HORA = 65;

    private final List<DayOfWeek> diasSemana = Arrays.asList(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private Div grelhaContainer;
    private VerticalLayout notificationsSection;
    private LocalDate semanaAtual;
    private Span labelSemana;
    private boolean isAdmin;

    public SalaScheduleView(SalaRepository salaRepository, TurmaRepository turmaRepository,
            MarcacaoSalaRepository marcacaoRepository, AulaRepository aulaRepository,
            ProfessorRepository professorRepository, TurmaService turmaService, AlunoRepository alunoRepository,
            EmailService emailService, UserRepository userRepository, OcorrenciaAulaRepository ocorrenciaAulaRepository,
            VideoAulaRepository videoAulaRepository, R2StorageService storageService,
            SumarioAulaRepository sumarioAulaRepository, InterrupcaoLetivaRepository interrupcaoRepository) {

        this.salaRepository = salaRepository;
        this.turmaRepository = turmaRepository;
        this.marcacaoRepository = marcacaoRepository;
        this.aulaRepository = aulaRepository;
        this.professorRepository = professorRepository;
        this.alunoRepository = alunoRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.ocorrenciaAulaRepository = ocorrenciaAulaRepository;
        this.videoAulaRepository = videoAulaRepository;
        this.storageService = storageService;
        this.sumarioAulaRepository = sumarioAulaRepository;
        this.turmaService = turmaService;
        this.interrupcaoRepository = interrupcaoRepository;

        this.isAdmin = VaadinServletRequest.getCurrent().getHttpServletRequest().isUserInRole("ADMIN");
        this.professorLogado = isAdmin ? null : resolverProfessorLogado();

        setPadding(true);
        setSpacing(true);
        setSizeFull();
        getStyle().set("background-color", "#f8f9fa");

        // Injeção de Estilos CSS Avançados para Efeitos de Grelha e Transições
        // Dinâmicas
        Div estiloInjetado = new Div();
        estiloInjetado.getElement().setProperty("innerHTML", "<style>"
                + ".grid-slot { transition: background-color 0.15s ease; }"
                + ".grid-slot:hover { background-color: #f1f5f9 !important; z-index: 1; }"
                + ".schedule-card { transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.2s ease; }"
                + ".schedule-card:hover { transform: translateY(-2px); box-shadow: 0 6px 12px rgba(0,0,0,0.15) !important; z-index: 50 !important; }"
                + "</style>");
        add(estiloInjetado);

        semanaAtual = LocalDate.now().with(DayOfWeek.MONDAY);

        H2 titulo = new H2("Mapa Geral de Salas");
        titulo.getStyle()
                .set("margin", "0")
                .set("font-size", "1.4rem")
                .set("font-weight", "700")
                .set("color", "#2b2d42");

        if (isAdmin) {
            notificationsSection = new VerticalLayout();
            notificationsSection.getStyle()
                    .set("background-color", "#fff5f5")
                    .set("border", "1px solid #feb2b2")
                    .set("border-radius", "12px")
                    .set("padding", "16px")
                    .set("margin-bottom", "10px");
            notificationsSection.setVisible(false);
            add(notificationsSection);
        }

        Button btnAnterior = new Button("◀");
        btnAnterior.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button btnSeguinte = new Button("▶");
        btnSeguinte.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button btnHoje = new Button("Hoje");
        btnHoje.addThemeVariants(ButtonVariant.LUMO_SMALL);

        labelSemana = new Span();
        labelSemana.getStyle()
                .set("font-weight", "600")
                .set("font-size", "1rem")
                .set("margin", "0 10px")
                .set("color", "#4a5568")
                .set("white-space", "nowrap");

        btnAnterior.addClickListener(e -> {
            semanaAtual = semanaAtual.minusWeeks(1);
            atualizarTudo();
        });
        btnSeguinte.addClickListener(e -> {
            semanaAtual = semanaAtual.plusWeeks(1);
            atualizarTudo();
        });
        btnHoje.addClickListener(e -> {
            semanaAtual = LocalDate.now().with(DayOfWeek.MONDAY);
            atualizarTudo();
        });

        HorizontalLayout navLayout = new HorizontalLayout(btnHoje, btnAnterior, labelSemana, btnSeguinte);
        navLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        navLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        navLayout.getStyle()
                .set("background-color", "#ffffff")
                .set("padding", "6px 12px")
                .set("border-radius", "30px")
                .set("box-shadow", "0 2px 4px rgba(0,0,0,0.04)");

        Button btnAdicionarRegular = new Button("➕ Regular");
        btnAdicionarRegular.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnAdicionarRegular.addClickListener(e -> abrirDialogAdicionarAulaRegular());
        btnAdicionarRegular.setVisible(isAdmin);

        Button btnAdicionarPontual = new Button(isAdmin ? "📅 Pontual" : "📅 Pedir Sala");
        btnAdicionarPontual.addThemeVariants(isAdmin ? ButtonVariant.LUMO_SUCCESS : ButtonVariant.LUMO_PRIMARY);
        btnAdicionarPontual.addClickListener(e -> abrirDialogAdicionarAulaPontual());

        Button btnInterrupcoes = new Button("🚫 Interrupções", e -> abrirDialogInterrupcoes());
        btnInterrupcoes.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        btnInterrupcoes.getElement().setAttribute("title", "Períodos sem aulas (Natal, Páscoa, feriados)");
        btnInterrupcoes.setVisible(isAdmin);

        HorizontalLayout actions = new HorizontalLayout(btnAdicionarRegular, btnAdicionarPontual, btnInterrupcoes);
        actions.setSpacing(true);

        HorizontalLayout topBar = new HorizontalLayout(titulo, navLayout, actions);
        topBar.setWidthFull();
        topBar.setAlignItems(FlexComponent.Alignment.CENTER);
        topBar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        topBar.getStyle().set("flex-wrap", "wrap").set("gap", "12px");

        // Legenda de cores
        HorizontalLayout legenda = new HorizontalLayout();
        legenda.setAlignItems(FlexComponent.Alignment.CENTER);
        legenda.getStyle().set("gap", "16px").set("flex-wrap", "wrap");
        legenda.add(
            criarChipLegenda("Aula Regular", "#3b82f6"),
            criarChipLegenda("Aula Pontual / Privada", "#f97316"),
            criarChipLegenda("Pendente aprovação", "#94a3b8"),
            criarChipLegenda("Hoje", "#f0fdf4", "#16a34a")
        );

        add(topBar, legenda);

        grelhaContainer = new Div();
        grelhaContainer.setSizeFull();
        grelhaContainer.getStyle()
                .set("overflow", "auto")
                .set("-webkit-overflow-scrolling", "touch")
                .set("border-radius", "12px")
                .set("background-color", "#ffffff")
                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.05)");
        add(grelhaContainer);

        atualizarTudo();
    }

    private void atualizarTudo() {
        atualizarLabelSemana(labelSemana);
        atualizarMapa();
        if (isAdmin) {
            atualizarNotificacoes();
        }
    }

    private void atualizarNotificacoes() {
        notificationsSection.removeAll();
        Studio studio = TenantContext.getCurrentStudio();
        List<MarcacaoSala> pendentes = (studio != null
                ? marcacaoRepository.findByStudioAndStatus(studio, "PENDENTE")
                : marcacaoRepository.findAll().stream().filter(m -> "PENDENTE".equalsIgnoreCase(m.getStatus())).collect(Collectors.toList()))
                .stream()
                .sorted((a, b) -> a.getData() != null && b.getData() != null
                        ? a.getData().compareTo(b.getData()) : 0)
                .collect(Collectors.toList());

        if (pendentes.isEmpty()) {
            notificationsSection.setVisible(false);
            return;
        }

        notificationsSection.setVisible(true);
        notificationsSection.getStyle()
                .set("background-color", "#fff7ed")
                .set("border", "1px solid #fed7aa")
                .set("border-radius", "12px")
                .set("padding", "14px 16px")
                .set("margin-bottom", "10px");

        // Cabeçalho com badge
        HorizontalLayout cabecalho = new HorizontalLayout();
        cabecalho.setAlignItems(FlexComponent.Alignment.CENTER);
        cabecalho.setWidthFull();
        cabecalho.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        HorizontalLayout left = new HorizontalLayout();
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        Icon bellIcon = VaadinIcon.BELL.create();
        bellIcon.setColor("#ea580c");
        bellIcon.setSize("18px");
        H3 titulo = new H3("Pedidos de Sala Pendentes");
        titulo.getStyle().set("margin", "0 0 0 8px").set("font-size", "0.95rem").set("color", "#c2410c");
        Span badge = new Span(String.valueOf(pendentes.size()));
        badge.getStyle()
                .set("background", "#ea580c").set("color", "white")
                .set("border-radius", "12px").set("padding", "2px 8px")
                .set("font-size", "0.75rem").set("font-weight", "700").set("margin-left", "8px");
        left.add(bellIcon, titulo, badge);

        Span hint = new Span("Clique em Aprovar ou Recusar para cada pedido");
        hint.getStyle().set("font-size", "0.78rem").set("color", "#9a3412").set("opacity", "0.8");
        cabecalho.add(left, hint);
        notificationsSection.add(cabecalho);

        DateTimeFormatter fmtData = DateTimeFormatter.ofPattern("EEEE, d MMM", new Locale("pt", "PT"));

        for (MarcacaoSala p : pendentes) {
            HorizontalLayout item = new HorizontalLayout();
            item.setWidthFull();
            item.setAlignItems(FlexComponent.Alignment.CENTER);
            item.getStyle()
                    .set("background", "white")
                    .set("border-radius", "8px")
                    .set("padding", "10px 14px")
                    .set("margin-top", "8px")
                    .set("box-shadow", "0 1px 3px rgba(0,0,0,0.06)")
                    .set("border-left", "4px solid #ea580c")
                    .set("flex-wrap", "wrap")
                    .set("gap", "8px");

            // Info principal
            VerticalLayout info = new VerticalLayout();
            info.setPadding(false);
            info.setSpacing(false);
            info.getStyle().set("flex", "1").set("min-width", "200px");

            String dataStr = p.getData() != null ? p.getData().format(fmtData) : "—";
            Span linha1 = new Span("👤 " + p.getProfessor() + "   📅 " + dataStr
                    + "   🕐 " + p.getHoraInicio() + " – " + p.getHoraFim());
            linha1.getStyle().set("font-weight", "600").set("font-size", "0.88rem").set("color", "#1c1917");

            String sala = p.getSala() != null ? p.getSala().getNome() : "—";
            String obs = (p.getObservacoes() != null && !p.getObservacoes().isBlank())
                    ? " · " + p.getObservacoes() : "";
            Span linha2 = new Span("🏠 " + sala + "   📋 " + p.getTipo() + obs);
            linha2.getStyle().set("font-size", "0.8rem").set("color", "#78716c");

            info.add(linha1, linha2);

            // Botões
            Button btnAprovar = new Button("✅ Aprovar", e -> {
                p.setStatus("APROVADO");
                marcacaoRepository.save(p);
                getProfessoresDoStudio().stream()
                        .filter(prof -> prof.getNome().equals(p.getProfessor()))
                        .findFirst()
                        .ifPresent(prof -> emailService.enviarEmailAprovacaoSala(
                                TenantContext.getCurrentStudio(),
                                prof.getEmail(), prof.getNome(), p.getSala().getNome(),
                                pt.studioflow.util.DataUtil.formatar(p.getData()), p.getHoraInicio().toString(), p.getHoraFim().toString()));
                atualizarTudo();
                Notification n = Notification.show("Pedido aprovado — email enviado ao professor!", 3000,
                        Notification.Position.BOTTOM_CENTER);
                n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });
            btnAprovar.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);

            Button btnRecusar = new Button("❌ Recusar", e -> {
                marcacaoRepository.delete(p);
                getProfessoresDoStudio().stream()
                        .filter(prof -> prof.getNome().equals(p.getProfessor()))
                        .findFirst()
                        .ifPresent(prof -> emailService.enviarEmailRecusaSala(
                                TenantContext.getCurrentStudio(),
                                prof.getEmail(), prof.getNome(), p.getSala().getNome(),
                                pt.studioflow.util.DataUtil.formatar(p.getData()), p.getHoraInicio().toString(), p.getHoraFim().toString()));
                atualizarTudo();
                Notification n = Notification.show("Pedido recusado — professor notificado.", 3000,
                        Notification.Position.BOTTOM_CENTER);
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            });
            btnRecusar.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR,
                    ButtonVariant.LUMO_TERTIARY);

            item.add(info, btnAprovar, btnRecusar);
            notificationsSection.add(item);
        }
    }

    private List<Turma> getTurmasDoStudio() {
        Studio s = TenantContext.getCurrentStudio();
        return s != null ? turmaRepository.findAllByStudio(s) : turmaRepository.findAll();
    }

    private List<Professor> getProfessoresDoStudio() {
        Studio s = TenantContext.getCurrentStudio();
        return s != null ? professorRepository.findAllByStudio(s) : professorRepository.findAll();
    }

    private Professor resolverProfessorLogado() {
        try {
            String userLogado = VaadinServletRequest.getCurrent().getHttpServletRequest()
                    .getUserPrincipal().getName();
            String primeiro = resolverPrimeiroNomeUserLogado(userLogado);
            return getProfessoresDoStudio().stream()
                    .filter(p -> p.getNome() != null && !p.getNome().isBlank())
                    .filter(p -> normalizarTxt(p.getNome()).contains(normalizarTxt(primeiro)))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isMinhaTurma(Turma t) {
        return !isAdmin && professorLogado != null && professorLogado.getId() != null
                && t != null && t.getProfessor() != null
                && professorLogado.getId().equals(t.getProfessor().getId());
    }

    private static String normalizarTxt(String t) {
        return t == null ? ""
                : java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    /**
     * Resolve o primeiro nome do utilizador logado a partir do campo firstName na BD
     * (mesma lógica usada no resto da app), em vez de assumir que o username/principal
     * (que pode vir como "slug:username") corresponde ao primeiro nome.
     */
    private String resolverPrimeiroNomeUserLogado(String userLogado) {
        String firstName = userRepository.findByPrincipalName(userLogado).map(User::getFirstName).orElse(null);
        if (firstName != null && !firstName.isBlank()) {
            return firstName.trim().split("\\s+")[0].toLowerCase();
        }
        String semSlug = userLogado.contains(":") ? userLogado.split(":", 2)[1] : userLogado;
        return semSlug.split("[\\.\\s_]")[0].toLowerCase();
    }

    private List<Aluno> getAlunosDoStudio() {
        Studio s = TenantContext.getCurrentStudio();
        return s != null ? alunoRepository.findAllByStudio(s) : alunoRepository.findAllByOrderByNomeCompletoAsc();
    }

    /** Rótulo legível para o seletor de alunos: nome completo (nunca o email/hash). */
    private String rotuloAluno(Aluno a) {
        if (a == null) return "";
        if (a.getNomeCompleto() != null && !a.getNomeCompleto().isBlank()) return a.getNomeCompleto().trim();
        if (a.getEmail() != null && !a.getEmail().isBlank()) return a.getEmail();
        return "Aluno #" + a.getId();
    }

    private List<Sala> getSalasDoStudio() {
        Studio s = TenantContext.getCurrentStudio();
        return s != null ? salaRepository.findAllByStudio(s) : salaRepository.findAll();
    }

    private String traduzirDia(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, new Locale("pt", "PT"));
    }

    private Span criarChipLegenda(String label, String cor) {
        return criarChipLegenda(label, cor, "white");
    }

    private Span criarChipLegenda(String label, String bg, String textColor) {
        Span chip = new Span(label);
        chip.getStyle()
                .set("background", bg)
                .set("color", textColor)
                .set("border-radius", "6px")
                .set("padding", "3px 10px")
                .set("font-size", "0.75rem")
                .set("font-weight", "600")
                .set("border", "1px solid rgba(0,0,0,0.08)");
        return chip;
    }

    private void atualizarLabelSemana(Span label) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM", new Locale("pt", "PT"));
        DateTimeFormatter fmtAno = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("pt", "PT"));
        label.setText(semanaAtual.format(fmt) + " – " + semanaAtual.plusDays(6).format(fmtAno));
    }

    private void atualizarMapa() {
        grelhaContainer.removeAll();
        Studio studio = TenantContext.getCurrentStudio();
        List<Sala> salas = studio != null ? salaRepository.findAllByStudio(studio) : salaRepository.findAll();
        if (salas.isEmpty()) {
            Span emptySpan = new Span("Nenhuma sala configurada no sistema.");
            emptySpan.getStyle().set("padding", "20px").set("color", "#718096");
            grelhaContainer.add(emptySpan);
            return;
        }
        List<Aula> todasAulas = studio != null
                ? aulaRepository.findByTurmaStudio(studio)
                : aulaRepository.findAll();
        List<MarcacaoSala> todasMarcacoes = studio != null
                ? marcacaoRepository.findByStudioAndDataBetween(studio, semanaAtual, semanaAtual.plusDays(6))
                : marcacaoRepository.findByDataBetween(semanaAtual, semanaAtual.plusDays(6));
        List<OcorrenciaAula> ocorrenciasDaSemana = studio != null
                ? ocorrenciaAulaRepository.findByStudioAndDataBetweenOrderByDataAsc(studio, semanaAtual,
                        semanaAtual.plusDays(6))
                : ocorrenciaAulaRepository.findAll().stream()
                        .filter(o -> !o.getData().isBefore(semanaAtual) && !o.getData().isAfter(semanaAtual.plusDays(6)))
                        .collect(Collectors.toList());

        List<SumarioAula> sumariosSemana = studio != null
                ? sumarioAulaRepository.findByStudioAndDataBetween(studio, semanaAtual, semanaAtual.plusDays(6))
                : sumarioAulaRepository.findByDataBetween(semanaAtual, semanaAtual.plusDays(6));

        grelhaContainer.add(criarGrelhaGlobal(salas, todasAulas, todasMarcacoes, ocorrenciasDaSemana, sumariosSemana));
    }

    private Div criarGrelhaGlobal(List<Sala> salas, List<Aula> aulas, List<MarcacaoSala> marcacoes,
            List<OcorrenciaAula> ocorrencias, List<SumarioAula> sumarios) {
        Div container = new Div();
        container.getStyle().set("display", "inline-block").set("min-width", "100%").set("position", "relative");

        int numSalas = salas.size();
        String gridTemplate = "60px repeat(" + (7 * numSalas) + ", minmax(130px, 1fr))";

        Div headerDias = new Div();
        headerDias.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", gridTemplate)
                .set("position", "sticky")
                .set("top", "0")
                .set("z-index", "100")
                .set("background-color", "#1e293b")
                .set("color", "#ffffff")
                .set("text-align", "center");
        headerDias.add(new Div());

        for (DayOfWeek dia : diasSemana) {
            LocalDate dataDiaHeader = semanaAtual.with(dia);
            boolean isHojeHeader = dataDiaHeader.equals(LocalDate.now());
            DateTimeFormatter fmtDia = DateTimeFormatter.ofPattern("d MMM", new Locale("pt", "PT"));

            Span txtDia = new Span(traduzirDia(dia).toUpperCase() + (isHojeHeader ? " · HOJE" : ""));
            txtDia.getStyle()
                    .set("font-size", "0.7rem")
                    .set("font-weight", "700")
                    .set("letter-spacing", "0.05em");

            Span dataDiaSpan = new Span(dataDiaHeader.format(fmtDia));
            dataDiaSpan.getStyle().set("font-size", "0.65rem").set("opacity", "0.75").set("display", "block");

            Div diaDiv = new Div(txtDia, dataDiaSpan);
            diaDiv.getStyle()
                    .set("grid-column", "span " + numSalas)
                    .set("border-left", "1px solid #334155")
                    .set("padding", "8px 0")
                    .set("text-align", "center");
            if (isHojeHeader) {
                diaDiv.getStyle()
                        .set("background", "#16a34a")
                        .set("color", "white");
                txtDia.getStyle().set("color", "white");
                dataDiaSpan.getStyle().set("color", "rgba(255,255,255,0.85)");
            }
            headerDias.add(diaDiv);
        }
        container.add(headerDias);

        Div headerSalas = new Div();
        headerSalas.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", gridTemplate)
                .set("position", "sticky")
                .set("top", "37px")
                .set("z-index", "90")
                .set("background-color", "#f1f5f9")
                .set("border-bottom", "1px solid #e2e8f0")
                .set("text-align", "center");

        Span txtHora = new Span("Hora");
        txtHora.getStyle().set("font-size", "0.65rem").set("font-weight", "600").set("color", "#64748b");
        headerSalas.add(new Div(txtHora));

        for (DayOfWeek dia : diasSemana) {
            for (Sala sala : salas) {
                Span sTxt = new Span(sala.getNome());
                sTxt.getStyle().set("font-size", "0.7rem").set("font-weight", "600").set("color", "#334155");
                Div sDiv = new Div(sTxt);
                sDiv.getStyle()
                        .set("border-left", "1px solid #e2e8f0")
                        .set("padding", "6px 0");
                headerSalas.add(sDiv);
            }
        }
        container.add(headerSalas);

        Div corpoGrelha = new Div();
        corpoGrelha.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", gridTemplate)
                .set("height", ((HORA_FIM - HORA_INICIO) * PIXELS_POR_HORA) + "px")
                .set("position", "relative");

        Div colHoras = new Div();
        colHoras.getStyle()
                .set("position", "relative")
                .set("border-right", "1px solid #cbd5e1")
                .set("background-color", "#f8fafc");
        for (int h = HORA_INICIO; h < HORA_FIM; h++) {
            Span horaLabel = new Span(String.format("%02d:00", h));
            horaLabel.getStyle()
                    .set("position", "absolute")
                    .set("top", ((h - HORA_INICIO) * PIXELS_POR_HORA) + "px")
                    .set("left", "8px")
                    .set("font-size", "0.7rem")
                    .set("font-weight", "500")
                    .set("color", "#64748b");
            colHoras.add(horaLabel);
        }
        corpoGrelha.add(colHoras);

        for (DayOfWeek dia : diasSemana) {
            LocalDate dataDia = semanaAtual.with(dia);
            boolean isHoje = dataDia.equals(LocalDate.now());

            for (Sala sala : salas) {
                Div coluna = new Div();
                coluna.getStyle()
                        .set("position", "relative")
                        .set("border-left", "1px solid #f1f5f9")
                        .set("height", "100%");

                if (isHoje) {
                    coluna.getStyle().set("background-color", "#f0fdf4");
                }

                for (int h = HORA_INICIO; h < HORA_FIM; h++) {
                    final int horaFixa = h;
                    Div slotClique = new Div();
                    slotClique.addClassName("grid-slot"); // Vinculado ao CSS injetado para hover sofisticado
                    slotClique.getStyle()
                            .set("position", "absolute")
                            .set("top", ((h - HORA_INICIO) * PIXELS_POR_HORA) + "px")
                            .set("width", "100%")
                            .set("height", PIXELS_POR_HORA + "px")
                            .set("border-top", "1px solid #f1f5f9")
                            .set("cursor", "pointer")
                            .set("z-index", "0");

                    slotClique.addClickListener(e -> {
                        if (isAdmin) {
                            abrirDialogEscolherTipoAula(dataDia, LocalTime.of(horaFixa, 0), sala);
                        } else {
                            abrirDialogAdicionarAulaPontualPrePreenchido(dataDia, LocalTime.of(horaFixa, 0), sala);
                        }
                    });
                    coluna.add(slotClique);
                }

                aulas.stream()
                        .filter(a -> a.getDia().equals(dia) && a.getSala().getId().equals(sala.getId()))
                        .forEach(a -> {
                            OcorrenciaAula oc = ocorrencias.stream()
                                    .filter(o -> o.getTipo() != OcorrenciaAula.Tipo.REPOSICAO)
                                    .filter(o -> o.getTurma() != null && a.getTurma() != null
                                            && o.getTurma().getId().equals(a.getTurma().getId())
                                            && o.getData().equals(dataDia))
                                    .findFirst().orElse(null);
                            coluna.add(criarElementoAula(a, oc, dataDia, sumarios));
                        });

                marcacoes.stream()
                        .filter(m -> m.getSala().getId().equals(sala.getId()) && m.getData().equals(dataDia))
                        .forEach(m -> coluna.add(criarElementoMarcacao(m)));

                corpoGrelha.add(coluna);
            }
        }
        container.add(corpoGrelha);
        return container;
    }

    private Div criarElementoAula(Aula a, OcorrenciaAula ocorrencia, LocalDate dataDia, List<SumarioAula> sumarios) {
        double top = calcularTop(a.getHoraInicio());
        double height = calcularAltura(a.getHoraInicio(), a.getHoraFim());
        String corBase = (a.getTurma() != null && a.getTurma().getCor() != null) ? a.getTurma().getCor() : "#3b82f6";

        boolean cancelada = ocorrencia != null && ocorrencia.getTipo() == OcorrenciaAula.Tipo.CANCELAMENTO;
        boolean substituida = ocorrencia != null && ocorrencia.getTipo() == OcorrenciaAula.Tipo.SUBSTITUICAO;

        boolean minhaTurma = isMinhaTurma(a.getTurma());
        SumarioAula sumario = sumarioParaAula(a, dataDia, sumarios);
        boolean planeada = sumario != null && sumario.temConteudo();

        String texto = a.getTurma().getDescricao();
        if (cancelada) {
            texto = "❌ " + texto + " (Cancelada)";
        } else if (substituida) {
            String nomeSubst = ocorrencia.getProfessorSubstituto() != null
                    ? ocorrencia.getProfessorSubstituto().getNome() : "—";
            texto = "🔄 " + texto + " (Subst.: " + nomeSubst + ")";
        } else if (minhaTurma) {
            texto = (planeada ? "📝 " : "⏳ ") + texto;
        }

        Span label = new Span(texto);
        label.getStyle()
                .set("font-size", "0.68rem")
                .set("font-weight", "700")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");
        if (cancelada) {
            label.getStyle().set("text-decoration", "line-through");
        }

        Div div = new Div(label);
        div.addClassName("schedule-card"); // Ativa o efeito hover 3D
        div.getStyle()
                .set("position", "absolute")
                .set("top", (top + 2) + "px")
                .set("height", (height - 4) + "px")
                .set("left", "3px")
                .set("width", "calc(100% - 6px)")
                .set("background-color", cancelada ? "#94a3b8" : corBase)
                .set("color", "#ffffff")
                .set("z-index", "10")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("border-radius", "6px")
                .set("padding", "0 4px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)")
                .set("border-left", "4px solid " + (substituida ? "#E67E22" : "rgba(0,0,0,0.15)"));
        if (cancelada) {
            div.getStyle().set("opacity", "0.65");
        } else if (minhaTurma && !planeada) {
            // Aula do professor ainda por planear: contorno tracejado âmbar.
            div.getStyle()
                    .set("border", "2px dashed #f59e0b")
                    .set("box-shadow", "0 0 0 1px rgba(245,158,11,0.35), 0 1px 3px rgba(0,0,0,0.1)");
        }

        div.getStyle().set("cursor", "pointer");
        if (isAdmin) {
            div.addClickListener(e -> abrirDialogEditarAula(a, dataDia));
        } else if (minhaTurma) {
            // Professor, aula sua: planeamento + vídeos.
            div.getElement().setAttribute("title", planeada ? "Ver / editar plano" : "Planear esta aula");
            div.addClickListener(e -> abrirDialogPlanoAula(a, dataDia, sumario));
        } else if (a.getTurma() != null) {
            // Professor, aula de outro: só vídeos.
            div.addClickListener(e -> abrirDialogVideosAula(a.getTurma(), dataDia));
        }
        return div;
    }

    private SumarioAula sumarioParaAula(Aula a, LocalDate data, List<SumarioAula> sumarios) {
        if (a.getTurma() == null || sumarios == null) {
            return null;
        }
        return sumarios.stream()
                .filter(s -> s.getTurma() != null && s.getTurma().getId().equals(a.getTurma().getId()))
                .filter(s -> data.equals(s.getData()))
                .filter(s -> a.getHoraInicio() == null || a.getHoraInicio().equals(s.getHoraInicio()))
                .findFirst().orElse(null);
    }

    /**
     * Diálogo (adaptado a mobile) para o professor consultar/editar o plano e o
     * sumário de uma aula sua, com atalho para os vídeos.
     */
    private void abrirDialogPlanoAula(Aula a, LocalDate data, SumarioAula sumarioGrelha) {
        Turma turma = a.getTurma();
        SumarioAula s = sumarioGrelha;
        if (s == null && turma != null && a.getHoraInicio() != null) {
            s = sumarioAulaRepository.findByTurmaAndDataAndHoraInicio(turma, data, a.getHoraInicio()).orElse(null);
        }
        final SumarioAula existente = s;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(turma != null ? turma.getDescricao() : "Aula");
        dialog.setWidth("94vw");
        dialog.setMaxWidth("480px");

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter
                .ofPattern("EEEE, d 'de' MMMM", new java.util.Locale("pt", "PT"));
        String horas = a.getHoraInicio() + (a.getHoraFim() != null ? " – " + a.getHoraFim() : "");
        Span sub = new Span(capitalizarPt(data.format(fmt)) + "  ·  " + horas
                + (a.getSala() != null ? "  ·  " + a.getSala().getNome() : ""));
        sub.getStyle().set("font-size", "0.85rem").set("color", "#64748b").set("font-weight", "600");

        TextArea planeamento = new TextArea("📝 Plano da aula");
        planeamento.setPlaceholder("O que vais dar nesta aula...");
        planeamento.setWidthFull();
        planeamento.setMinHeight("120px");
        if (existente != null && existente.getPlaneamento() != null) planeamento.setValue(existente.getPlaneamento());

        TextArea sumarioTxt = new TextArea("✅ Sumário (depois da aula)");
        sumarioTxt.setPlaceholder("Resumo do que foi dado...");
        sumarioTxt.setWidthFull();
        sumarioTxt.setMinHeight("120px");
        if (existente != null && existente.getSumario() != null) sumarioTxt.setValue(existente.getSumario());

        VerticalLayout conteudo = new VerticalLayout(sub, planeamento, sumarioTxt);
        conteudo.setPadding(false);
        conteudo.setSpacing(true);

        if (existente != null && existente.isEnviado() && existente.getDataEnvio() != null) {
            Span env = new Span("Sumário enviado à turma em " + DataUtil.formatar(existente.getDataEnvio()));
            env.getStyle().set("color", "#15803d").set("font-weight", "600").set("font-size", "0.8rem");
            conteudo.add(env);
        }
        dialog.add(conteudo);

        Button btnVideos = new Button("🎬 Vídeos", e -> abrirDialogVideosAula(turma, data));
        btnVideos.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        btnVideos.setEnabled(turma != null);

        Button guardar = new Button("Guardar", e -> guardarPlanoAula(a, data, existente,
                planeamento.getValue(), sumarioTxt.getValue(), false, dialog));
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button enviar = new Button("Guardar e enviar sumário", e -> guardarPlanoAula(a, data, existente,
                planeamento.getValue(), sumarioTxt.getValue(), true, dialog));
        enviar.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        enviar.setEnabled(sumarioTxt.getValue() != null && !sumarioTxt.getValue().isBlank());
        sumarioTxt.addValueChangeListener(e -> enviar.setEnabled(e.getValue() != null && !e.getValue().isBlank()));

        dialog.getFooter().add(btnVideos, new Button("Fechar", e -> dialog.close()), guardar, enviar);
        dialog.open();
    }

    private void guardarPlanoAula(Aula a, LocalDate data, SumarioAula existente, String plano, String sumarioTxt,
            boolean enviar, Dialog dialog) {
        Turma turma = a.getTurma();
        if (turma == null) return;

        SumarioAula s = existente != null ? existente : new SumarioAula();
        s.setTurma(turma);
        s.setData(data);
        s.setHoraInicio(a.getHoraInicio());
        s.setHoraFim(a.getHoraFim());
        s.setTipo(a.getTipo() != null ? a.getTipo() : "REGULAR");
        s.setStudio(TenantContext.getCurrentStudio());
        if (s.getProfessor() == null) {
            s.setProfessor(professorLogado != null ? professorLogado.getNome()
                    : (turma.getProfessor() != null ? turma.getProfessor().getNome() : null));
        }
        s.setPlaneamento(plano);
        s.setSumario(sumarioTxt);

        if (enviar) {
            if (sumarioTxt == null || sumarioTxt.isBlank()) {
                Notification.show("Escreve o sumário antes de enviar.")
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            List<String> emails = turmaService.getAlunosDaTurma(turma).stream()
                    .map(Aluno::getEmail).filter(em -> em != null && !em.isBlank()).distinct()
                    .collect(java.util.stream.Collectors.toList());
            if (emails.isEmpty()) {
                Notification.show("Nenhum aluno desta turma tem email registado.")
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            try {
                String assunto = "Sumário da aula — " + turma.getDescricao() + " (" + DataUtil.formatar(data) + ")";
                emailService.enviarEmailParaLista(TenantContext.getCurrentStudio(), turma.getProfessor(), emails, assunto, sumarioTxt);
                s.setEnviado(true);
                s.setDataEnvio(LocalDateTime.now());
                Notification.show("Sumário enviado a " + emails.size() + " aluno(s).")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Erro ao enviar: " + ex.getMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
        } else {
            Notification.show("Plano guardado.").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }
        sumarioAulaRepository.save(s);
        dialog.close();
        atualizarTudo();
    }

    private static String capitalizarPt(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private Div criarElementoMarcacao(MarcacaoSala m) {
        double top = calcularTop(m.getHoraInicio());
        double height = calcularAltura(m.getHoraInicio(), m.getHoraFim());

        boolean isPendente = "PENDENTE".equalsIgnoreCase(m.getStatus());
        String corFundo = isPendente ? "#94a3b8" : "#f97316";
        String texto = (isPendente ? "⏱️ " : "") + m.getTipo() + " (" + m.getProfessor() + ")";

        Span label = new Span(texto);
        label.getStyle()
                .set("font-size", "0.68rem")
                .set("font-weight", "700")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");

        Div div = new Div(label);
        div.addClassName("schedule-card"); // Ativa o efeito hover 3D
        div.getStyle()
                .set("position", "absolute")
                .set("top", (top + 2) + "px")
                .set("height", (height - 4) + "px")
                .set("left", "3px")
                .set("width", "calc(100% - 6px)")
                .set("background-color", corFundo)
                .set("color", "#ffffff")
                .set("z-index", "10")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("border-radius", "6px")
                .set("padding", "0 4px")
                .set("box-shadow", "0 1px 3px rgba(0,0,0,0.1)");

        if (isPendente) {
            div.getStyle().set("border", "1px dashed #475569");
        }

        if (isAdmin) {
            div.getStyle().set("cursor", "pointer");
            div.addClickListener(e -> abrirDialogEditarMarcacao(m));
        }
        return div;
    }

    private double calcularTop(LocalTime hora) {
        int minutes = (hora.getHour() - HORA_INICIO) * 60 + hora.getMinute();
        return minutes * (PIXELS_POR_HORA / 60.0);
    }

    private double calcularAltura(LocalTime inicio, LocalTime fim) {
        long minutes = Duration.between(inicio, fim).toMinutes();
        return minutes * (PIXELS_POR_HORA / 60.0);
    }

    private void abrirDialogAdicionarAulaRegular() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Nova Aula Regular");
        dialog.setWidth("460px");
        dialog.setMaxWidth("100%");

        ComboBox<Turma> comboTurma = new ComboBox<>("Turma", getTurmasDoStudio());
        comboTurma.setItemLabelGenerator(Turma::getDescricao);
        comboTurma.setWidthFull();

        ComboBox<Sala> comboSala = new ComboBox<>("Sala", getSalasDoStudio());
        comboSala.setItemLabelGenerator(Sala::getNome);
        comboSala.setWidthFull();

        ComboBox<DayOfWeek> comboDia = new ComboBox<>("Dia da Semana", diasSemana);
        comboDia.setItemLabelGenerator(this::traduzirDia);
        comboDia.setWidthFull();

        TimePicker inicio = new TimePicker("Início");
        inicio.setWidthFull();
        TimePicker fim = new TimePicker("Fim");
        fim.setWidthFull();

        HorizontalLayout tempoLayout = new HorizontalLayout(inicio, fim);
        tempoLayout.setWidthFull();
        tempoLayout.getStyle().set("flex-wrap", "wrap");

        DatePicker dataInicio = new DatePicker("Início do período (opcional)");
        dataInicio.setWidthFull();
        dataInicio.setHelperText("Ex.: arranque a 14/09. Vazio = todo o ano letivo.");
        DatePicker dataFim = new DatePicker("Fim do período (opcional)");
        dataFim.setWidthFull();
        HorizontalLayout periodoLayout = new HorizontalLayout(dataInicio, dataFim);
        periodoLayout.setWidthFull();
        periodoLayout.getStyle().set("flex-wrap", "wrap");

        VerticalLayout form = new VerticalLayout(comboTurma, comboSala, comboDia, tempoLayout, periodoLayout);
        form.setPadding(false);
        form.setSpacing(true);
        dialog.add(form);

        Button btnGuardar = new Button("Guardar", e -> {
            Aula aula = new Aula();
            aula.setTurma(comboTurma.getValue());
            aula.setSala(comboSala.getValue());
            aula.setDia(comboDia.getValue());
            aula.setHoraInicio(inicio.getValue());
            aula.setHoraFim(fim.getValue());
            aula.setDataInicio(dataInicio.getValue());
            aula.setDataFim(dataFim.getValue());
            aula.setTipo("NORMAL");
            aulaRepository.save(aula);
            dialog.close();
            atualizarTudo();
        });
        btnGuardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button btnCancelar = new Button("Cancelar", e -> dialog.close());
        btnCancelar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(btnCancelar, btnGuardar);
        dialog.open();
    }

    private void abrirDialogAdicionarAulaPontual() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isAdmin ? "Nova Aula Pontual" : "Pedir Reserva de Sala");
        dialog.setWidth("460px");
        dialog.setMaxWidth("100%");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        String userLogado = VaadinServletRequest.getCurrent().getHttpServletRequest().getUserPrincipal().getName();
        String primeiroNomeUser = resolverPrimeiroNomeUserLogado(userLogado);

        Professor profLogado = getProfessoresDoStudio().stream()
                .filter(p -> p.getNome() != null && !p.getNome().trim().isEmpty())
                .filter(p -> {
                    String nomeProfDB = p.getNome().split(" ")[0].toLowerCase();
                    return primeiroNomeUser.contains(nomeProfDB);
                })
                .findFirst()
                .orElse(null);

        final Professor professorFinal;

        if (!isAdmin) {
            TextField tfProf = new TextField("Professor");
            tfProf.setWidthFull();
            tfProf.setValue(profLogado != null ? profLogado.getNome() : userLogado);
            tfProf.setReadOnly(true);
            layout.add(tfProf);
            professorFinal = profLogado;
        } else {
            ComboBox<Professor> comboProf = new ComboBox<>("Professor", getProfessoresDoStudio());
            comboProf.setItemLabelGenerator(Professor::getNome);
            comboProf.setWidthFull();
            layout.add(comboProf);
            professorFinal = null;
        }

        ComboBox<LocalDate> comboData = new ComboBox<>("Data do Evento", Arrays.asList(
                semanaAtual, semanaAtual.plusDays(1), semanaAtual.plusDays(2),
                semanaAtual.plusDays(3), semanaAtual.plusDays(4), semanaAtual.plusDays(5), semanaAtual.plusDays(6)));
        comboData.setWidthFull();

        ComboBox<Sala> comboSala = new ComboBox<>("Sala", getSalasDoStudio());
        comboSala.setItemLabelGenerator(Sala::getNome);
        comboSala.setWidthFull();

        ComboBox<String> tipo = new ComboBox<>("Tipo", "PRIVADA", "ENSAIO", "EXTRA/COMPENSAÇÃO");
        tipo.setWidthFull();

        ComboBox<Turma> comboTurma = new ComboBox<>("Turma", getTurmasDoStudio());
        comboTurma.setItemLabelGenerator(Turma::getDescricao);
        comboTurma.setVisible(false);
        comboTurma.setWidthFull();

        MultiSelectComboBox<Aluno> comboAlunos = new MultiSelectComboBox<>("Alunos Envolvidos",
                getAlunosDoStudio());
        comboAlunos.setItemLabelGenerator(this::rotuloAluno);
        comboAlunos.setVisible(false);
        comboAlunos.setWidthFull();

        tipo.addValueChangeListener(e -> {
            comboTurma.setVisible("ENSAIO".equals(e.getValue()) || "EXTRA/COMPENSAÇÃO".equals(e.getValue()));
            comboAlunos.setVisible("PRIVADA".equals(e.getValue()));
        });

        TimePicker inicio = new TimePicker("Início");
        TimePicker fim = new TimePicker("Fim");
        HorizontalLayout hoursRow = new HorizontalLayout(inicio, fim);
        hoursRow.setWidthFull();
        hoursRow.getStyle().set("flex-wrap", "wrap");

        TextArea obs = new TextArea("Observações / Justificação");
        obs.setWidthFull();

        layout.add(comboData, comboSala, tipo, comboTurma, comboAlunos, hoursRow, obs);
        dialog.add(layout);

        Button guardar = new Button(isAdmin ? "Registar" : "Submeter Pedido", e -> {
            if (comboData.isEmpty() || comboSala.isEmpty() || tipo.isEmpty()) {
                Notification.show("Por favor, preencha a Data, Sala e Tipo!");
                return;
            }

            if (inicio.isEmpty() || fim.isEmpty()) {
                Notification.show("Indique a hora de início e de fim!");
                return;
            }

            MarcacaoSala m = new MarcacaoSala();
            m.setStudio(TenantContext.getCurrentStudio());
            m.setData(comboData.getValue());
            m.setSala(comboSala.getValue());

            if (!isAdmin) {
                m.setProfessor(professorFinal != null ? professorFinal.getNome() : userLogado);
            } else {
                @SuppressWarnings("unchecked")
                ComboBox<Professor> cb = (ComboBox<Professor>) layout.getComponentAt(0);
                m.setProfessor(cb.getValue() != null ? cb.getValue().getNome() : "");
            }

            m.setTipo(tipo.getValue());
            m.setHoraInicio(inicio.getValue());
            m.setHoraFim(fim.getValue());
            m.setObservacoes(obs.getValue());
            m.setStatus(isAdmin ? "APROVADO" : "PENDENTE");

            if (comboTurma.isVisible())
                m.setTurma(comboTurma.getValue());
            if (comboAlunos.isVisible())
                m.setAlunos(new ArrayList<>(comboAlunos.getValue()));

            marcacaoRepository.save(m);
            dialog.close();
            atualizarTudo();

            try {
                emailService.notificarAdminNovoPedido(
                        TenantContext.getCurrentStudio(),
                        m.getProfessor(), m.getTipo(), m.getTurma() != null ? m.getTurma().getDescricao() : "",
                        m.getSala().getNome(), pt.studioflow.util.DataUtil.formatar(m.getData()),
                        m.getHoraInicio().toString(), m.getHoraFim().toString(),
                        m.getObservacoes() != null ? m.getObservacoes() : "");
            } catch (Exception ex) {
                // o pedido foi gravado; falha no email não deve bloquear o fluxo
            }

            Notification.show("Agendamento processado com sucesso!");
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelar = new Button("Cancelar", e -> dialog.close());
        cancelar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(cancelar, guardar);
        dialog.open();
    }

    // Gestão dos períodos do ano letivo sem aulas (Natal, Páscoa, feriados),
    // por estúdio. Usados nas estimativas do relatório de rentabilidade.
    private void abrirDialogInterrupcoes() {
        pt.studioflow.model.Studio studio = TenantContext.getCurrentStudio();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Interrupções letivas");
        dialog.setWidth("560px");
        dialog.setMaxWidth("100%");

        Grid<pt.studioflow.model.InterrupcaoLetiva> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(pt.studioflow.model.InterrupcaoLetiva::getDescricao).setHeader("Descrição").setFlexGrow(1);
        grid.addColumn(i -> i.getDataInicio() != null ? DataUtil.formatar(i.getDataInicio()) : "-")
                .setHeader("De").setAutoWidth(true);
        grid.addColumn(i -> i.getDataFim() != null ? DataUtil.formatar(i.getDataFim()) : "-")
                .setHeader("Até").setAutoWidth(true);
        grid.addComponentColumn(i -> {
            Button rem = new Button(VaadinIcon.TRASH.create(), e -> {
                interrupcaoRepository.delete(i);
                grid.setItems(interrupcaoRepository.findByStudioOrderByDataInicioAsc(studio));
            });
            rem.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return rem;
        }).setHeader("").setAutoWidth(true);
        grid.setItems(interrupcaoRepository.findByStudioOrderByDataInicioAsc(studio));
        grid.setAllRowsVisible(true);

        TextField desc = new TextField("Descrição");
        desc.setWidthFull();
        DatePicker de = new DatePicker("De");
        DatePicker ate = new DatePicker("Até");
        HorizontalLayout datas = new HorizontalLayout(de, ate);
        datas.setWidthFull();
        datas.getStyle().set("flex-wrap", "wrap");

        Button adicionar = new Button("Adicionar", e -> {
            if (de.getValue() == null || ate.getValue() == null) {
                Notification.show("Indica as duas datas.").addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            if (ate.getValue().isBefore(de.getValue())) {
                Notification.show("A data final é anterior à inicial.")
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            interrupcaoRepository.save(new pt.studioflow.model.InterrupcaoLetiva(studio,
                    desc.getValue() != null && !desc.getValue().isBlank() ? desc.getValue() : "Interrupção",
                    de.getValue(), ate.getValue()));
            desc.clear();
            de.clear();
            ate.clear();
            grid.setItems(interrupcaoRepository.findByStudioOrderByDataInicioAsc(studio));
        });
        adicionar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout form = new VerticalLayout(grid, desc, datas, adicionar);
        form.setPadding(false);
        dialog.add(form);
        dialog.getFooter().add(new Button("Fechar", e -> dialog.close()));
        dialog.open();
    }

    private void abrirDialogEditarAula(Aula aula, LocalDate data) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Modificar Aula Regular");
        dialog.setWidth("440px");
        dialog.setMaxWidth("100%");

        ComboBox<Turma> comboTurma = new ComboBox<>("Turma", getTurmasDoStudio());
        comboTurma.setValue(aula.getTurma());
        comboTurma.setItemLabelGenerator(Turma::getDescricao);
        comboTurma.setWidthFull();

        TimePicker inicio = new TimePicker("Início", aula.getHoraInicio());
        TimePicker fim = new TimePicker("Fim", aula.getHoraFim());
        HorizontalLayout tempo = new HorizontalLayout(inicio, fim);
        tempo.setWidthFull();
        tempo.getStyle().set("flex-wrap", "wrap");

        DatePicker dataInicio = new DatePicker("Início do período (opcional)", aula.getDataInicio());
        dataInicio.setWidthFull();
        dataInicio.setHelperText("Vazio = todo o ano letivo");
        DatePicker dataFim = new DatePicker("Fim do período (opcional)", aula.getDataFim());
        dataFim.setWidthFull();
        HorizontalLayout periodo = new HorizontalLayout(dataInicio, dataFim);
        periodo.setWidthFull();
        periodo.getStyle().set("flex-wrap", "wrap");

        Button btnVideos = new Button("🎬 Vídeos da aula (" + DataUtil.formatar(data) + ")",
                e -> abrirDialogVideosAula(aula.getTurma(), data));
        btnVideos.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        btnVideos.setEnabled(aula.getTurma() != null && data != null);

        VerticalLayout layout = new VerticalLayout(comboTurma, tempo, periodo, btnVideos);
        layout.setPadding(false);
        dialog.add(layout);

        Button guardar = new Button("Guardar Alterações", e -> {
            aula.setTurma(comboTurma.getValue());
            aula.setHoraInicio(inicio.getValue());
            aula.setHoraFim(fim.getValue());
            aula.setDataInicio(dataInicio.getValue());
            aula.setDataFim(dataFim.getValue());
            aulaRepository.save(aula);
            dialog.close();
            atualizarTudo();
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button apagar = new Button("Remover", e -> {
            aulaRepository.delete(aula);
            dialog.close();
            atualizarTudo();
        });
        apagar.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(apagar, guardar);
        dialog.open();
    }

    /**
     * Vídeos de uma aula concreta (turma + data), acessível ao clicar na aula
     * no mapa de salas. Reutiliza o armazenamento R2 / {@link VideoAula} — os
     * alunos veem estes vídeos no portal.
     */
    private void abrirDialogVideosAula(Turma turma, LocalDate data) {
        if (turma == null || data == null) {
            Notification.show("Esta aula não tem turma/data associada.")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Vídeos · " + turma.getDescricao() + " · " + DataUtil.formatar(data));
        dialog.setWidth("440px");
        dialog.setMaxWidth("100%");

        FlexLayout lista = new FlexLayout();
        lista.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        lista.getStyle().set("gap", "12px").set("margin-top", "4px");

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            lista.removeAll();
            List<VideoAula> videos = videoAulaRepository.findByTurmaAndDataOrderByDataUploadDesc(turma, data);
            if (videos.isEmpty()) {
                Span vazio = new Span("Ainda não há vídeos para esta aula.");
                vazio.getStyle().set("color", "#888");
                lista.add(vazio);
            } else {
                videos.forEach(v -> lista.add(criarCardVideoMapa(v, refresh[0])));
            }
        };

        FileBuffer buffer = new FileBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("video/mp4", "video/quicktime", "video/x-msvideo", "video/webm");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(300 * 1024 * 1024);
        upload.setUploadButton(new Button("Enviar vídeo"));
        upload.setDropLabel(new Span("ou arrastar aqui (vídeo, máx 300MB)"));
        upload.addSucceededListener(event -> {
            Studio studio = TenantContext.getCurrentStudio();
            try {
                String nome = event.getFileName();
                String chave = "videos/" + (studio != null ? studio.getSlug() : "sem-estudio") + "/" + turma.getId()
                        + "/" + data + "/" + UUID.randomUUID() + "_" + nome;
                storageService.upload(chave, resolverContentTypeVideo(event.getMIMEType(), nome),
                        buffer.getInputStream(), event.getContentLength());
                VideoAula v = new VideoAula();
                v.setTurma(turma);
                v.setData(data);
                v.setChaveArmazenamento(chave);
                v.setNomeFicheiro(nome);
                v.setTamanhoBytes(event.getContentLength());
                v.setProfessor(turma.getProfessor());
                v.setStudio(studio);
                videoAulaRepository.save(v);
                upload.clearFileList();
                refresh[0].run();
                Notification.show("Vídeo enviado!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Erro ao enviar: " + ex.getMessage(), 6000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        upload.addFailedListener(e -> Notification.show("Falha no upload: " + e.getReason().getMessage(), 5000,
                Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR));

        VerticalLayout content = new VerticalLayout(upload, lista);
        content.setPadding(false);
        content.setSpacing(true);
        dialog.add(content);
        dialog.getFooter().add(new Button("Fechar", e -> dialog.close()));
        refresh[0].run();
        dialog.open();
    }

    private Component criarCardVideoMapa(VideoAula video, Runnable refresh) {
        VerticalLayout card = new VerticalLayout();
        card.setWidth("120px");
        card.setPadding(false);
        card.setSpacing(false);
        card.setAlignItems(Alignment.CENTER);
        card.getStyle().set("border", "1px solid #e2e8f0").set("border-radius", "10px").set("padding", "8px");

        Div thumb = new Div(VaadinIcon.PLAY_CIRCLE.create());
        thumb.getStyle().set("width", "100%").set("height", "56px").set("border-radius", "6px")
                .set("display", "flex").set("align-items", "center").set("justify-content", "center")
                .set("background", "linear-gradient(135deg, #1e293b 0%, #334155 100%)").set("color", "white");
        Anchor play = new Anchor(
                storageService.gerarUrlTemporario(video.getChaveArmazenamento(), Duration.ofHours(2)), thumb);
        play.setTarget("_blank");
        play.setRouterIgnore(true);
        play.getStyle().set("width", "100%").set("text-decoration", "none");

        Span nome = new Span(video.getNomeFicheiro());
        nome.getStyle().set("font-size", "0.65rem").set("text-align", "center").set("width", "100%")
                .set("white-space", "nowrap").set("overflow", "hidden").set("text-overflow", "ellipsis")
                .set("margin-top", "4px");

        Button apagar = new Button(VaadinIcon.TRASH.create(), e -> {
            ConfirmDialog cd = new ConfirmDialog();
            cd.setHeader("Apagar vídeo?");
            cd.setText("\"" + video.getNomeFicheiro() + "\" será removido.");
            cd.setCancelable(true);
            cd.setConfirmText("Apagar");
            cd.setConfirmButtonTheme("error primary");
            cd.addConfirmListener(ev -> {
                try {
                    storageService.apagar(video.getChaveArmazenamento());
                } catch (Exception ignored) {
                }
                videoAulaRepository.delete(video);
                refresh.run();
            });
            cd.open();
        });
        apagar.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        card.add(play, nome, apagar);
        return card;
    }

    private static String resolverContentTypeVideo(String mime, String nomeFicheiro) {
        if (mime != null && !mime.isBlank() && !"application/octet-stream".equalsIgnoreCase(mime)) {
            return mime;
        }
        String nome = nomeFicheiro == null ? "" : nomeFicheiro.toLowerCase();
        int ponto = nome.lastIndexOf('.');
        String ext = ponto >= 0 ? nome.substring(ponto + 1) : "";
        return switch (ext) {
            case "mp4", "m4v" -> "video/mp4";
            case "mov", "qt" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "avi" -> "video/x-msvideo";
            case "mkv" -> "video/x-matroska";
            default -> "application/octet-stream";
        };
    }

    private void abrirDialogEditarMarcacao(MarcacaoSala mSimplificada) {
        MarcacaoSala marcacao = marcacaoRepository.findByIdComAlunosETurma(mSimplificada.getId())
                .orElseThrow(() -> new RuntimeException("Registo não encontrado"));

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Gestão da Reserva");
        dialog.setWidth("440px");
        dialog.setMaxWidth("100%");

        ComboBox<Professor> comboProf = new ComboBox<>("Professor Responsável", getProfessoresDoStudio());
        comboProf.setItemLabelGenerator(Professor::getNome);
        comboProf.setWidthFull();
        if (marcacao.getProfessor() != null) {
            getProfessoresDoStudio().stream().filter(p -> p.getNome().equals(marcacao.getProfessor())).findFirst()
                    .ifPresent(comboProf::setValue);
        }

        ComboBox<String> comboStatus = new ComboBox<>("Estado do Pedido", "PENDENTE", "APROVADO");
        comboStatus.setValue(marcacao.getStatus());
        comboStatus.setWidthFull();
        comboStatus.setEnabled(isAdmin);

        TimePicker inicio = new TimePicker("Início", marcacao.getHoraInicio());
        TimePicker fim = new TimePicker("Fim", marcacao.getHoraFim());
        HorizontalLayout hours = new HorizontalLayout(inicio, fim);
        hours.setWidthFull();
        hours.getStyle().set("flex-wrap", "wrap");

        TextArea obs = new TextArea("Observações adicionais", marcacao.getObservacoes());
        obs.setWidthFull();

        VerticalLayout layout = new VerticalLayout(comboProf, comboStatus, hours, obs);
        layout.setPadding(false);
        dialog.add(layout);

        Button guardar = new Button("Salvar", e -> {
            marcacao.setProfessor(comboProf.getValue() != null ? comboProf.getValue().getNome() : "");
            marcacao.setHoraInicio(inicio.getValue());
            marcacao.setHoraFim(fim.getValue());
            marcacao.setObservacoes(obs.getValue());
            marcacao.setStatus(comboStatus.getValue());
            marcacaoRepository.save(marcacao);
            dialog.close();
            atualizarTudo();
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button apagar = new Button("Eliminar", e -> {
            marcacaoRepository.delete(marcacao);
            dialog.close();
            atualizarTudo();
        });
        apagar.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(apagar, guardar);
        dialog.open();
    }

    private void abrirDialogEscolherTipoAula(LocalDate data, LocalTime hora, Sala sala) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Escolha o Âmbito da Aula");
        dialog.setWidth("400px");
        dialog.setMaxWidth("100%");

        Button r = new Button("Aula Regular", e -> {
            dialog.close();
            abrirDialogAdicionarAulaRegularPrePreenchido(data, hora, sala);
        });
        r.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        r.getStyle().set("flex", "1 1 140px");

        Button p = new Button("Aula Pontual / Reserva", e -> {
            dialog.close();
            abrirDialogAdicionarAulaPontualPrePreenchido(data, hora, sala);
        });
        p.getStyle().set("flex", "1 1 140px");

        HorizontalLayout choices = new HorizontalLayout(r, p);
        choices.setPadding(true);
        choices.setWidthFull();
        choices.getStyle()
                .set("flex-wrap", "wrap")
                .set("gap", "12px");

        dialog.add(choices);
        dialog.open();
    }

    private void abrirDialogAdicionarAulaRegularPrePreenchido(LocalDate data, LocalTime hora, Sala sala) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Nova Aula Regular (" + sala.getNome() + ")");
        dialog.setWidth("440px");
        dialog.setMaxWidth("100%");

        ComboBox<Turma> comboTurma = new ComboBox<>("Turma", getTurmasDoStudio());
        comboTurma.setItemLabelGenerator(Turma::getDescricao);
        comboTurma.setWidthFull();

        TimePicker inicio = new TimePicker("Horário de Início", hora);
        inicio.setWidthFull();
        TimePicker fim = new TimePicker("Horário de Fim", hora.plusHours(1));
        fim.setWidthFull();

        HorizontalLayout tempo = new HorizontalLayout(inicio, fim);
        tempo.setWidthFull();
        tempo.getStyle().set("flex-wrap", "wrap");

        DatePicker dataInicio = new DatePicker("Início do período (opcional)");
        dataInicio.setWidthFull();
        dataInicio.setHelperText("Vazio = todo o ano letivo");
        DatePicker dataFim = new DatePicker("Fim do período (opcional)");
        dataFim.setWidthFull();
        HorizontalLayout periodo = new HorizontalLayout(dataInicio, dataFim);
        periodo.setWidthFull();
        periodo.getStyle().set("flex-wrap", "wrap");

        VerticalLayout content = new VerticalLayout(comboTurma, tempo, periodo);
        content.setPadding(false);
        dialog.add(content);

        Button g = new Button("Agendar Regular", e -> {
            Aula a = new Aula();
            a.setTurma(comboTurma.getValue());
            a.setSala(sala);
            a.setDia(data.getDayOfWeek());
            a.setHoraInicio(inicio.getValue());
            a.setHoraFim(fim.getValue());
            a.setDataInicio(dataInicio.getValue());
            a.setDataFim(dataFim.getValue());
            a.setTipo("NORMAL");
            aulaRepository.save(a);
            dialog.close();
            atualizarTudo();
        });
        g.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), g);
        dialog.open();
    }

    private void abrirDialogAdicionarAulaPontualPrePreenchido(LocalDate data, LocalTime hora, Sala sala) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(isAdmin ? "Nova Marcação Direta" : "Pedir Reserva de Sala");
        dialog.setWidth("420px");
        dialog.setMaxWidth("100%");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);

        String userLogado = VaadinServletRequest.getCurrent().getHttpServletRequest().getUserPrincipal().getName();
        String primeiroNomeUser = resolverPrimeiroNomeUserLogado(userLogado);

        Professor profLogado = getProfessoresDoStudio().stream()
                .filter(p -> p.getNome() != null && !p.getNome().trim().isEmpty())
                .filter(p -> {
                    String nomeProfDB = p.getNome().split(" ")[0].toLowerCase();
                    return primeiroNomeUser.contains(nomeProfDB);
                })
                .findFirst()
                .orElse(null);

        final Professor professorFinal;

        if (!isAdmin) {
            TextField tfProf = new TextField("Professor Requerente");
            tfProf.setWidthFull();
            if (profLogado != null) {
                tfProf.setValue(profLogado.getNome());
                professorFinal = profLogado;
            } else {
                tfProf.setValue(userLogado);
                professorFinal = null;
            }
            tfProf.setReadOnly(true);
            layout.add(tfProf);
        } else {
            ComboBox<Professor> comboProf = new ComboBox<>("Professor Responsável", getProfessoresDoStudio());
            comboProf.setItemLabelGenerator(Professor::getNome);
            comboProf.setWidthFull();
            if (profLogado != null) {
                comboProf.setValue(profLogado);
            }
            layout.add(comboProf);
            professorFinal = null;
        }

        ComboBox<String> tipo = new ComboBox<>("Tipo de Reserva", "PRIVADA", "ENSAIO", "EXTRA/COMPENSAÇÃO");
        tipo.setWidthFull();

        ComboBox<Turma> comboTurma = new ComboBox<>("Turma", getTurmasDoStudio());
        comboTurma.setItemLabelGenerator(Turma::getDescricao);
        comboTurma.setVisible(false);
        comboTurma.setWidthFull();

        MultiSelectComboBox<Aluno> comboAlunos = new MultiSelectComboBox<>("Alunos Atribuídos",
                getAlunosDoStudio());
        comboAlunos.setItemLabelGenerator(this::rotuloAluno);
        comboAlunos.setVisible(false);
        comboAlunos.setWidthFull();

        tipo.addValueChangeListener(e -> {
            comboTurma.setVisible("ENSAIO".equals(e.getValue()) || "EXTRA/COMPENSAÇÃO".equals(e.getValue()));
            comboAlunos.setVisible("PRIVADA".equals(e.getValue()));
        });

        TimePicker inicio = new TimePicker("Início", hora);
        TimePicker fim = new TimePicker("Fim", hora.plusHours(1));
        HorizontalLayout times = new HorizontalLayout(inicio, fim);
        times.setWidthFull();
        times.getStyle().set("flex-wrap", "wrap");

        TextArea obs = new TextArea("Justificação / Observações");
        obs.setWidthFull();

        layout.add(tipo, comboTurma, comboAlunos, times, obs);
        dialog.add(layout);

        Button guardar = new Button(isAdmin ? "Aprovar e Gravar" : "Enviar Pedido", e -> {
            if (tipo.isEmpty()) {
                Notification.show("Selecione o tipo de alocação!");
                return;
            }

            if (inicio.isEmpty() || fim.isEmpty()) {
                Notification.show("Indique a hora de início e de fim!");
                return;
            }

            MarcacaoSala m = new MarcacaoSala();
            m.setStudio(TenantContext.getCurrentStudio());
            m.setSala(sala);
            m.setData(data);

            if (!isAdmin) {
                m.setProfessor(professorFinal != null ? professorFinal.getNome() : userLogado);
            } else {
                @SuppressWarnings("unchecked")
                ComboBox<Professor> cb = (ComboBox<Professor>) layout.getComponentAt(0);
                m.setProfessor(cb.getValue() != null ? cb.getValue().getNome() : "");
            }

            m.setTipo(tipo.getValue());
            m.setHoraInicio(inicio.getValue());
            m.setHoraFim(fim.getValue());
            m.setObservacoes(obs.getValue());
            m.setStatus(isAdmin ? "APROVADO" : "PENDENTE");

            if (comboTurma.isVisible())
                m.setTurma(comboTurma.getValue());
            if (comboAlunos.isVisible())
                m.setAlunos(new ArrayList<>(comboAlunos.getValue()));

            marcacaoRepository.save(m);
            dialog.close();
            atualizarTudo();

            try {
                emailService.notificarAdminNovoPedido(
                        TenantContext.getCurrentStudio(),
                        m.getProfessor(), m.getTipo(), m.getTurma() != null ? m.getTurma().getDescricao() : "",
                        m.getSala().getNome(), pt.studioflow.util.DataUtil.formatar(m.getData()),
                        m.getHoraInicio().toString(), m.getHoraFim().toString(),
                        m.getObservacoes() != null ? m.getObservacoes() : "");
            } catch (Exception ex) {
                // o pedido foi gravado; falha no email não deve bloquear o fluxo
            }

            Notification.show("Agendamento processado com sucesso!");
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelar = new Button("Cancelar", e -> dialog.close());
        cancelar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(cancelar, guardar);
        dialog.open();
    }
}
