package pt.studioflow.view;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import org.springframework.transaction.annotation.Transactional;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.PresencaRepository;
import pt.studioflow.service.MensalidadeService;
import pt.studioflow.service.EmailService; // Importação do EmailService

@PageTitle("Validação de Inscrições | CoreoFlow")
@Route(value = "validar-inscricoes", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class ValidacaoInscricoesView extends VerticalLayout {

    private final AlunoRepository repository;
    private final TurmaRepository turmaRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final MensalidadeRepository mensalidadeRepository;
    private final PresencaRepository presencaRepository;
    private final MensalidadeService mensalidadeService;
    private final EmailService emailService; // Campo adicionado para o serviço de e-mail

    private final Grid<Aluno> grid = new Grid<>(Aluno.class, false);
    private final Span totalPendentesLabel = new Span("0");
    private final Span totalExperimentaisLabel = new Span("0");

    public ValidacaoInscricoesView(AlunoRepository repository,
            TurmaRepository turmaRepository,
            AlunoTurmaRepository alunoTurmaRepository,
            MensalidadeRepository mensalidadeRepository,
            PresencaRepository presencaRepository,
            MensalidadeService mensalidadeService,
            EmailService emailService) { // Injeção adicionada no construtor
        this.repository = repository;
        this.turmaRepository = turmaRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.mensalidadeRepository = mensalidadeRepository;
        this.presencaRepository = presencaRepository;
        this.mensalidadeService = mensalidadeService;
        this.emailService = emailService;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        injectStyles();
        H2 titulo = new H2("Validação de Candidaturas");
        titulo.getStyle().set("margin-top", "0");
        add(titulo, criarStatsCards(), grid);

        configurarGrid();
        atualizarGrid();
    }

    private void injectStyles() {
        String styles = ".stat-card { background: white; padding: 20px; border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); border: 1px solid #eee; min-width: 250px; }"
                + ".stat-title { color: #7f8c8d; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 1px; font-weight: 600; }"
                + ".stat-value { font-size: 1.6rem; font-weight: 800; display: block; margin-top: 5px; }"
                + ".experimental-row { background-color: #fff9f0 !important; font-weight: 500; }";

        UI.getCurrent().getElement().executeJs(
                "document.head.appendChild(Object.assign(document.createElement('style'), {textContent: $0}));",
                styles);
    }

    private Component criarStatsCards() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.getStyle().set("margin", "15px 0");
        layout.add(
                criarCard("Novas Inscrições", totalPendentesLabel, "#1967D2", VaadinIcon.USER_CHECK),
                criarCard("Aulas Experimentais", totalExperimentaisLabel, "#FF8C00", VaadinIcon.MAGIC));
        return layout;
    }

    private Div criarCard(String titulo, Span val, String color, VaadinIcon icon) {
        Div card = new Div();
        card.addClassName("stat-card");
        Icon i = icon.create();
        i.setColor(color);
        i.getStyle().set("float", "right");
        Span t = new Span(titulo);
        t.addClassName("stat-title");
        val.addClassName("stat-value");
        val.getStyle().set("color", color);
        card.add(i, t, val);
        return card;
    }

    private void configurarGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(Aluno::getNomeCompleto).setHeader("CANDIDATO").setSortable(true).setFlexGrow(1);

        grid.addComponentColumn(aluno -> {
            Span badge = new Span(aluno.getStatus().toString());
            boolean isExp = aluno.getStatus() == AlunoStatus.EXPERIMENTAL;
            badge.getElement().getThemeList().add("badge pill " + (isExp ? "warning" : "success"));
            return badge;
        }).setHeader("TIPO").setAutoWidth(true);

        grid.addColumn(aluno -> {
            if (aluno.getTurmas() != null && !aluno.getTurmas().isEmpty()) {
                return aluno.getTurmas().stream()
                        .map(at -> at.getTurma().getDescricao())
                        .collect(Collectors.joining(", "));
            }
            String morada = aluno.getMorada();
            if (morada != null && morada.contains("[")) {
                return morada.substring(morada.indexOf("[") + 1, morada.indexOf("]"));
            }
            return "Não definida";
        }).setHeader("INTERESSES / TURMA").setAutoWidth(true);

        grid.addComponentColumn(aluno -> {
            Button btnVer = new Button("Rever Dados", VaadinIcon.EYE.create());
            btnVer.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            btnVer.addClickListener(e -> abrirDialogDetalhes(aluno));
            return btnVer;
        }).setHeader("ACÇÕES").setAutoWidth(true);

        grid.setPartNameGenerator(aluno -> aluno.getStatus() == AlunoStatus.EXPERIMENTAL ? "experimental-row" : null);
    }

    private void abrirDialogDetalhes(Aluno alunoSimplificado) {
        Aluno aluno = repository.findByIdWithTurmas(alunoSimplificado.getId()).orElse(alunoSimplificado);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Ficha de Inscrição");
        dialog.setWidth("600px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        if (aluno.getStatus() == AlunoStatus.EXPERIMENTAL) {
            Div aviso = new Div(new Icon(VaadinIcon.INFO_CIRCLE), new Span(" Candidato solicitou Aula Experimental"));
            aviso.getStyle().set("background", "#FFF4E5").set("color", "#663C00").set("padding", "12px")
                    .set("border-radius", "8px").set("width", "100%");
            content.add(aviso);
        }

        FormLayout form = new FormLayout();
        form.add(createField("E-mail", aluno.getEmail()), 2);
        form.add(createField("Telemóvel", aluno.getTelemovel()), 1);
        form.add(createField("Nascimento", aluno.getDataNascimento()), 1);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        String moradaFull = aluno.getMorada() != null ? aluno.getMorada() : "";
        String moradaLimpa = moradaFull.contains("[") ? moradaFull.substring(0, moradaFull.indexOf("[")).trim()
                : moradaFull;

        content.add(new H3("Dados de Contacto"), form, createField("Morada", moradaLimpa));

        if (aluno.getTurmas() != null && !aluno.getTurmas().isEmpty()) {
            content.add(new Hr(), new H3("Turma Vinculada"));
            content.add(new Span(aluno.getTurmas().stream().map(at -> at.getTurma().getDescricao())
                    .collect(Collectors.joining(", "))));
        } else if (moradaFull.contains("[")) {
            content.add(new Hr(), new H3("Preferências de Aula"));
            String interesses = moradaFull.substring(moradaFull.indexOf("[") + 1, moradaFull.indexOf("]"));
            content.add(new Span(interesses));
        }

        Button btnValidarECompletar = new Button("Ativar e Completar Ficha", VaadinIcon.USER_CARD.create(), e -> {
            processarAceitacao(aluno);
            dialog.close();
            UI.getCurrent().navigate("alunos", new QueryParameters(
                    Collections.singletonMap("edit", Collections.singletonList(aluno.getId().toString()))));
        });
        btnValidarECompletar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnValidarECompletar.getStyle().set("background-color", "#673ab7");

        Button btnAceitar = new Button("Apenas Validar", VaadinIcon.CHECK_CIRCLE.create(), e -> {
            processarAceitacao(aluno);
            dialog.close();
        });
        btnAceitar.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        Button btnRejeitar = new Button("Apagar Registo", VaadinIcon.TRASH.create(), e -> {
            removerDadosEAluno(aluno);
            atualizarGrid();
            Notification.show("Inscrição removida.");
            dialog.close();
        });
        btnRejeitar.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(btnRejeitar, new Button("Cancelar", i -> dialog.close()), btnAceitar,
                btnValidarECompletar);
        dialog.add(content);
        dialog.open();
    }

    private TextField createField(String label, Object value) {
        TextField tf = new TextField(label);
        tf.setValue(value != null ? String.valueOf(value) : "N/D");
        tf.setReadOnly(true);
        tf.addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant.LUMO_SMALL);
        return tf;
    }

    private void processarAceitacao(Aluno aluno) {
        String notasComInteresses = aluno.getMorada();
        if (aluno.getMorada() != null && aluno.getMorada().contains("[")) {
            aluno.setMorada(aluno.getMorada().substring(0, aluno.getMorada().indexOf("[")).trim());
        }
        if (aluno.getDataNascimento() != null) {
            int idade = java.time.Period.between(aluno.getDataNascimento(), LocalDate.now()).getYears();
            aluno.setCrianca(idade < 18);
        }

        aluno.setStatus(AlunoStatus.ATIVO);
        aluno.setAtivo(true);
        repository.save(aluno);

        vincularTurmasEFinanceiro(aluno, notasComInteresses);
        atualizarGrid();
        Notification.show("Aluno " + aluno.getNomeCompleto() + " ativado!")
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void vincularTurmasEFinanceiro(Aluno aluno, String notas) {
        // Cenário A: O aluno já veio com a associação explícita direta do formulário
        if (aluno.getTurmas() != null && !aluno.getTurmas().isEmpty()) {
            aluno.getTurmas().forEach(at -> {
                mensalidadeService.gerarMensalidadesParaAluno(aluno, at.getTurma());
                notificarProfessor(at.getTurma(), aluno); // Dispara notificação por e-mail
            });
            return;
        }

        if (notas == null)
            return;

        // Cenário B: Associação via varredura do texto de interesses recolhido na
        // morada
        pt.studioflow.model.Studio _studioV = pt.studioflow.config.TenantContext.getCurrentStudio();
        List<Turma> todasTurmas = _studioV != null ? turmaRepository.findAllByStudio(_studioV) : turmaRepository.findAll();
        for (Turma t : todasTurmas) {
            if (notas.contains(t.getDescricao())) {
                if (!alunoTurmaRepository.existsByAlunoAndTurma(aluno, t)) {
                    AlunoTurma at = new AlunoTurma();
                    at.setAluno(aluno);
                    at.setTurma(t);
                    at.setAulasPorSemana(notas.contains(t.getDescricao() + " (2 vezes") ? 2 : 1);
                    alunoTurmaRepository.save(at);
                    mensalidadeService.gerarMensalidadesParaAluno(aluno, t);

                    notificarProfessor(t, aluno); // Dispara notificação por e-mail
                }
            }
        }
    }

    // Método auxiliar adicionado para enviar o e-mail ao docente responsável
    private void notificarProfessor(Turma turma, Aluno aluno) {
        try {
            // Valida se a turma possui um professor atribuído com um e-mail válido
            // configurado
            if (turma.getProfessor() != null && turma.getProfessor().getEmail() != null
                    && !turma.getProfessor().getEmail().isBlank()) {
                String emailDestinatario = turma.getProfessor().getEmail();
                String nomeProfessor = turma.getProfessor().getNome() != null
                        ? turma.getProfessor().getNome()
                        : "Professor";

                String assunto = "Novo Aluno na sua Turma: " + turma.getDescricao();
                String corpo = String.format(
                        "Olá %s,\n\n" +
                                "Informa-se que o aluno \"%s\" foi integrado e ativado com sucesso na sua turma \"%s\".\n\n"
                                +
                                "Já poderá consultá-lo na lista de presenças da turma!\n\n"
                                +
                                "Mensagem gerada automaticamente pelo plataforma CoreoFlow.",
                        nomeProfessor,
                        aluno.getNomeCompleto(),
                        turma.getDescricao());

                emailService.enviarEmailNotificacaoProfessor(emailDestinatario, aluno, assunto, corpo);
            }
        } catch (Exception e) {
            System.err
                    .println("Erro ao notificar o professor da turma " + turma.getDescricao() + ": " + e.getMessage());
        }
    }

    @Transactional
    public void removerDadosEAluno(Aluno alunoSimplificado) {
        Long id = alunoSimplificado.getId();

        presencaRepository.deleteByAluno(alunoSimplificado);
        mensalidadeRepository.deleteByAluno(alunoSimplificado);
        alunoTurmaRepository.deleteByAlunoId(id);

        repository.findById(id).ifPresent(managedAluno -> {
            repository.delete(managedAluno);
        });

        repository.flush();
    }

    private void atualizarGrid() {
        List<Aluno> lista = repository.findByStatusWithTurmas(List.of(AlunoStatus.PENDENTE, AlunoStatus.EXPERIMENTAL));
        grid.setItems(lista);

        long pendentes = lista.stream().filter(a -> a.getStatus() == AlunoStatus.PENDENTE).count();
        long experimentais = lista.stream().filter(a -> a.getStatus() == AlunoStatus.EXPERIMENTAL).count();
        totalPendentesLabel.setText(String.valueOf(pendentes));
        totalExperimentaisLabel.setText(String.valueOf(experimentais));
    }
}
