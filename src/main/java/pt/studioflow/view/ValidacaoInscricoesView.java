package pt.studioflow.view;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import org.springframework.transaction.annotation.Transactional;
import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.PresencaRepository;
import pt.studioflow.service.MensalidadeService;
import pt.studioflow.service.EmailService;
import pt.studioflow.util.DataUtil;

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
    private final MensalidadeConfig mensalidadeConfig;
    private final EmailService emailService;

    private final Grid<Aluno> grid = new Grid<>(Aluno.class, false);
    private final Span totalPendentesLabel = new Span("0");
    private final Span totalExperimentaisLabel = new Span("0");
    private final Span totalRenovacoesLabel = new Span("0");
    private final ComboBox<String> filtroTipoCombo = new ComboBox<>("Tipo");
    private List<Turma> turmasCache = List.of();

    private static final String FILTRO_TODAS = "Todas";
    private static final String FILTRO_INSCRICAO = "Novas Inscrições";
    private static final String FILTRO_EXPERIMENTAL = "Experimentais";
    private static final String FILTRO_RENOVACAO = "Renovações";

    public ValidacaoInscricoesView(AlunoRepository repository,
            TurmaRepository turmaRepository,
            AlunoTurmaRepository alunoTurmaRepository,
            MensalidadeRepository mensalidadeRepository,
            PresencaRepository presencaRepository,
            MensalidadeService mensalidadeService,
            MensalidadeConfig mensalidadeConfig,
            EmailService emailService) {
        this.repository = repository;
        this.turmaRepository = turmaRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.mensalidadeRepository = mensalidadeRepository;
        this.presencaRepository = presencaRepository;
        this.mensalidadeService = mensalidadeService;
        this.mensalidadeConfig = mensalidadeConfig;
        this.emailService = emailService;

        setSizeFull();
        setPadding(true);
        setSpacing(false);

        injectStyles();
        H2 titulo = new H2("Validação de Candidaturas");
        titulo.getStyle().set("margin-top", "0");
        add(titulo, criarStatsCards(), criarFiltroTipo(), grid);

        configurarGrid();
        atualizarGrid();
    }

    private void injectStyles() {
        String styles = ".stat-card { background: white; padding: 20px; border-radius: 15px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); border: 1px solid #eee; min-width: 250px; }"
                + ".stat-title { color: #7f8c8d; font-size: 0.75rem; text-transform: uppercase; letter-spacing: 1px; font-weight: 600; }"
                + ".stat-value { font-size: 1.6rem; font-weight: 800; display: block; margin-top: 5px; }"
                + ".experimental-row { background-color: #fff9f0 !important; font-weight: 500; }"
                + ".renewal-row { background-color: #f5f0fa !important; font-weight: 500; }";

        UI.getCurrent().getElement().executeJs(
                "document.head.appendChild(Object.assign(document.createElement('style'), {textContent: $0}));",
                styles);
    }

    private Component criarFiltroTipo() {
        filtroTipoCombo.setItems(FILTRO_TODAS, FILTRO_INSCRICAO, FILTRO_EXPERIMENTAL, FILTRO_RENOVACAO);
        filtroTipoCombo.setValue(FILTRO_TODAS);
        filtroTipoCombo.setAllowCustomValue(false);
        filtroTipoCombo.setWidth("240px");
        filtroTipoCombo.addValueChangeListener(e -> atualizarGrid());

        HorizontalLayout layout = new HorizontalLayout(filtroTipoCombo);
        layout.setAlignItems(Alignment.END);
        layout.getStyle().set("margin-bottom", "10px");
        return layout;
    }

    private Component criarStatsCards() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.getStyle().set("margin", "15px 0");
        layout.add(
                criarCard("Novas Inscrições", totalPendentesLabel, "#1967D2", VaadinIcon.USER_CHECK),
                criarCard("Aulas Experimentais", totalExperimentaisLabel, "#FF8C00", VaadinIcon.MAGIC),
                criarCard("Renovações", totalRenovacoesLabel, "#8E44AD", VaadinIcon.REFRESH));
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

    // Badge sólido com ícone e cor por estado - mais legível do que os temas de badge
    // por defeito do Vaadin
    private Component criarBadge(String label, VaadinIcon icone, String cor) {
        Icon i = icone.create();
        i.setSize("13px");
        i.getStyle().set("color", "#ffffff");

        Span texto = new Span(label);
        texto.getStyle().set("color", "#ffffff").set("font-size", "12px").set("font-weight", "700")
                .set("letter-spacing", "0.3px");

        HorizontalLayout badge = new HorizontalLayout(i, texto);
        badge.setAlignItems(Alignment.CENTER);
        badge.setSpacing(false);
        badge.getStyle().set("gap", "5px").set("background", cor).set("padding", "4px 10px")
                .set("border-radius", "12px").set("width", "fit-content");
        return badge;
    }

    private Component criarBadgeTipo(Aluno aluno) {
        return aluno.getStatus() == AlunoStatus.EXPERIMENTAL
                ? criarBadge("EXPERIMENTAL", VaadinIcon.STAR, "#F5A623")
                : criarBadge("INSCRIÇÃO", VaadinIcon.PLUS_CIRCLE, "#2D9CDB");
    }

    private Component criarBadgePedido(Aluno aluno) {
        return aluno.isPedidoRenovacao()
                ? criarBadge("RENOVAÇÃO", VaadinIcon.REFRESH, "#8E44AD")
                : criarBadge("NOVA", VaadinIcon.PLUS, "#27AE60");
    }

    // Célula "INTERESSES / TURMA": mostra as turmas como pills coloridos (cor da própria
    // turma, contraste de texto automático), tal como na grelha de Alunos. Como na validação
    // nada está ainda vinculado, todos ficam a contorno tracejado (pendente).
    private Component criarCelulaTurmas(Aluno aluno) {
        List<AlunoTurma> turmas = aluno.getTurmas();
        if (turmas != null && !turmas.isEmpty()) {
            HorizontalLayout badges = new HorizontalLayout();
            badges.setSpacing(false);
            badges.getStyle().set("gap", "4px").set("flex-wrap", "wrap");
            turmas.forEach(at -> badges.add(criarBadgeTurma(at.getTurma(), true)));
            return badges;
        }
        String morada = aluno.getMorada();
        if (morada != null && morada.contains("[")) {
            String interesses = morada.substring(morada.indexOf("[") + 1, morada.indexOf("]"));
            return criarBadgesInteresses(interesses);
        }
        return new Span("Não definida");
    }

    // Para novas inscrições/renovações as turmas de interesse ainda não estão vinculadas
    // (é só texto livre gravado na morada) - tenta casar cada interesse com a turma real
    // para mostrar o mesmo pill colorido por modalidade usado nas restantes colunas.
    private Component criarBadgesInteresses(String interessesTexto) {
        String texto = interessesTexto.replaceFirst("(?i)^Interesses:\\s*", "");
        HorizontalLayout badges = new HorizontalLayout();
        badges.setSpacing(false);
        badges.getStyle().set("gap", "4px").set("flex-wrap", "wrap");
        for (String item : texto.split(",\\s*")) {
            String itemTrim = item.trim();
            if (itemTrim.isEmpty()) {
                continue;
            }
            Turma turma = casarInteresseComTurma(itemTrim);
            badges.add(turma != null ? criarBadgeTurma(turma, true) : new Span(itemTrim));
        }
        return badges.getComponentCount() > 0 ? badges : new Span(texto);
    }

    private Turma casarInteresseComTurma(String item) {
        return turmasCache.stream()
                .filter(t -> t.getModalidade() != null
                        && item.startsWith(t.getModalidade().getDescricao() + " " + t.getDescricao()))
                .findFirst().orElse(null);
    }

    private Span criarBadgeTurma(Turma turma, boolean pendente) {
        String cor = (turma.getCor() != null && !turma.getCor().isBlank()) ? turma.getCor() : "#9e9e9e";
        Span badge = new Span(turma.getDescricao() + (pendente ? " (pendente)" : ""));
        badge.getStyle().set("background", cor).set("color", corTexto(cor))
                .set("padding", "3px 10px").set("border-radius", "12px")
                .set("font-size", "0.75rem").set("font-weight", "600").set("white-space", "nowrap");
        if (pendente) {
            badge.getStyle().set("opacity", "0.75").set("border", "1px dashed rgba(0,0,0,0.4)");
        }
        return badge;
    }

    private String corTexto(String corFundo) {
        try {
            int r = Integer.parseInt(corFundo.substring(1, 3), 16);
            int g = Integer.parseInt(corFundo.substring(3, 5), 16);
            int b = Integer.parseInt(corFundo.substring(5, 7), 16);
            double luminancia = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
            return luminancia > 0.6 ? "#212121" : "#ffffff";
        } catch (Exception e) {
            return "#ffffff";
        }
    }

    private void configurarGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(Aluno::getNomeCompleto).setHeader("CANDIDATO").setSortable(true).setFlexGrow(1);

        grid.addComponentColumn(this::criarBadgeTipo).setHeader("TIPO").setAutoWidth(true);

        grid.addComponentColumn(this::criarBadgePedido).setHeader("PEDIDO").setAutoWidth(true);

        grid.addComponentColumn(aluno -> {
            if (!aluno.isDadosValidados()) {
                return new Span("");
            }
            Icon icone = VaadinIcon.CHECK_CIRCLE.create();
            icone.setColor("#1E8E3E");
            icone.setSize("18px");
            icone.getElement().setAttribute("title", "Dados já revistos pela secretaria");
            return icone;
        }).setHeader("REVISTO").setAutoWidth(true);

        grid.addColumn(aluno -> DataUtil.formatar(aluno.getCarimboDataHora()))
                .setHeader("DATA DO PEDIDO").setSortable(true).setAutoWidth(true)
                .setComparator(Comparator.comparing(Aluno::getCarimboDataHora,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        grid.addComponentColumn(this::criarCelulaTurmas).setHeader("INTERESSES / TURMA").setAutoWidth(true)
                .setFlexGrow(1);

        grid.addComponentColumn(aluno -> {
            Button btnVer = new Button("Rever Dados", VaadinIcon.EYE.create());
            btnVer.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            btnVer.addClickListener(e -> abrirDialogDetalhes(aluno));
            return btnVer;
        }).setHeader("ACÇÕES").setAutoWidth(true);

        grid.setPartNameGenerator(aluno -> {
            if (aluno.getStatus() == AlunoStatus.EXPERIMENTAL) {
                return "experimental-row";
            }
            return aluno.isPedidoRenovacao() ? "renewal-row" : null;
        });
    }

    private void abrirDialogDetalhes(Aluno alunoSimplificado) {
        Aluno aluno = repository.findByIdWithTurmas(alunoSimplificado.getId()).orElse(alunoSimplificado);
        boolean isRenovacao = aluno.isPedidoRenovacao();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Ficha de Inscrição");
        dialog.setWidth("600px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        if (aluno.getStatus() == AlunoStatus.EXPERIMENTAL) {
            content.add(criarAviso(VaadinIcon.INFO_CIRCLE, " Candidato solicitou Aula Experimental",
                    "#FFF4E5", "#663C00"));
        }
        if (isRenovacao) {
            content.add(criarAviso(VaadinIcon.REFRESH, " Pedido de Renovação de Matrícula",
                    "#F3E8FF", "#4A148C"));
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
            HorizontalLayout badges = new HorizontalLayout();
            badges.setSpacing(true);
            aluno.getTurmas().forEach(at -> badges.add(criarBadgeTurma(at.getTurma(), true)));
            content.add(badges);
        } else if (moradaFull.contains("[")) {
            content.add(new Hr(), new H3("Preferências de Aula"));
            String interesses = moradaFull.substring(moradaFull.indexOf("[") + 1, moradaFull.indexOf("]"));
            content.add(criarBadgesInteresses(interesses));
        }

        content.add(new Hr(), criarResumoFinanceiro(aluno));

        if (aluno.isDadosValidados()) {
            content.add(criarAviso(VaadinIcon.CHECK_CIRCLE, " Dados já revistos pela secretaria",
                    "#E6F4EA", "#1E8E3E"));
        }

        Button btnAtivar = new Button("Ativar", VaadinIcon.USER_CARD.create(), e -> {
            processarAceitacao(aluno);
            dialog.close();
            UI.getCurrent().navigate("alunos", new QueryParameters(
                    Collections.singletonMap("edit", Collections.singletonList(aluno.getId().toString()))));
        });
        btnAtivar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnAtivar.getStyle().set("background-color", "#673ab7");

        // Abre a ficha completa do aluno só para consulta/edição - não ativa nem altera o
        // estado do pedido
        Button btnVerDados = new Button("Ver Dados", VaadinIcon.EDIT.create(), e -> {
            dialog.close();
            UI.getCurrent().navigate("alunos", new QueryParameters(
                    Collections.singletonMap("edit", Collections.singletonList(aluno.getId().toString()))));
        });
        btnVerDados.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // Ação leve: só confirma que a ficha foi revista pela secretaria, sem ativar o aluno
        // nem gerar mensalidades - essa parte fica reservada ao botão "Ativar".
        Button btnValidarDados = new Button(aluno.isDadosValidados() ? "Dados Validados" : "Validar Dados",
                VaadinIcon.CHECK_CIRCLE.create(), e -> {
                    aluno.setDadosValidados(true);
                    repository.save(aluno);
                    atualizarGrid();
                    Notification.show("Dados de " + aluno.getNomeCompleto() + " validados.")
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    dialog.close();
                });
        btnValidarDados.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        btnValidarDados.setEnabled(!aluno.isDadosValidados());

        Button btnRejeitar = new Button("Apagar Registo", VaadinIcon.TRASH.create(), e -> {
            removerDadosEAluno(aluno);
            atualizarGrid();
            Notification.show("Inscrição removida.");
            dialog.close();
        });
        btnRejeitar.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(btnRejeitar, new Button("Cancelar", i -> dialog.close()), btnVerDados,
                btnValidarDados, btnAtivar);
        dialog.add(content);
        dialog.open();
    }

    private Div criarAviso(VaadinIcon icone, String texto, String fundo, String cor) {
        Div aviso = new Div(new Icon(icone), new Span(texto));
        aviso.getStyle().set("background", fundo).set("color", cor).set("padding", "12px")
                .set("border-radius", "8px").set("width", "100%").set("margin-top", "0.4em");
        return aviso;
    }

    // Secção "Valores a Faturar": taxa de inscrição/renovação e mensalidades estimadas por
    // turma (com o desconto de +modalidades já aplicado) - dá à secretaria visibilidade do
    // que vai ser faturado antes de ativar o pedido. Espelha o resumo mostrado ao candidato
    // no formulário público (InscricaoPublicaView).
    private Component criarResumoFinanceiro(Aluno aluno) {
        VerticalLayout box = new VerticalLayout();
        box.setPadding(true);
        box.setSpacing(false);
        box.setWidthFull();
        box.getStyle().set("background", "#f8f9fa").set("border", "1px solid #e9ecef")
                .set("border-radius", "12px").set("margin-top", "0.5em");

        H3 titulo = new H3("Valores a Faturar");
        titulo.getStyle().set("margin", "0 0 8px 0");
        box.add(titulo);

        Studio studio = TenantContext.getCurrentStudio();
        List<LinhaMensalidade> linhas = obterLinhasParaResumoMensalidade(aluno);

        if (studio == null || linhas.isEmpty()) {
            Span nota = new Span(studio == null
                    ? "Sem estúdio ativo na sessão — valores indisponíveis."
                    : "As mensalidades serão calculadas após a associação às turmas.");
            nota.getStyle().set("font-size", "0.85em").set("color", "#6c757d").set("display", "block");
            box.add(nota);
            return box;
        }

        boolean socio = aluno.isSocio();
        boolean renovacao = aluno.isPedidoRenovacao();
        int idade = aluno.getDataNascimento() != null
                ? Period.between(aluno.getDataNascimento(), LocalDate.now()).getYears()
                : 0;
        String tipo = idade > 0 && idade < 18 ? "crianca" : "adulto";

        double base = 0;
        List<Span> linhasMensalidades = new ArrayList<>();
        for (LinhaMensalidade linha : linhas) {
            Turma t = linha.turma();
            double valor = mensalidadeConfig.calcularMensalidade(studio, tipo, linha.aulasPorSemana(), socio);
            base += valor;
            String modalidade = t.getModalidade() != null ? t.getModalidade().getDescricao() + " - " : "";
            Span linhaSpan = new Span(modalidade + t.getDescricao() + ": " + formatarEuro(valor) + "/mês");
            linhaSpan.getStyle().set("font-size", "0.9em").set("display", "block");
            linhasMensalidades.add(linhaSpan);
        }

        long numModalidades = linhas.stream()
                .map(l -> l.turma().getModalidade())
                .filter(m -> m != null)
                .map(m -> m.getId())
                .distinct().count();

        MensalidadeConfig.ResumoInscricao resumo = mensalidadeConfig.calcularResumo(
                studio, base, renovacao, false, (int) numModalidades);

        Span subtitulo = new Span("Mensalidades estimadas:");
        subtitulo.getStyle().set("font-weight", "600").set("display", "block").set("margin-top", "6px");
        box.add(subtitulo);
        linhasMensalidades.forEach(box::add);

        box.add(criarLinhaValor(renovacao ? "Taxa de renovação" : "Taxa de inscrição", resumo.taxa(), false));
        if (resumo.descontoMultiModalidade() > 0) {
            box.add(criarLinhaValor("Desconto +modalidades", -resumo.descontoMultiModalidade(), true));
        }

        Span totalMensal = new Span("Total mensal: "
                + formatarEuro(base - resumo.descontoMultiModalidade()) + "/mês");
        totalMensal.getStyle().set("font-weight", "700").set("display", "block").set("margin-top", "8px");
        box.add(totalMensal);

        Span totalInicial = new Span("Total 1º pagamento: " + formatarEuro(resumo.total()));
        totalInicial.getStyle().set("font-weight", "700").set("display", "block").set("margin-top", "2px");
        box.add(totalInicial);

        return box;
    }

    private Component criarLinhaValor(String label, double valor, boolean destacarDesconto) {
        Span nome = new Span(label + ": ");
        Span val = new Span(formatarEuro(valor));
        val.getStyle().set("font-weight", "600");
        if (destacarDesconto) {
            val.getStyle().set("color", "#27ae60");
        }
        Div linha = new Div(nome, val);
        linha.getStyle().set("font-size", "0.9em").set("margin-top", "4px");
        return linha;
    }

    private record LinhaMensalidade(Turma turma, int aulasPorSemana) {
    }

    // Turmas + frequência a considerar para a estimativa: usa as associações AlunoTurma já
    // existentes quando há; caso contrário, casa o texto livre de interesses (morada) com as
    // turmas reais, à semelhança de criarBadgesInteresses.
    private List<LinhaMensalidade> obterLinhasParaResumoMensalidade(Aluno aluno) {
        if (aluno.getTurmas() != null && !aluno.getTurmas().isEmpty()) {
            Map<Long, Turma> turmasCompletas = turmasCache.stream()
                    .collect(Collectors.toMap(Turma::getId, t -> t, (a, b) -> a));
            return aluno.getTurmas().stream()
                    .map(at -> new LinhaMensalidade(
                            turmasCompletas.getOrDefault(at.getTurma().getId(), at.getTurma()),
                            at.getAulasPorSemana()))
                    .toList();
        }

        String morada = aluno.getMorada();
        if (morada == null || !morada.contains("[")) {
            return List.of();
        }
        String interesses = morada.substring(morada.indexOf("[") + 1, morada.indexOf("]"))
                .replaceFirst("(?i)^Interesses:\\s*", "");

        List<LinhaMensalidade> linhas = new ArrayList<>();
        for (String item : interesses.split(",\\s*")) {
            String itemTrim = item.trim();
            if (itemTrim.isEmpty()) {
                continue;
            }
            Turma turma = casarInteresseComTurma(itemTrim);
            if (turma != null) {
                int aulasPorSemana = itemTrim.contains("2 vez") ? 2 : 1;
                linhas.add(new LinhaMensalidade(turma, aulasPorSemana));
            }
        }
        return linhas;
    }

    private String formatarEuro(double valor) {
        return String.format("%.2f €", valor);
    }

    private TextField createField(String label, Object value) {
        TextField tf = new TextField(label);
        String texto;
        if (value == null) {
            texto = "N/D";
        } else if (value instanceof LocalDate data) {
            texto = DataUtil.formatar(data);
        } else {
            texto = String.valueOf(value);
        }
        tf.setValue(texto);
        tf.setReadOnly(true);
        tf.addThemeVariants(com.vaadin.flow.component.textfield.TextFieldVariant.LUMO_SMALL);
        return tf;
    }

    private void processarAceitacao(Aluno aluno) {
        boolean isRenovacao = aluno.isPedidoRenovacao();
        String notasComInteresses = aluno.getMorada();
        if (aluno.getMorada() != null && aluno.getMorada().contains("[")) {
            aluno.setMorada(aluno.getMorada().substring(0, aluno.getMorada().indexOf("[")).trim());
        }
        if (aluno.getDataNascimento() != null) {
            int idade = Period.between(aluno.getDataNascimento(), LocalDate.now()).getYears();
            aluno.setCrianca(idade < 18);
        }

        aluno.setStatus(AlunoStatus.ATIVO);
        aluno.setAtivo(true);
        aluno.setPedidoRenovacao(false);
        if (isRenovacao) {
            aluno.setDataInscricaoRenovacao(LocalDate.now());
        }
        repository.save(aluno);

        vincularTurmasEFinanceiro(aluno, notasComInteresses, isRenovacao);

        if (isRenovacao) {
            try {
                emailService.enviarEmailAprovacaoRenovacao(aluno);
            } catch (Exception e) {
                System.err.println("Erro ao notificar o aluno " + aluno.getNomeCompleto()
                        + " da renovação: " + e.getMessage());
            }
        }

        atualizarGrid();
        Notification.show((isRenovacao ? "Renovação de " : "Aluno ") + aluno.getNomeCompleto()
                + (isRenovacao ? " confirmada!" : " ativado!"))
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void vincularTurmasEFinanceiro(Aluno aluno, String notas, boolean renovacao) {
        // turmasCache vem de findAllByStudio/findAllComplete (inclui "professor") - resolve
        // cada at.getTurma() (proxy lazy de findByIdWithTurmas, cujo EntityGraph só cobre
        // turmas/turmas.turma) para a instância completa antes de notificar o professor,
        // senão turma.getProfessor().getEmail() lança LazyInitializationException dentro do
        // try/catch de notificarProfessor e o email nunca sai.
        Map<Long, Turma> turmasCompletas = turmasCache.stream()
                .collect(Collectors.toMap(Turma::getId, t -> t, (a, b) -> a));

        // Cenário A: o aluno já tem turmas associadas.
        if (aluno.getTurmas() != null && !aluno.getTurmas().isEmpty()) {
            aluno.getTurmas().forEach(at -> {
                mensalidadeService.gerarMensalidadesParaAluno(aluno, at.getTurma());
                Turma completa = turmasCompletas.getOrDefault(at.getTurma().getId(), at.getTurma());
                notificarProfessor(completa, aluno, renovacao);
            });
        }

        if (notas == null)
            return;

        // Cenário B: Associação via varredura do texto de interesses recolhido na morada.
        Studio studio = TenantContext.getCurrentStudio();
        List<Turma> todasTurmas = studio != null ? turmaRepository.findAllByStudio(studio) : turmaRepository.findAll();
        for (Turma t : todasTurmas) {
            if (notas.contains(t.getDescricao())) {
                if (!alunoTurmaRepository.existsByAlunoAndTurma(aluno, t)) {
                    AlunoTurma at = new AlunoTurma();
                    at.setAluno(aluno);
                    at.setTurma(t);
                    at.setAulasPorSemana(notas.contains(t.getDescricao() + " (2 vez") ? 2 : 1);
                    alunoTurmaRepository.save(at);
                    mensalidadeService.gerarMensalidadesParaAluno(aluno, t);

                    notificarProfessor(t, aluno, renovacao);
                }
            }
        }
    }

    private void notificarProfessor(Turma turma, Aluno aluno, boolean renovacao) {
        try {
            if (turma.getProfessor() != null && turma.getProfessor().getEmail() != null
                    && !turma.getProfessor().getEmail().isBlank()) {
                String emailDestinatario = turma.getProfessor().getEmail();
                String nomeProfessor = turma.getProfessor().getNome() != null
                        ? turma.getProfessor().getNome()
                        : "Professor";

                String assunto = renovacao
                        ? "Renovação de Aluno na sua Turma: " + turma.getDescricao()
                        : "Novo Aluno na sua Turma: " + turma.getDescricao();
                String corpo = String.format(
                        "Olá %s,\n\n" +
                                (renovacao
                                        ? "Informa-se que o aluno \"%s\" renovou a matrícula na sua turma \"%s\" para o novo período.\n\n"
                                        : "Informa-se que o aluno \"%s\" foi integrado e ativado com sucesso na sua turma \"%s\".\n\n")
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
        Studio studio = TenantContext.getCurrentStudio();
        turmasCache = studio != null ? turmaRepository.findAllByStudio(studio) : turmaRepository.findAllComplete();

        List<Aluno> lista = repository.findByStatusWithTurmas(List.of(AlunoStatus.PENDENTE, AlunoStatus.EXPERIMENTAL));
        lista.sort(Comparator.comparing(Aluno::getNomeCompleto, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        long renovacoes = lista.stream().filter(Aluno::isPedidoRenovacao).count();
        long experimentais = lista.stream().filter(a -> a.getStatus() == AlunoStatus.EXPERIMENTAL).count();
        long novasInscricoes = lista.stream()
                .filter(a -> a.getStatus() == AlunoStatus.PENDENTE && !a.isPedidoRenovacao()).count();

        totalPendentesLabel.setText(String.valueOf(novasInscricoes));
        totalExperimentaisLabel.setText(String.valueOf(experimentais));
        totalRenovacoesLabel.setText(String.valueOf(renovacoes));

        String filtro = filtroTipoCombo.getValue();
        List<Aluno> listaFiltrada = switch (filtro == null ? FILTRO_TODAS : filtro) {
            case FILTRO_INSCRICAO -> lista.stream()
                    .filter(a -> a.getStatus() == AlunoStatus.PENDENTE && !a.isPedidoRenovacao()).toList();
            case FILTRO_EXPERIMENTAL -> lista.stream()
                    .filter(a -> a.getStatus() == AlunoStatus.EXPERIMENTAL).toList();
            case FILTRO_RENOVACAO -> lista.stream().filter(Aluno::isPedidoRenovacao).toList();
            default -> lista;
        };
        grid.setItems(listaFiltrada);
    }
}
