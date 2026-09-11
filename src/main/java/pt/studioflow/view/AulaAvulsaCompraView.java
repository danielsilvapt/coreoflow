package pt.studioflow.view;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.ContaPortal;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.OrigemCompraCredito;
import pt.studioflow.model.PackAula;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.ContaPortalRepository;
import pt.studioflow.repository.ModalidadeRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.service.CompraCreditoService;
import pt.studioflow.service.EmailService;
import pt.studioflow.service.PackAulaService;

/**
 * Compra pública de aula avulsa / pack de aulas, a partir do link /join.
 * Sem validação de admin: o acesso fica ativo assim que a compra é submetida
 * (com Mollie configurado, redireciona para o checkout; senão fica "pagar no
 * estúdio", com o crédito já ativo e o pagamento por regularizar).
 */
@Route("aula-avulsa")
@AnonymousAllowed
public class AulaAvulsaCompraView extends VerticalLayout implements BeforeEnterObserver {

    private final StudioRepository studioRepository;
    private final ModalidadeRepository modalidadeRepository;
    private final PackAulaService packAulaService;
    private final AlunoRepository alunoRepository;
    private final CompraCreditoService compraCreditoService;
    private final ContaPortalRepository contaPortalRepository;
    private final EmailService emailService;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:https://app.coreoflow.me}")
    private String baseUrl;

    private final VerticalLayout card = new VerticalLayout();
    private Studio studioAtual;
    private String slug;

    public AulaAvulsaCompraView(StudioRepository studioRepository, ModalidadeRepository modalidadeRepository,
            PackAulaService packAulaService, AlunoRepository alunoRepository,
            CompraCreditoService compraCreditoService, ContaPortalRepository contaPortalRepository,
            EmailService emailService) {
        this.studioRepository = studioRepository;
        this.modalidadeRepository = modalidadeRepository;
        this.packAulaService = packAulaService;
        this.alunoRepository = alunoRepository;
        this.compraCreditoService = compraCreditoService;
        this.contaPortalRepository = contaPortalRepository;
        this.emailService = emailService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        slug = event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("studio", List.of()).stream().findFirst().orElse(null);

        if (slug != null && !slug.isBlank()) {
            studioAtual = studioRepository.findBySlugAndAtivoTrue(slug).orElse(null);
        }

        if (studioAtual == null) {
            removeAll();
            add(new H2("Estúdio não encontrado."), new Span("O link é inválido ou o estúdio já não está ativo."));
            return;
        }

        construirLayoutBase();

        boolean voltaDoMollie = !event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("compra", List.of()).isEmpty();
        if (voltaDoMollie) {
            mostrarConfirmacao(true);
        } else {
            mostrarFormulario();
        }
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
        logo.setWidth("min(160px, 36vw)");

        H2 titulo = new H2("Aula Avulsa / Packs");
        Span subtitulo = new Span("Sem matrícula, sem validação — o acesso fica ativo de imediato.");
        subtitulo.getStyle().set("color", "#7f8c8d").set("text-align", "center");

        VerticalLayout header = new VerticalLayout(logo, titulo, subtitulo);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(false);
        header.getStyle().set("margin-bottom", "12px");

        removeAll();
        card.removeAll();
        card.setWidth("min(480px, 94vw)");
        card.getStyle()
                .set("background", "white").set("border-radius", "16px").set("padding", "28px 20px")
                .set("box-shadow", "0 4px 24px rgba(0,0,0,0.08)");

        add(header, card);
    }

    private void mostrarFormulario() {
        card.removeAll();

        List<PackAula> packs = packAulaService.listarAtivosPorStudio(studioAtual);
        if (packs.isEmpty()) {
            card.add(new Span("Este estúdio ainda não tem packs de aulas avulso disponíveis."));
            return;
        }

        List<Modalidade> modalidades = modalidadeRepository.findAllByStudio(studioAtual).stream()
                .filter(Modalidade::isAtivo).collect(Collectors.toList());

        TextField nome = new TextField("Nome completo");
        nome.setWidthFull();
        nome.setRequired(true);

        EmailField email = new EmailField("Email");
        email.setWidthFull();
        email.setRequired(true);
        email.setHelperText("Se já és aluno(a), usa o mesmo email da tua conta.");

        TextField telemovel = new TextField("Telemóvel");
        telemovel.setWidthFull();

        ComboBox<Modalidade> modalidadeCombo = new ComboBox<>("Modalidade");
        modalidadeCombo.setItems(modalidades);
        modalidadeCombo.setItemLabelGenerator(Modalidade::getDescricao);
        modalidadeCombo.setWidthFull();
        modalidadeCombo.setHelperText("Só é vinculativa se o pack escolhido for restrito a uma modalidade.");

        ComboBox<PackAula> packCombo = new ComboBox<>("Pack");
        packCombo.setItems(packs);
        packCombo.setItemLabelGenerator(p -> p.getNome() + " — " + p.getNumAulas() + " aula(s) — " + p.getPreco() + "€"
                + (p.isRestritoAModalidade() ? " (modalidade fixa)" : ""));
        packCombo.setWidthFull();
        packCombo.setRequired(true);

        RadioButtonGroup<String> pagamento = new RadioButtonGroup<>();
        pagamento.setLabel("Pagamento");
        if (studioAtual.isMollieAtivo()) {
            pagamento.setItems("Pagar agora (online)", "Pagar no estúdio");
            pagamento.setValue("Pagar agora (online)");
        } else {
            pagamento.setItems("Pagar no estúdio");
            pagamento.setValue("Pagar no estúdio");
        }

        Button submeter = new Button("Confirmar Compra");
        submeter.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submeter.setWidthFull();
        submeter.getStyle().set("height", "52px").set("margin-top", "12px").set("font-weight", "700");

        submeter.addClickListener(e -> {
            if (nome.isEmpty() || email.isEmpty() || packCombo.isEmpty()) {
                Notification.show("Preenche nome, email e escolhe um pack.").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            PackAula pack = packCombo.getValue();
            if (pack.isRestritoAModalidade() && modalidadeCombo.isEmpty()) {
                Notification.show("Este pack exige que escolhas uma modalidade.").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            Aluno aluno = obterOuCriarAluno(nome.getValue().trim(), email.getValue().trim(), telemovel.getValue());
            boolean usarMollie = "Pagar agora (online)".equals(pagamento.getValue());

            try {
                CompraCreditoService.ResultadoCompra resultado = compraCreditoService.criarCompra(
                        aluno, pack, modalidadeCombo.getValue(), usarMollie, OrigemCompraCredito.JOIN_PUBLICO,
                        baseUrl + "/aula-avulsa?studio=" + slug + "&compra=" + java.util.UUID.randomUUID(),
                        baseUrl + "/api/mollie/webhook");

                if (resultado.mollieCheckoutUrl != null) {
                    UI.getCurrent().getPage().setLocation(resultado.mollieCheckoutUrl);
                } else {
                    mostrarConfirmacao(false);
                }
            } catch (Exception ex) {
                Notification.show("Não foi possível concluir a compra: " + ex.getMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        card.add(nome, email, telemovel, modalidadeCombo, packCombo, pagamento, submeter);
    }

    private Aluno obterOuCriarAluno(String nome, String email, String telemovel) {
        List<Aluno> existentes = alunoRepository.findByEmailAndStudioWithTurmas(email, studioAtual);
        if (!existentes.isEmpty()) {
            return existentes.get(0);
        }
        Aluno novo = new Aluno();
        novo.setNomeCompleto(nome);
        novo.setEmail(email);
        novo.setTelemovel(telemovel);
        novo.setStatus(Aluno.AlunoStatus.EXPERIMENTAL);
        novo.setAtivo(true);
        novo.setCarimboDataHora(LocalDate.now());
        novo.setDataInscricao(LocalDate.now());
        novo.setStudio(studioAtual);
        novo = alunoRepository.save(novo);

        provisionarAcessoPortal(email, nome);
        return novo;
    }

    /**
     * Para um visitante novo (sem conta ainda), cria/reenvia o convite de acesso
     * ao portal do aluno (mesmo fluxo de {@code ContasPortalView}) para que
     * consiga fazer login e o self-checkin das aulas que comprou.
     */
    private void provisionarAcessoPortal(String email, String nome) {
        ContaPortal conta = contaPortalRepository.findByEmailIgnoreCase(email).orElseGet(ContaPortal::new);
        if (conta.isAtivo()) return; // já tem acesso, não reenvia convite
        conta.setEmail(email);
        conta.setNome(nome);
        conta.setStudio(studioAtual);
        conta.setTokenAtivacao(java.util.UUID.randomUUID().toString().replace("-", ""));
        conta.setTokenExpiraEm(java.time.LocalDateTime.now().plusDays(30));
        contaPortalRepository.save(conta);
        emailService.enviarConvitePortal(email, studioAtual, baseUrl + "/portal-ativar?token=" + conta.getTokenAtivacao());
    }

    private void mostrarConfirmacao(boolean viaMollie) {
        card.removeAll();
        H4 titulo = new H4(viaMollie ? "Pagamento em confirmação" : "Compra confirmada!");
        Span texto = new Span(viaMollie
                ? "Recebemos o teu pagamento e estamos a confirmá-lo — o teu acesso já está ativo."
                : "O teu crédito já está ativo. Paga no estúdio antes/durante a aula.");
        Span janela = new Span("No dia da aula, faz o checkin entre "
                + studioAtual.getCheckinJanelaAntesMin() + " min antes e "
                + studioAtual.getCheckinJanelaDepoisMin() + " min depois do início, na área de aluno.");
        janela.getStyle().set("color", "#7f8c8d").set("font-size", "0.9em").set("margin-top", "8px");

        Button irPortal = new Button("Ir para a área de aluno", e -> UI.getCurrent().navigate("portal"));
        irPortal.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        irPortal.setWidthFull();
        irPortal.getStyle().set("height", "52px").set("margin-top", "16px");

        card.add(titulo, texto, janela, irPortal);
    }
}
