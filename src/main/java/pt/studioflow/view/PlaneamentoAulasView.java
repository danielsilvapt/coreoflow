package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aula;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Sala;
import pt.studioflow.model.Studio;
import pt.studioflow.model.SumarioAula;
import pt.studioflow.model.Turma;
import pt.studioflow.model.User;
import pt.studioflow.repository.AulaRepository;
import pt.studioflow.repository.MarcacaoSalaRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.SumarioAulaRepository;
import pt.studioflow.repository.UserRepository;
import pt.studioflow.service.EmailService;
import pt.studioflow.service.TurmaService;
import pt.studioflow.util.DataUtil;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Planeamento de aulas. O professor vê e planeia as suas aulas; o admin vê o
 * planeamento de todos, com filtros por professor / turma / estado / pesquisa.
 * As "aulas" são geradas das aulas regulares ({@link Aula}) e das marcações
 * pontuais aprovadas, cruzadas com o {@link SumarioAula} de cada data.
 */
@PageTitle("Planeamento de Aulas | CoreoFlow")
@Route(value = "planeamento-aulas", layout = MainLayout.class)
@RolesAllowed({ "ADMIN", "PROF" })
public class PlaneamentoAulasView extends VerticalLayout {

    private static final Locale PT = new Locale("pt", "PT");
    private static final DateTimeFormatter FMT_DIA = DateTimeFormatter.ofPattern("EEE, d MMM", PT);
    private static final DateTimeFormatter FMT_DIA_LONGO = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT);

    private final AulaRepository aulaRepository;
    private final MarcacaoSalaRepository marcacaoRepository;
    private final SumarioAulaRepository sumarioRepository;
    private final ProfessorRepository professorRepository;
    private final UserRepository userRepository;
    private final TurmaService turmaService;
    private final EmailService emailService;

    private final boolean isAdmin;
    private final String primeiroNomeUser;

    private final ComboBox<YearMonth> mesCombo = new ComboBox<>("Mês");
    private final ComboBox<Professor> profCombo = new ComboBox<>("Professor");
    private final ComboBox<String> estadoCombo = new ComboBox<>("Estado");
    private final TextField pesquisa = new TextField();
    private final Div stats = new Div();
    private final Grid<Linha> grid = new Grid<>();

    public PlaneamentoAulasView(AulaRepository aulaRepository, MarcacaoSalaRepository marcacaoRepository,
            SumarioAulaRepository sumarioRepository, ProfessorRepository professorRepository,
            UserRepository userRepository, TurmaService turmaService, EmailService emailService) {
        this.aulaRepository = aulaRepository;
        this.marcacaoRepository = marcacaoRepository;
        this.sumarioRepository = sumarioRepository;
        this.professorRepository = professorRepository;
        this.userRepository = userRepository;
        this.turmaService = turmaService;
        this.emailService = emailService;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        this.isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        this.primeiroNomeUser = normalizar(userRepository.findByPrincipalName(auth.getName())
                .map(User::getFirstName).orElse(""));

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2(isAdmin ? "Planeamento de Aulas" : "O Meu Planeamento");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        Studio studio = TenantContext.getCurrentStudio();
        List<Professor> professores = studio != null ? professorRepository.findAllByStudio(studio)
                : professorRepository.findAll();

        List<YearMonth> meses = new ArrayList<>();
        for (int i = -2; i <= 3; i++) meses.add(YearMonth.now().plusMonths(i));
        mesCombo.setItems(meses);
        mesCombo.setItemLabelGenerator(m -> capitalizar(m.getMonth().getDisplayName(java.time.format.TextStyle.FULL, PT))
                + " " + m.getYear());
        mesCombo.setValue(YearMonth.now());
        mesCombo.setWidth("190px");
        mesCombo.addValueChangeListener(e -> atualizar());

        profCombo.setItems(professores.stream()
                .sorted(Comparator.comparing(p -> p.getNome() == null ? "" : p.getNome(), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList()));
        profCombo.setItemLabelGenerator(p -> p.getNome() != null ? p.getNome() : "—");
        profCombo.setClearButtonVisible(true);
        profCombo.setWidth("200px");
        profCombo.setVisible(isAdmin);
        profCombo.addValueChangeListener(e -> atualizar());

        estadoCombo.setItems("Por planear", "Planeada", "Sumário enviado");
        estadoCombo.setClearButtonVisible(true);
        estadoCombo.setWidth("170px");
        estadoCombo.addValueChangeListener(e -> atualizar());

        pesquisa.setPlaceholder("Pesquisar turma...");
        pesquisa.setPrefixComponent(VaadinIcon.SEARCH.create());
        pesquisa.setClearButtonVisible(true);
        pesquisa.setValueChangeMode(ValueChangeMode.LAZY);
        pesquisa.setWidth("220px");
        pesquisa.addValueChangeListener(e -> atualizar());

        HorizontalLayout filtros = new HorizontalLayout(mesCombo, profCombo, estadoCombo, pesquisa);
        filtros.setAlignItems(Alignment.END);
        filtros.getStyle().set("flex-wrap", "wrap");
        add(filtros);

        stats.getStyle().set("display", "flex").set("flex-wrap", "wrap").set("gap", "12px").set("margin", "4px 0");
        add(stats);

        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setSizeFull();
        grid.addColumn(l -> capitalizar(l.data().format(FMT_DIA))).setHeader("Data").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(l -> l.horaInicio() + (l.horaFim() != null ? "–" + l.horaFim() : ""))
                .setHeader("Hora").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(l -> l.turma().getDescricao()).setHeader("Turma").setFlexGrow(1);
        if (isAdmin) {
            grid.addColumn(l -> l.turma().getProfessor() != null ? l.turma().getProfessor().getNome() : "—")
                    .setHeader("Professor").setAutoWidth(true);
        }
        grid.addComponentColumn(l -> badgeEstado(estadoDe(l.sumario()))).setHeader("Estado").setAutoWidth(true)
                .setFlexGrow(0);
        grid.addComponentColumn(l -> {
            Button b = new Button(l.sumario() == null ? "Planear" : "Abrir", VaadinIcon.EDIT.create(),
                    e -> abrirDialogPlaneamento(l));
            b.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            return b;
        }).setHeader("").setAutoWidth(true).setFlexGrow(0);
        add(grid);
        expand(grid);

        atualizar();
    }

    // ---------------- dados ----------------

    private record Ocorrencia(LocalDate data, LocalTime horaInicio, LocalTime horaFim, Turma turma, Sala sala,
            String tipo) {
    }

    private record Linha(LocalDate data, LocalTime horaInicio, LocalTime horaFim, Turma turma, Sala sala, String tipo,
            SumarioAula sumario) {
    }

    private void atualizar() {
        YearMonth mes = mesCombo.getValue() != null ? mesCombo.getValue() : YearMonth.now();
        LocalDate ini = mes.atDay(1);
        LocalDate fim = mes.atEndOfMonth();
        Studio studio = TenantContext.getCurrentStudio();

        List<Aula> aulas = studio != null ? aulaRepository.findByStudioComHorario(studio)
                : aulaRepository.findAllComHorario();

        List<Ocorrencia> ocorrencias = new ArrayList<>();
        for (LocalDate d = ini; !d.isAfter(fim); d = d.plusDays(1)) {
            final LocalDate dia = d;
            aulas.stream()
                    .filter(a -> a.getDia() == dia.getDayOfWeek() && a.getTurma() != null && a.getHoraInicio() != null)
                    .forEach(a -> ocorrencias.add(new Ocorrencia(dia, a.getHoraInicio(), a.getHoraFim(),
                            a.getTurma(), a.getSala(), a.getTipo() != null ? a.getTipo() : "REGULAR")));
        }
        (studio != null ? marcacaoRepository.findByStudioAndDataBetween(studio, ini, fim)
                : marcacaoRepository.findByDataBetween(ini, fim)).stream()
                .filter(m -> "APROVADO".equalsIgnoreCase(m.getStatus()) && m.getTurma() != null
                        && m.getHoraInicio() != null)
                .forEach(m -> ocorrencias.add(new Ocorrencia(m.getData(), m.getHoraInicio(), m.getHoraFim(),
                        m.getTurma(), m.getSala(), m.getTipo() != null ? m.getTipo() : "PONTUAL")));

        List<SumarioAula> sumarios = studio != null ? sumarioRepository.findByStudioAndDataBetween(studio, ini, fim)
                : sumarioRepository.findByDataBetween(ini, fim);

        Long profId = profCombo.getValue() != null ? profCombo.getValue().getId() : null;
        String q = normalizar(pesquisa.getValue());
        String estadoFiltro = estadoCombo.getValue();

        List<Linha> linhas = ocorrencias.stream()
                .filter(o -> {
                    if (!isAdmin) {
                        Professor p = o.turma().getProfessor();
                        return p != null && p.getNome() != null
                                && !primeiroNomeUser.isBlank()
                                && normalizar(p.getNome()).contains(primeiroNomeUser);
                    }
                    if (profId != null) {
                        Professor p = o.turma().getProfessor();
                        return p != null && profId.equals(p.getId());
                    }
                    return true;
                })
                .filter(o -> q.isBlank() || normalizar(o.turma().getDescricao()).contains(q))
                .map(o -> new Linha(o.data(), o.horaInicio(), o.horaFim(), o.turma(), o.sala(), o.tipo(),
                        sumarios.stream()
                                .filter(s -> s.getTurma() != null && s.getTurma().getId().equals(o.turma().getId())
                                        && o.data().equals(s.getData())
                                        && (s.getHoraInicio() == null || s.getHoraInicio().equals(o.horaInicio())))
                                .findFirst().orElse(null)))
                .filter(l -> estadoFiltro == null || estadoDe(l.sumario()).equals(estadoFiltro))
                .sorted(Comparator.comparing(Linha::data).thenComparing(Linha::horaInicio))
                .collect(Collectors.toList());

        long total = linhas.size();
        long planeadas = linhas.stream().filter(l -> !estadoDe(l.sumario()).equals("Por planear")).count();
        long enviadas = linhas.stream().filter(l -> estadoDe(l.sumario()).equals("Sumário enviado")).count();
        stats.removeAll();
        stats.add(statCard("Aulas no mês", String.valueOf(total), "#1e293b"),
                statCard("Planeadas", planeadas + " / " + total, "#1d4ed8"),
                statCard("Sumários enviados", String.valueOf(enviadas), "#15803d"));

        grid.setItems(linhas);
    }

    private String estadoDe(SumarioAula s) {
        if (s == null) return "Por planear";
        if (s.isEnviado()) return "Sumário enviado";
        return s.temConteudo() ? "Planeada" : "Por planear";
    }

    private Span badgeEstado(String estado) {
        String[] cfg = switch (estado) {
            case "Sumário enviado" -> new String[] { "#dcfce7", "#15803d", "✅" };
            case "Planeada" -> new String[] { "#dbeafe", "#1d4ed8", "📝" };
            default -> new String[] { "#e2e8f0", "#475569", "⏳" };
        };
        Span b = new Span(cfg[2] + " " + estado);
        b.getStyle().set("background", cfg[0]).set("color", cfg[1]).set("padding", "3px 10px")
                .set("border-radius", "12px").set("font-size", "0.75rem").set("font-weight", "600")
                .set("white-space", "nowrap");
        return b;
    }

    private Component statCard(String label, String valor, String cor) {
        Div card = new Div();
        card.getStyle().set("background", "white").set("border-radius", "10px").set("padding", "10px 16px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.06)").set("min-width", "120px").set("text-align", "center");
        Span v = new Span(valor);
        v.getStyle().set("display", "block").set("font-size", "20px").set("font-weight", "800").set("color", cor);
        Span l = new Span(label);
        l.getStyle().set("font-size", "10px").set("color", "#95a5a6").set("font-weight", "700")
                .set("text-transform", "uppercase").set("letter-spacing", "0.4px");
        card.add(v, l);
        return card;
    }

    // ---------------- diálogo ----------------

    void abrirDialogPlaneamento(Linha l) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(l.turma().getDescricao());
        dialog.setWidth("94vw");
        dialog.setMaxWidth("520px");

        Span info = new Span(capitalizar(l.data().format(FMT_DIA_LONGO)) + "  ·  " + l.horaInicio()
                + (l.horaFim() != null ? "–" + l.horaFim() : "")
                + (l.sala() != null ? "  ·  " + l.sala().getNome() : ""));
        info.getStyle().set("font-size", "0.85rem").set("color", "#64748b").set("font-weight", "600");

        SumarioAula s = l.sumario();

        TextArea planeamento = new TextArea("Plano da aula");
        planeamento.setPlaceholder("O que vais dar nesta aula...");
        planeamento.setWidthFull();
        planeamento.setMinHeight("120px");
        if (s != null && s.getPlaneamento() != null) planeamento.setValue(s.getPlaneamento());

        TextArea sumario = new TextArea("Sumário (depois da aula)");
        sumario.setPlaceholder("Resumo do que foi dado — preencher após a aula...");
        sumario.setWidthFull();
        sumario.setMinHeight("120px");
        if (s != null && s.getSumario() != null) sumario.setValue(s.getSumario());

        VerticalLayout layout = new VerticalLayout(info, planeamento, sumario);
        layout.setPadding(false);
        layout.setSpacing(true);

        if (s != null && s.isEnviado() && s.getDataEnvio() != null) {
            Span env = new Span("✅ Sumário enviado à turma em " + DataUtil.formatar(s.getDataEnvio()));
            env.getStyle().set("color", "#15803d").set("font-weight", "600").set("font-size", "0.8rem");
            layout.add(env);
        }
        dialog.add(layout);

        Button guardar = new Button("Guardar", e -> guardar(l, planeamento.getValue(), sumario.getValue(), false, dialog));
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button enviar = new Button("Guardar e enviar sumário", VaadinIcon.PAPERPLANE.create(),
                e -> guardar(l, planeamento.getValue(), sumario.getValue(), true, dialog));
        enviar.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        enviar.setEnabled(sumario.getValue() != null && !sumario.getValue().isBlank());
        sumario.addValueChangeListener(e -> enviar.setEnabled(e.getValue() != null && !e.getValue().isBlank()));

        dialog.getFooter().add(new Button("Fechar", e -> dialog.close()), guardar, enviar);
        dialog.open();
    }

    private void guardar(Linha l, String plano, String sumarioTxt, boolean enviarAgora, Dialog dialog) {
        SumarioAula s = l.sumario() != null ? l.sumario() : new SumarioAula();
        s.setTurma(l.turma());
        s.setData(l.data());
        s.setHoraInicio(l.horaInicio());
        s.setHoraFim(l.horaFim());
        s.setTipo(l.tipo());
        s.setStudio(TenantContext.getCurrentStudio());
        if (s.getProfessor() == null && l.turma().getProfessor() != null) {
            s.setProfessor(l.turma().getProfessor().getNome());
        }
        s.setPlaneamento(plano);
        s.setSumario(sumarioTxt);

        if (enviarAgora) {
            if (sumarioTxt == null || sumarioTxt.isBlank()) {
                Notification.show("Escreve o sumário antes de enviar.")
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            List<String> emails = turmaService.getAlunosDaTurma(l.turma()).stream()
                    .map(Aluno::getEmail).filter(em -> em != null && !em.isBlank()).distinct()
                    .collect(Collectors.toList());
            if (emails.isEmpty()) {
                Notification.show("Nenhum aluno desta turma tem email registado.")
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            try {
                String assunto = "Sumário da aula — " + l.turma().getDescricao() + " ("
                        + DataUtil.formatar(l.data()) + ")";
                emailService.enviarEmailParaLista(TenantContext.getCurrentStudio(), l.turma().getProfessor(), emails, assunto, sumarioTxt);
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
            Notification.show("Guardado.").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }
        sumarioRepository.save(s);
        dialog.close();
        atualizar();
    }

    // ---------------- util ----------------

    private String normalizar(String t) {
        return t == null ? ""
                : Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private String capitalizar(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
