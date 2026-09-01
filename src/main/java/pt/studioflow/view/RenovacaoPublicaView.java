package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
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
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Idioma;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.RenovacaoService;
import pt.studioflow.service.TranslationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fluxo público de renovação de matrícula: o encarregado de educação procura
 * o(s) educando(s) pelo email, escolhe as turmas para o novo período e submete
 * o pedido, que fica PENDENTE até ser validado em ValidacaoInscricoesView
 * (mesmo fluxo de aprovação das novas inscrições, ver InscricaoPublicaView).
 */
@Route("renovacao")
@RouteAlias("renew")
@AnonymousAllowed
public class RenovacaoPublicaView extends VerticalLayout implements BeforeEnterObserver {

    private final RenovacaoService renovacaoService;
    private final TurmaRepository turmaRepository;
    private final StudioRepository studioRepository;
    private final MensalidadeConfig mensalidadeConfig;
    private final TranslationService translationService;

    private final VerticalLayout card = new VerticalLayout();

    private Studio studioAtual = null;
    private boolean temFamiliarInscrito = false;
    private Idioma idiomaAtual = Idioma.PT;

    private final Map<Long, Select<String>> mapaFrequencias = new HashMap<>();
    private final VerticalLayout containerFrequencias = new VerticalLayout();
    private final Div resumoPagamento = new Div();

    public RenovacaoPublicaView(RenovacaoService renovacaoService, TurmaRepository turmaRepository,
            StudioRepository studioRepository, MensalidadeConfig mensalidadeConfig,
            TranslationService translationService) {
        this.renovacaoService = renovacaoService;
        this.turmaRepository = turmaRepository;
        this.studioRepository = studioRepository;
        this.mensalidadeConfig = mensalidadeConfig;
        this.translationService = translationService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String slug = event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("studio", List.of()).stream()
                .findFirst().orElse(null);

        if (slug != null && !slug.isBlank()) {
            studioAtual = studioRepository.findBySlugAndAtivoTrue(slug).orElse(null);
        }

        if (studioAtual == null) {
            removeAll();
            add(new H2("Estúdio não encontrado."),
                    new Span("O link de renovação é inválido ou o estúdio já não está ativo."));
            return;
        }

        construirLayoutBase();
        mostrarPassoEmail();
    }

    private void construirLayoutBase() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setPadding(true);
        getStyle().set("background-color", "#f8f9fa");

        String logoSrc = (studioAtual.getLogoPath() != null && !studioAtual.getLogoPath().isBlank())
                ? pt.studioflow.util.LogoUrl.comVersao(studioAtual.getLogoPath())
                : "images/logo-coreoflow.png";
        Image logo = new Image(logoSrc, studioAtual.getNome());
        logo.setWidth("min(180px, 40vw)");

        H2 titulo = new H2(translationService.t("renovacao.titulo", idiomaAtual));
        Span subtitulo = new Span(translationService.t("renovacao.subtitulo", idiomaAtual));

        VerticalLayout header = new VerticalLayout(logo, titulo, subtitulo);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(false);

        List<Idioma> idiomasStudio = studioAtual.getIdiomasDisponiveisList();
        if (idiomasStudio.size() > 1) {
            Select<Idioma> seletorIdioma = new Select<>();
            seletorIdioma.setItems(idiomasStudio);
            seletorIdioma.setValue(Idioma.PT);
            seletorIdioma.setItemLabelGenerator(Idioma::getLabel);
            seletorIdioma.getStyle().set("margin-top", "8px");
            seletorIdioma.addValueChangeListener(ev -> {
                idiomaAtual = ev.getValue();
                titulo.setText(translationService.t("renovacao.titulo", idiomaAtual));
                subtitulo.setText(translationService.t("renovacao.subtitulo", idiomaAtual));
            });
            header.add(seletorIdioma);
        }

        card.removeAll();
        card.add(header);
        card.setMaxWidth("650px");
        card.setWidth("100%");
        card.getStyle()
                .set("background", "white")
                .set("padding", "min(2.5em, 5vw)")
                .set("border-radius", "15px")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.1)");

        add(card);
    }

    // --- PASSO 1: procurar por email ---

    private void mostrarPassoEmail() {
        removerConteudoAposHeader();

        EmailField email = new EmailField(translationService.t("renovacao.email", idiomaAtual));
        email.setWidthFull();

        Button btnProcurar = new Button(translationService.t("renovacao.procurar", idiomaAtual), VaadinIcon.SEARCH.create());
        btnProcurar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnProcurar.setWidthFull();
        btnProcurar.addClickListener(e -> {
            if (email.isEmpty() || email.isInvalid()) {
                Notification.show("Indica um email válido.").addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            processarPesquisa(email.getValue());
        });

        card.add(email, btnProcurar);
    }

    private void processarPesquisa(String emailPesquisado) {
        List<Aluno> encontrados = renovacaoService.procurarPorEmail(emailPesquisado, studioAtual);

        if (encontrados.isEmpty()) {
            Notification.show("Não encontrámos nenhum educando com este email. Contacta o estúdio.", 5000,
                    Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        List<Aluno> elegiveis = encontrados.stream().filter(renovacaoService::elegivel).toList();
        if (elegiveis.isEmpty()) {
            Notification.show("Já existe um pedido em curso para este email, ou não há educandos elegíveis para renovação. Contacta o estúdio.",
                    6000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        temFamiliarInscrito = encontrados.size() > 1;

        if (elegiveis.size() == 1) {
            mostrarPassoTurmas(elegiveis.get(0));
        } else {
            mostrarPassoEscolherEducando(elegiveis);
        }
    }

    // --- PASSO 1.5: escolher educando (irmãos com o mesmo email) ---

    private void mostrarPassoEscolherEducando(List<Aluno> elegiveis) {
        removerConteudoAposHeader();

        Span instrucao = new Span("Encontrámos mais do que um educando associado a este email. Escolhe quem vai renovar:");

        ComboBox<Aluno> escolha = new ComboBox<>("Educando");
        escolha.setItems(elegiveis);
        escolha.setItemLabelGenerator(Aluno::getNomeCompleto);
        escolha.setWidthFull();

        Button btnContinuar = new Button("Continuar", VaadinIcon.ARROW_RIGHT.create());
        btnContinuar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnContinuar.setWidthFull();
        btnContinuar.addClickListener(e -> {
            if (escolha.isEmpty()) {
                Notification.show("Escolhe um educando.").addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            mostrarPassoTurmas(escolha.getValue());
        });

        card.add(instrucao, escolha, btnContinuar);
    }

    // --- PASSO 2: escolher turmas + resumo + submeter ---

    private void mostrarPassoTurmas(Aluno aluno) {
        removerConteudoAposHeader();
        mapaFrequencias.clear();
        containerFrequencias.removeAll();
        containerFrequencias.setPadding(false);
        containerFrequencias.setSpacing(true);
        containerFrequencias.setWidthFull();

        Span saudacao = new Span("Olá, " + aluno.getNomeCompleto() + "! Escolhe as turmas para o novo período:");

        MultiSelectComboBox<Turma> turmas = new MultiSelectComboBox<>(translationService.t("renovacao.turmas", idiomaAtual));
        turmas.setItems(turmaRepository.findByStudioAndAtivoTrue(studioAtual));
        turmas.setItemLabelGenerator(t -> t.getModalidade().getDescricao() + " - " + t.getDescricao());
        turmas.setWidthFull();

        Set<Turma> turmasAtuais = aluno.getTurmas() != null
                ? aluno.getTurmas().stream().map(AlunoTurma::getTurma).collect(Collectors.toSet())
                : Set.of();
        Map<Long, Integer> frequenciaAtual = aluno.getTurmas() != null
                ? aluno.getTurmas().stream().collect(Collectors.toMap(
                        at -> at.getTurma().getId(), AlunoTurma::getAulasPorSemana, (a, b) -> a))
                : Map.of();

        resumoPagamento.removeAll();
        resumoPagamento.getStyle()
                .set("background", "#f8f9fa")
                .set("border-radius", "10px")
                .set("padding", "16px")
                .set("margin-top", "8px");

        turmas.addValueChangeListener(event -> {
            Set<Turma> atuaisSel = event.getValue();
            mapaFrequencias.keySet().removeIf(id -> {
                boolean removido = atuaisSel.stream().noneMatch(t -> t.getId().equals(id));
                if (removido) {
                    containerFrequencias.getChildren()
                            .filter(comp -> id.toString().equals(comp.getElement().getAttribute("data-id")))
                            .findFirst().ifPresent(containerFrequencias::remove);
                }
                return removido;
            });

            atuaisSel.forEach(t -> {
                if (!mapaFrequencias.containsKey(t.getId())) {
                    Select<String> freqSelect = new Select<>();
                    freqSelect.setItems("1 vez / semana", "2 vezes / semana");
                    int freqDefault = frequenciaAtual.getOrDefault(t.getId(), 2);
                    freqSelect.setValue(freqDefault == 1 ? "1 vez / semana" : "2 vezes / semana");
                    freqSelect.setLabel("Frequência em " + t.getDescricao());
                    freqSelect.setWidthFull();
                    freqSelect.addValueChangeListener(ev -> atualizarResumo(aluno, turmas.getValue()));

                    HorizontalLayout row = new HorizontalLayout(new Icon(VaadinIcon.ARROW_RIGHT), freqSelect);
                    row.setWidthFull();
                    row.setAlignItems(Alignment.END);
                    row.getElement().setAttribute("data-id", t.getId().toString());

                    containerFrequencias.add(row);
                    mapaFrequencias.put(t.getId(), freqSelect);
                }
            });

            atualizarResumo(aluno, atuaisSel);
        });

        turmas.setValue(turmasAtuais);

        Button btnSubmeter = new Button(translationService.t("renovacao.submeter", idiomaAtual));
        btnSubmeter.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        btnSubmeter.setWidthFull();
        btnSubmeter.setHeight("50px");
        btnSubmeter.addClickListener(e -> submeter(aluno, turmas.getValue()));

        card.add(saudacao, turmas, containerFrequencias, resumoPagamento, btnSubmeter);
    }

    private void atualizarResumo(Aluno aluno, Set<Turma> selecionadas) {
        resumoPagamento.removeAll();
        if (selecionadas.isEmpty()) return;

        double mensalidadeBaseTotal = selecionadas.stream()
                .mapToDouble(t -> {
                    Select<String> freqSelect = mapaFrequencias.get(t.getId());
                    int freq = (freqSelect != null && "1 vez / semana".equals(freqSelect.getValue())) ? 1 : 2;
                    return mensalidadeConfig.calcularMensalidade(studioAtual, aluno.isCrianca() ? "crianca" : "adulto", freq, aluno.isSocio());
                }).sum();

        long numModalidades = selecionadas.stream().map(Turma::getModalidade).map(Modalidade::getId).distinct().count();

        MensalidadeConfig.ResumoInscricao resumo = mensalidadeConfig.calcularResumo(
                studioAtual, mensalidadeBaseTotal, true, temFamiliarInscrito, (int) numModalidades);

        resumoPagamento.add(
                linhaResumo("Mensalidade estimada", resumo.mensalidadeBase(), false),
                linhaResumo("Taxa de renovação", resumo.taxa(), false),
                linhaResumo("Desconto familiar", -resumo.descontoFamiliar(), resumo.descontoFamiliar() > 0),
                linhaResumo("Desconto +modalidades", -resumo.descontoMultiModalidade(), resumo.descontoMultiModalidade() > 0),
                linhaResumo("Total estimado (1º pagamento)", resumo.total(), false));
    }

    private HorizontalLayout linhaResumo(String label, double valor, boolean destacarDesconto) {
        Span nome = new Span(label);
        Span val = new Span(String.format("%.2f €", valor));
        val.getStyle().set("font-weight", "600");
        if (destacarDesconto) val.getStyle().set("color", "#27ae60");
        HorizontalLayout linha = new HorizontalLayout(nome, val);
        linha.setWidthFull();
        linha.setJustifyContentMode(JustifyContentMode.BETWEEN);
        return linha;
    }

    private void submeter(Aluno aluno, Set<Turma> selecionadas) {
        if (selecionadas.isEmpty()) {
            Notification.show("Escolhe pelo menos uma turma.").addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        Map<Turma, Integer> turmasEFrequencia = new HashMap<>();
        for (Turma t : selecionadas) {
            Select<String> freqSelect = mapaFrequencias.get(t.getId());
            int freq = (freqSelect != null && "1 vez / semana".equals(freqSelect.getValue())) ? 1 : 2;
            turmasEFrequencia.put(t, freq);
        }

        boolean submetido = renovacaoService.submeterRenovacao(aluno, turmasEFrequencia, studioAtual.getNome());
        if (!submetido) {
            Notification.show("Já existe um pedido em curso para este educando. Contacta o estúdio.", 5000,
                    Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_WARNING);
            return;
        }

        mostrarSucesso();
    }

    private void mostrarSucesso() {
        removerConteudoAposHeader();
        VerticalLayout layout = new VerticalLayout(
                new Icon(VaadinIcon.CHECK_CIRCLE),
                new H2(translationService.t("renovacao.sucesso.titulo", idiomaAtual)),
                new Span(studioAtual.getNome() + translationService.t("renovacao.sucesso.msg", idiomaAtual)));
        layout.setAlignItems(Alignment.CENTER);
        card.add(layout);
    }

    /** Remove tudo do card exceto o cabeçalho (primeiro filho), para trocar de passo. */
    private void removerConteudoAposHeader() {
        List<com.vaadin.flow.component.Component> filhos = card.getChildren().collect(Collectors.toList());
        for (int i = 1; i < filhos.size(); i++) {
            card.remove(filhos.get(i));
        }
    }
}
