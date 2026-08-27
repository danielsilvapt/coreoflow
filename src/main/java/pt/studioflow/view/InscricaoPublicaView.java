package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.EmailValidator;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.Idioma;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.EmailService;
import pt.studioflow.service.TranslationService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Route("inscricao")
@AnonymousAllowed
public class InscricaoPublicaView extends VerticalLayout implements BeforeEnterObserver {

    private final AlunoRepository alunoRepository;
    private final TurmaRepository turmaRepository;
    private final StudioRepository studioRepository;
    private final MensalidadeConfig mensalidadeConfig;
    private final TranslationService translationService;
    private final Binder<Aluno> binder = new Binder<>(Aluno.class);
    private Idioma idiomaAtual = Idioma.PT;

    @Autowired
    private final EmailService emailService;

    private final Map<Long, Select<String>> mapaFrequencias = new HashMap<>();
    private final VerticalLayout containerFrequencias = new VerticalLayout();
    private final Div resumoPagamento = new Div();

    private byte[] fotoBytesTemporaria = null;
    private Studio studioAtual = null;
    private final BuildProperties buildProperties;

    public InscricaoPublicaView(AlunoRepository alunoRepository, TurmaRepository turmaRepository,
            EmailService emailService, StudioRepository studioRepository, BuildProperties buildProperties,
            MensalidadeConfig mensalidadeConfig, TranslationService translationService) {
        this.alunoRepository = alunoRepository;
        this.turmaRepository = turmaRepository;
        this.emailService = emailService;
        this.studioRepository = studioRepository;
        this.buildProperties = buildProperties;
        this.mensalidadeConfig = mensalidadeConfig;
        this.translationService = translationService;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String slug = event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("studio", java.util.List.of()).stream()
                .findFirst().orElse(null);

        if (slug != null && !slug.isBlank()) {
            studioAtual = studioRepository.findBySlugAndAtivoTrue(slug).orElse(null);
        }

        if (studioAtual == null && slug != null) {
            removeAll();
            add(new com.vaadin.flow.component.html.H2("Estúdio não encontrado."),
                new Span("O link de inscrição é inválido ou o estúdio já não está ativo."));
            return;
        }

        construirUI();
    }

    private void construirUI() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setPadding(true);
        getStyle().set("background-color", "#f8f9fa");

        // --- 1. CABEÇALHO ---
        String logoSrc = (studioAtual != null && studioAtual.getLogoPath() != null && !studioAtual.getLogoPath().isBlank())
                ? studioAtual.getLogoPath().replaceFirst("^src/main/resources/static/", "").replaceFirst("^static/", "").replaceFirst("^/", "")
                : "images/logo-coreoflow.png";
        String studioNomeDisplay = (studioAtual != null) ? studioAtual.getNome() : "CoreoFlow";
        Image logo = new Image(logoSrc, studioNomeDisplay);
        logo.setWidth("min(180px, 40vw)");

        H2 titulo = new H2(translationService.t("inscricao.titulo", idiomaAtual));
        Span subtitulo = new Span(translationService.t("inscricao.subtitulo", idiomaAtual));

        VerticalLayout header = new VerticalLayout(logo, titulo, subtitulo);
        header.setAlignItems(Alignment.CENTER);
        header.setSpacing(false);

        java.util.List<Idioma> idiomasStudio = studioAtual != null
                ? studioAtual.getIdiomasDisponiveisList() : java.util.List.of(Idioma.PT);
        Select<Idioma> seletorIdioma = new Select<>();
        if (idiomasStudio.size() > 1) {
            seletorIdioma.setItems(idiomasStudio);
            seletorIdioma.setValue(Idioma.PT);
            seletorIdioma.setItemLabelGenerator(Idioma::getLabel);
            seletorIdioma.getStyle().set("margin-top", "8px");
            header.add(seletorIdioma);
        }

        // --- 1.5. COMPONENTE DE UPLOAD DA FOTO (NOVO) ---
        MemoryBuffer buffer = new MemoryBuffer();
        Upload uploadFoto = new Upload(buffer);
        uploadFoto.setAcceptedFileTypes("image/jpeg", "image/png");
        uploadFoto.setMaxFileSize(3 * 1024 * 1024); // Limite de 3MB para a BD
        uploadFoto.setDropAllowed(true);

        Button btnUpload = new Button("Escolher Foto...", new Icon(VaadinIcon.CAMERA));
        uploadFoto.setUploadButton(btnUpload);
        uploadFoto.setDropLabel(new Span("Arraste a foto do aluno para aqui"));

        // Container visual do Preview (Avatar Redondo)
        Div previewAvatar = new Div(new Icon(VaadinIcon.USER));
        previewAvatar.getStyle()
                .set("background", "#e9ecef")
                .set("width", "90px")
                .set("height", "90px")
                .set("border-radius", "50%")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("overflow", "hidden")
                .set("border", "2px solid #ced4da")
                .set("flex-shrink", "0");

        // Listener executado quando o upload termina com sucesso
        uploadFoto.addSucceededListener(event -> {
            try {
                fotoBytesTemporaria = buffer.getInputStream().readAllBytes();

                StreamResource resource = new StreamResource("preview.png",
                        () -> new ByteArrayInputStream(fotoBytesTemporaria));
                Image imgPreview = new Image(resource, "Preview");
                imgPreview.getStyle()
                        .set("width", "100%")
                        .set("height", "100%")
                        .set("object-fit", "cover");

                previewAvatar.removeAll();
                previewAvatar.add(imgPreview);

            } catch (IOException e) {
                Notification.show("Erro ao processar a imagem.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        // Listener executado quando o ficheiro é rejeitado (ex: tamanho excedido)
        uploadFoto.addFileRejectedListener(event -> {
            Notification.show(event.getErrorMessage(), 4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        // Listener utilizando evento nativo para quando o utilizador remove a imagem
        // (clica no X)
        uploadFoto.getElement().addEventListener("file-remove", event -> {
            fotoBytesTemporaria = null;
            previewAvatar.removeAll();
            previewAvatar.add(new Icon(VaadinIcon.USER));
        });

        // Organização horizontal do Preview + Upload
        HorizontalLayout layoutFoto = new HorizontalLayout(previewAvatar, uploadFoto);
        layoutFoto.setAlignItems(Alignment.CENTER);
        layoutFoto.setSpacing(true);
        layoutFoto.setWidthFull();
        layoutFoto.getStyle().set("margin-top", "0.5em").set("margin-bottom", "1em");

        // --- 2. CAMPOS DO FORMULÁRIO ---
        TextField nomeCompleto = new TextField("Nome Completo");
        DatePicker dataNascimento = new DatePicker("Data de Nascimento");
        Select<String> genero = new Select<>();
        genero.setLabel("Género");
        genero.setItems("Masculino", "Feminino", "Outro");

        TextField nif = new TextField("Nº Contribuinte (NIF)");
        TextField numIdentificacao = new TextField("Nº Identificação (CC/Passaporte)");

        ComboBox<String> nacionalidade = new ComboBox<>("Nacionalidade");
        nacionalidade.setItems("Portuguesa", "Brasileira", "Espanhola", "Francesa", "Outra");
        nacionalidade.setAllowCustomValue(true);
        nacionalidade.addCustomValueSetListener(e -> nacionalidade.setValue(e.getDetail()));

        TextField telemovel = new TextField("Telemóvel");
        EmailField email = new EmailField("E-mail");
        TextField morada = new TextField("Morada Completa");
        TextField codigoPostal = new TextField("Código Postal");
        TextField localidade = new TextField("Localidade");

        codigoPostal.setPlaceholder("0000-000");

        codigoPostal.addBlurListener(event -> {
            String cpDigitado = codigoPostal.getValue();
            if (cpDigitado != null && cpDigitado.matches("\\d{4}-\\d{3}")) {
                String cidadeEncontrada = alunoRepository.findLocalidadeByCodPostal(cpDigitado);
                if (cidadeEncontrada != null && !cidadeEncontrada.isEmpty()) {
                    localidade.setValue(cidadeEncontrada);
                } else {
                    Notification.show("Código postal não reconhecido. Por favor, insira manualmente.");
                }
            }
        });

        Select<String> seguroDesportivo = new Select<>();
        seguroDesportivo.setLabel("Seguro Desportivo");
        seguroDesportivo.setItems("Associação", "Próprio");

        Checkbox socio = new Checkbox("Desejo tornar-me sócio");

        // --- 3. SELEÇÃO DE TURMAS ---
        MultiSelectComboBox<Turma> turmasInteresse = new MultiSelectComboBox<>("Escolha as Turmas");
        if (studioAtual != null) {
            turmasInteresse.setItems(turmaRepository.findByStudioAndAtivoTrue(studioAtual));
        } else {
            turmasInteresse.setItems(turmaRepository.findAllComplete());
        }
        turmasInteresse.setItemLabelGenerator(t -> t.getModalidade().getDescricao() + " - " + t.getDescricao());
        turmasInteresse.setWidthFull();

        containerFrequencias.setPadding(false);
        containerFrequencias.setSpacing(true);
        containerFrequencias.setWidthFull();

        resumoPagamento.getStyle()
                .set("background", "#f8f9fa")
                .set("border-radius", "10px")
                .set("padding", "16px")
                .set("margin-top", "8px");

        Runnable atualizarResumo = () -> atualizarResumoInscricao(
                turmasInteresse.getValue(), dataNascimento.getValue(), socio.getValue());

        turmasInteresse.addValueChangeListener(event -> {
            Set<Turma> atuais = event.getValue();
            mapaFrequencias.keySet().removeIf(id -> {
                boolean removido = atuais.stream().noneMatch(t -> t.getId().equals(id));
                if (removido) {
                    containerFrequencias.getChildren()
                            .filter(comp -> comp.getElement().getAttribute("data-id") != null &&
                                    comp.getElement().getAttribute("data-id").equals(id.toString()))
                            .findFirst().ifPresent(containerFrequencias::remove);
                }
                return removido;
            });

            atuais.forEach(t -> {
                if (!mapaFrequencias.containsKey(t.getId())) {
                    Select<String> freqSelect = new Select<>();
                    freqSelect.setItems("1 vez / semana", "2 vezes / semana");
                    freqSelect.setValue("2 vezes / semana");
                    freqSelect.setLabel("Frequência em " + t.getDescricao());
                    freqSelect.setWidthFull();
                    freqSelect.addValueChangeListener(ev -> atualizarResumo.run());

                    HorizontalLayout row = new HorizontalLayout(new Icon(VaadinIcon.ARROW_RIGHT), freqSelect);
                    row.setWidthFull();
                    row.setAlignItems(Alignment.END);
                    row.getElement().setAttribute("data-id", t.getId().toString());

                    containerFrequencias.add(row);
                    mapaFrequencias.put(t.getId(), freqSelect);
                }
            });

            atualizarResumo.run();
        });

        dataNascimento.addValueChangeListener(ev -> atualizarResumo.run());
        socio.addValueChangeListener(ev -> atualizarResumo.run());

        // --- 4. BINDER ---
        binder.forField(nomeCompleto).asRequired("Obrigatório").bind(Aluno::getNomeCompleto, Aluno::setNomeCompleto);
        binder.forField(dataNascimento).asRequired("Obrigatório").bind(Aluno::getDataNascimento,
                Aluno::setDataNascimento);
        binder.forField(email).asRequired("Obrigatório").withValidator(new EmailValidator("E-mail inválido"))
                .bind(Aluno::getEmail, Aluno::setEmail);
        binder.forField(nif).asRequired("Obrigatório").withValidator(n -> n.matches("\\d{9}"), "9 dígitos")
                .bind(Aluno::getNumeroContribuinte, Aluno::setNumeroContribuinte);
        binder.forField(numIdentificacao).asRequired("Obrigatório").bind(Aluno::getNumeroIdentificacao,
                Aluno::setNumeroIdentificacao);
        binder.forField(telemovel).asRequired("Obrigatório").bind(Aluno::getTelemovel, Aluno::setTelemovel);
        binder.forField(nacionalidade).bind(Aluno::getNacionalidade, Aluno::setNacionalidade);
        binder.forField(morada).asRequired("Obrigatório").bind(Aluno::getMorada, Aluno::setMorada);
        binder.forField(seguroDesportivo).bind(Aluno::getSeguroDesportivo, Aluno::setSeguroDesportivo);
        binder.forField(socio).bind(Aluno::isSocio, Aluno::setSocio);
        binder.forField(genero).bind(Aluno::getGenero, Aluno::setGenero);
        binder.forField(codigoPostal).asRequired("O código postal é obrigatório")
                .withValidator(cp -> cp.matches("\\d{4}-\\d{3}"), "O código postal deve ter o formato 0000-000")
                .bind(Aluno::getCodigoPostal, Aluno::setCodigoPostal);
        binder.forField(localidade).asRequired("A localidade é obrigatória").bind(Aluno::getLocalidade,
                Aluno::setLocalidade);

        // --- 5. LAYOUT RESPONSIVO ---
        FormLayout form = new FormLayout();
        form.add(nomeCompleto, dataNascimento, genero, nif, numIdentificacao, nacionalidade,
                telemovel, email, morada, codigoPostal, localidade, seguroDesportivo, socio);

        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        form.setColspan(nomeCompleto, 2);
        form.setColspan(morada, 2);

        // --- 6. BOTÃO SUBMETER ---
        Button btnSubmeter = new Button("Finalizar Inscrição");
        btnSubmeter.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        btnSubmeter.setWidthFull();
        btnSubmeter.setHeight("55px");
        btnSubmeter.addClickListener(e -> submeter(turmasInteresse.getValue()));

        // Inserção ordenada dos componentes dentro do card (Cabeçalho -> Upload Foto ->
        // Formulário -> Turmas)
        seletorIdioma.addValueChangeListener(ev -> {
            idiomaAtual = ev.getValue();
            titulo.setText(translationService.t("inscricao.titulo", idiomaAtual));
            subtitulo.setText(translationService.t("inscricao.subtitulo", idiomaAtual));
            nomeCompleto.setLabel(translationService.t("inscricao.nomeCompleto", idiomaAtual));
            dataNascimento.setLabel(translationService.t("inscricao.dataNascimento", idiomaAtual));
            telemovel.setLabel(translationService.t("inscricao.telemovel", idiomaAtual));
            email.setLabel(translationService.t("inscricao.email", idiomaAtual));
            morada.setLabel(translationService.t("inscricao.morada", idiomaAtual));
            turmasInteresse.setLabel(translationService.t("inscricao.turmas", idiomaAtual));
            btnSubmeter.setText(translationService.t("inscricao.submeter", idiomaAtual));
        });

        VerticalLayout card = new VerticalLayout(header, layoutFoto, form, turmasInteresse, containerFrequencias,
                resumoPagamento, btnSubmeter);
        card.setMaxWidth("850px");
        card.setWidth("100%");
        card.getStyle()
                .set("background", "white")
                .set("padding", "min(2.5em, 5vw)")
                .set("border-radius", "15px")
                .set("box-shadow", "0 10px 30px rgba(0,0,0,0.1)");

        add(card);

        // --- FOOTER ---
        Image logoSF = new Image("images/logo-coreoflow.png", "CoreoFlow");
        logoSF.setHeight("22px");
        logoSF.getStyle().set("opacity", "0.6");

        String versao = buildProperties.getVersion().replace("-SNAPSHOT", "");
             Span textoFooter = new Span("v" + versao + " · by Daniel Silva");
        textoFooter.getStyle()
                .set("font-size", "11px")
                .set("color", "#adb5bd");

        HorizontalLayout footer = new HorizontalLayout(logoSF, textoFooter);
        footer.setAlignItems(Alignment.CENTER);
        footer.setJustifyContentMode(com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.CENTER);
        footer.getStyle()
                .set("margin-top", "24px")
                .set("padding-bottom", "16px")
                .set("gap", "8px");
        footer.setWidthFull();

        add(footer);
    } // fim construirUI

    private void atualizarResumoInscricao(Set<Turma> selecionadas, LocalDate dataNasc, boolean socioVal) {
        resumoPagamento.removeAll();
        if (selecionadas.isEmpty() || studioAtual == null) return;

        boolean crianca = dataNasc != null && java.time.Period.between(dataNasc, LocalDate.now()).getYears() < 18;

        double mensalidadeBaseTotal = selecionadas.stream()
                .mapToDouble(t -> {
                    Select<String> freqSelect = mapaFrequencias.get(t.getId());
                    int freq = (freqSelect != null && "1 vez / semana".equals(freqSelect.getValue())) ? 1 : 2;
                    return mensalidadeConfig.calcularMensalidade(studioAtual, crianca ? "crianca" : "adulto", freq, socioVal);
                }).sum();

        long numModalidades = selecionadas.stream().map(Turma::getModalidade).map(Modalidade::getId).distinct().count();

        MensalidadeConfig.ResumoInscricao resumo = mensalidadeConfig.calcularResumo(
                studioAtual, mensalidadeBaseTotal, false, false, (int) numModalidades);

        resumoPagamento.add(
                linhaResumo("Mensalidade estimada", resumo.mensalidadeBase(), false),
                linhaResumo("Taxa de inscrição", resumo.taxa(), false),
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
        linha.setJustifyContentMode(com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.BETWEEN);
        return linha;
    }

    private void submeter(Set<Turma> selecionadas) {
        Aluno novo = new Aluno();
        if (binder.writeBeanIfValid(novo)) {
            if (selecionadas.isEmpty()) {
                Notification.show("Selecione pelo menos uma turma.").addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            if (fotoBytesTemporaria != null) {
                novo.setFoto(fotoBytesTemporaria);
            }
            novo.setStatus(Aluno.AlunoStatus.PENDENTE);
            novo.setAtivo(false);
            novo.setCarimboDataHora(java.time.LocalDate.now());
            novo.setStudio(studioAtual);

            String tStr = selecionadas.stream()
                    .map(t -> {
                        String desc = t.getModalidade().getDescricao() + " " + t.getDescricao();
                        String freq = mapaFrequencias.get(t.getId()).getValue();
                        return desc + " (" + freq + ")";
                    })
                    .collect(Collectors.joining(", "));

            String moradaOriginal = novo.getMorada() != null ? novo.getMorada() : "";
            novo.setMorada(moradaOriginal + " [Interesses: " + tStr + "]");

            alunoRepository.save(novo);
            emailService.enviarEmailNotificacao(novo);
            mostrarSucesso();
        } else {
            Notification.show("Preencha todos os campos obrigatórios.")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void mostrarSucesso() {
        this.removeAll();
        String nome = studioAtual != null ? studioAtual.getNome() : "o estúdio";
        VerticalLayout layout = new VerticalLayout(
                new com.vaadin.flow.component.icon.Icon(com.vaadin.flow.component.icon.VaadinIcon.CHECK_CIRCLE),
                new H2(translationService.t("inscricao.sucesso.titulo", idiomaAtual)),
                new Span(nome + translationService.t("inscricao.sucesso.msg", idiomaAtual)));
        layout.setAlignItems(Alignment.CENTER);
        add(layout);
    }
}
