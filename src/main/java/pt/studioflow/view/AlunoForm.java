package pt.studioflow.view;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Arrays;
import java.util.Optional;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.converter.StringToIntegerConverter;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.CampoAluno;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.service.R2StorageService;

import java.time.Duration;
import java.util.UUID;

// Adicionada a Rota e Segurança
@Route(value = "aluno", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AlunoForm extends VerticalLayout implements HasUrlParameter<String> {

    private final AlunoRepository alunoRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final R2StorageService storageService;
    private Aluno alunoAtual;
    private final Binder<Aluno> binder = new Binder<>(Aluno.class);

    /* ====== CAMPOS ====== */
    private final TextField nomeCompleto = new TextField("Nome Completo");
    private final Select<String> genero = new Select<>();
    private final DatePicker dataNascimento = new DatePicker("Data de Nascimento");
    private final ComboBox<String> criancaCombo = new ComboBox<>("Tipo de Aluno");

    private final TextField telemovel = new TextField("Telemóvel");
    private final EmailField email = new EmailField("Email");
    private final TextField morada = new TextField("Morada");
    private final TextField codigoPostal = new TextField("Código Postal");
    private final TextField localidade = new TextField("Localidade");
    private final ComboBox<String> nacionalidade = new ComboBox<>("Nacionalidade");

    private final TextField numeroIdentificacao = new TextField("Nº Identificação");
    private final TextField numeroContribuinte = new TextField("Nº Contribuinte");

    private final Checkbox socio = new Checkbox("Sócio");

    private final TextField numeroSocio = new TextField("Nº Sócio");
    private final Select<String> seguroDesportivo = new Select<>();

    private final DatePicker dataQuotaPagamento = new DatePicker("Data Pagamento Quota");
    private final DatePicker dataExpiracaoQuota = new DatePicker("Expiração Quota");
    private final DatePicker dataSeguroPagamento = new DatePicker("Data Pagamento Seguro");
    private final DatePicker dataExpiracaoSeguro = new DatePicker("Expiração Seguro");
    private final DatePicker dataInscricao = new DatePicker("Data de Inscrição");
    private final DatePicker dataInscricaoRenovacao = new DatePicker("Data de Renovação");

    private final Checkbox ativo = new Checkbox("Ativo");
    private final Checkbox divida = new Checkbox("Dívida");

    private Dialog dialog;
    private Runnable onSaveCallback;

    private final Image imagePreview = new Image();
    private byte[] fotoBytes;
    private String fotoMimeType;
    private boolean fotoAlterada;

    private final Grid<AlunoTurma> gridTurmas = new Grid<>(AlunoTurma.class, false);

    public AlunoForm(AlunoRepository alunoRepository, AlunoTurmaRepository alunoTurmaRepository,
            R2StorageService storageService) {
        this.alunoRepository = alunoRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.storageService = storageService;

        binder.bindInstanceFields(this);

        configurarFormatoDatas();
        configurarValidacoes();
        configurarGridTurmas();
        add(criarCabecalhoIdentidade(), criarTabs());
    }

    // MÉTODO NOVO: Recebe o ID da URL (ex: aluno/1) e abre o formulário
    @Override
    public void setParameter(BeforeEvent event, String parameter) {
        if (parameter != null && !parameter.isEmpty()) {
            try {
                Long id = Long.parseLong(parameter);
                Optional<Aluno> alunoOpt = alunoRepository.findById(id);
                if (alunoOpt.isPresent()) {
                    abrir(alunoOpt.get());
                } else {
                    Notification.show("Aluno não encontrado.");
                }
            } catch (NumberFormatException e) {
                Notification.show("ID inválido.");
            }
        }
    }

    private void configurarFormatoDatas() {
        DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
        i18n.setDateFormat("dd/MM/yyyy");
        i18n.setMonthNames(Arrays.asList("Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"));
        i18n.setWeekdays(Arrays.asList("Domingo", "Segunda-feira", "Terça-feira", "Quarta-feira",
                "Quinta-feira", "Sexta-feira", "Sábado"));
        i18n.setWeekdaysShort(Arrays.asList("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"));
        i18n.setToday("Hoje");
        i18n.setCancel("Cancelar");

        List<DatePicker> camposData = Arrays.asList(
                dataNascimento, dataQuotaPagamento, dataExpiracaoQuota,
                dataSeguroPagamento, dataExpiracaoSeguro, dataInscricao, dataInscricaoRenovacao);
        camposData.forEach(dp -> dp.setI18n(i18n));
    }

    private void configurarValidacoes() {
        binder.forField(nomeCompleto)
                .asRequired("O nome é obrigatório")
                .bind(Aluno::getNomeCompleto, Aluno::setNomeCompleto);

        genero.setLabel("Género");
        genero.setItems("Masculino", "Feminino", "Outro");
        binder.forField(genero)
                .asRequired("Selecione o género")
                .bind(
                        aluno -> {
                            if ("M".equals(aluno.getGenero()))
                                return "Masculino";
                            if ("F".equals(aluno.getGenero()))
                                return "Feminino";
                            return aluno.getGenero();
                        },
                        (aluno, valorUI) -> {
                            if ("Masculino".equals(valorUI))
                                aluno.setGenero("M");
                            else if ("Feminino".equals(valorUI))
                                aluno.setGenero("F");
                            else
                                aluno.setGenero(valorUI);
                        });

        criancaCombo.setItems("Adulto", "Criança");
        binder.forField(criancaCombo)
                .asRequired("Defina se é Adulto ou Criança")
                .bind(
                        aluno -> aluno.isCrianca() ? "Criança" : "Adulto",
                        (aluno, valor) -> aluno.setCrianca("Criança".equals(valor)));

        // Preenche automaticamente Adulto/Criança a partir da data de nascimento
        // (< 18 anos = Criança). Só quando o utilizador mexe no campo, para não
        // sobrepor o valor guardado ao abrir um aluno existente. Continua editável.
        dataNascimento.addValueChangeListener(ev -> {
            if (!ev.isFromClient() || ev.getValue() == null) return;
            boolean crianca = java.time.Period.between(ev.getValue(), java.time.LocalDate.now()).getYears() < 18;
            criancaCombo.setValue(crianca ? "Criança" : "Adulto");
        });

        binder.forField(numeroContribuinte)
                .asRequired("Insira o Nº de Contribuinte")
                .bind(Aluno::getNumeroContribuinte, Aluno::setNumeroContribuinte);

        binder.forField(numeroSocio)
                .asRequired("O número de sócio é obrigatório")
                .withConverter(new StringToIntegerConverter("Insira um número inteiro válido"))
                .bind(Aluno::getNumeroSocio, Aluno::setNumeroSocio);

        binder.forField(email)
                .withValidator(e -> e == null || e.isEmpty() || e.contains("@"), "E-mail inválido")
                .bind(Aluno::getEmail, Aluno::setEmail);

        binder.bindInstanceFields(this);
    }

    private void configurarGridTurmas() {
        gridTurmas.addColumn(at -> at.getTurma() != null ? at.getTurma().getDescricao() : "N/A")
                .setHeader("Turma").setAutoWidth(true);

        gridTurmas.addColumn(at -> (at.getTurma() != null && at.getTurma().getModalidade() != null)
                ? at.getTurma().getModalidade().getDescricao()
                : "-")
                .setHeader("Modalidade").setAutoWidth(true);

        gridTurmas.addColumn(at -> at.getAulasPorSemana())
                .setHeader("Vezes Semanais").setAutoWidth(true);

        gridTurmas.addColumn(at -> (at.getTurma() != null && at.getTurma().getProfessor() != null)
                ? at.getTurma().getProfessor().getNome()
                : "-")
                .setHeader("Professor").setAutoWidth(true); 

        gridTurmas.setAllRowsVisible(true);
        gridTurmas.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)");
    }

    private HorizontalLayout criarCabecalhoIdentidade() {
        imagePreview.setWidth("130px");
        imagePreview.setHeight("130px");
        imagePreview.getStyle()
                .set("border-radius", "50%")
                .set("object-fit", "cover")
                .set("border", "3px solid var(--lumo-primary-color-50pct)");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("image/png", "image/jpeg");
        upload.setUploadButton(new Button("Foto", VaadinIcon.CAMERA.create()));
        upload.setDropAllowed(false);
        upload.setWidth("130px");

        upload.addSucceededListener(e -> {
            try {
                fotoBytes = toByteArray(buffer.getInputStream());
                fotoMimeType = e.getMIMEType();
                fotoAlterada = true;
                imagePreview.setSrc(getImageSrc(fotoBytes));
            } catch (IOException ex) {
                Notification.show("Erro ao carregar imagem");
            }
        });

        VerticalLayout fotoLayout = new VerticalLayout(imagePreview, upload);
        fotoLayout.setAlignItems(Alignment.CENTER);
        fotoLayout.setPadding(false);
        fotoLayout.setSpacing(true);
        fotoLayout.setWidth("auto");

        FormLayout infoRapida = new FormLayout();
        infoRapida.add(nomeCompleto, criancaCombo);
        if (campoAtivo(CampoAluno.GENERO)) infoRapida.add(genero);
        if (campoAtivo(CampoAluno.SOCIO)) infoRapida.add(numeroSocio);

        HorizontalLayout socioAtivoRow = new HorizontalLayout(ativo);
        socioAtivoRow.setSpacing(true);
        if (campoAtivo(CampoAluno.SOCIO)) {
            socioAtivoRow.addComponentAsFirst(socio);
        }
        infoRapida.add(socioAtivoRow);
        infoRapida.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("400px", 2));
        infoRapida.setColspan(nomeCompleto, 2);

        HorizontalLayout header = new HorizontalLayout(fotoLayout, infoRapida);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setFlexGrow(1, infoRapida);
        header.setPadding(true);
        header.getStyle().set("background", "var(--lumo-base-color)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        return header;
    }

    private TabSheet criarTabs() {
        TabSheet tabs = new TabSheet();
        tabs.setSizeFull();
        tabs.add("🧍 Dados Pessoais", criarFormDadosPessoais());
        tabs.add("📞 Contactos & Morada", criarFormContactos());
        tabs.add("💳 Quotas & Seguro", criarFormQuotas());
        tabs.add("💃 Turmas Inscritas", criarTabTurmas());
        return tabs;
    }

    private FormLayout baseForm() {
        FormLayout form = new FormLayout();
        form.setWidthFull();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("600px", 2),
                new FormLayout.ResponsiveStep("900px", 3));
        form.getStyle().set("padding", "1rem");
        return form;
    }

    /** Verifica se um campo opcional está ativo para o estúdio atual. */
    private boolean campoAtivo(CampoAluno campo) {
        Studio s = TenantContext.getCurrentStudio();
        return s == null || s.hasCampo(campo);
    }

    private FormLayout criarFormDadosPessoais() {
        FormLayout f = baseForm();
        nacionalidade.setItems("Portuguesa", "Brasileira", "Espanhola", "Francesa", "Outra");
        f.add(dataNascimento);
        if (campoAtivo(CampoAluno.NACIONALIDADE)) f.add(nacionalidade);
        if (campoAtivo(CampoAluno.N_IDENTIFICACAO)) f.add(numeroIdentificacao);
        if (campoAtivo(CampoAluno.N_CONTRIBUINTE)) f.add(numeroContribuinte);
        return f;
    }

    private FormLayout criarFormContactos() {
        FormLayout f = baseForm();
        f.add(telemovel, email);
        if (campoAtivo(CampoAluno.MORADA)) {
            f.add(morada, codigoPostal, localidade);
            f.setColspan(morada, 3);
        }
        return f;
    }

    private VerticalLayout criarFormQuotas() {
        seguroDesportivo.setLabel("Seguro Desportivo");
        seguroDesportivo.setItems("Associação", "Próprio", "Outro");
        seguroDesportivo.setWidth("250px");
        dataInscricao.setWidth("250px");
        dataInscricaoRenovacao.setWidth("250px");

        boolean mostraSeguroSelect = campoAtivo(CampoAluno.SEGURO_DESPORTIVO);

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);

        // Datas de Inscrição/Renovação à esquerda (mostradas sempre) e o tipo de Seguro à
        // direita (acima do Seguro Desportivo), alinhado ao início do card "Seguro
        // Desportivo" logo abaixo, para as datas de pagamento/expiração ficarem alinhadas
        // entre as duas secções.
        HorizontalLayout topoRow = new HorizontalLayout();
        topoRow.setWidthFull();
        topoRow.setSpacing(true);

        HorizontalLayout datasInscricaoBox = new HorizontalLayout(dataInscricao, dataInscricaoRenovacao);
        datasInscricaoBox.setWidthFull();
        datasInscricaoBox.setSpacing(true);
        topoRow.add(datasInscricaoBox);
        topoRow.setFlexGrow(1, datasInscricaoBox);

        if (mostraSeguroSelect) {
            Div seguroComboBox = new Div(seguroDesportivo);
            seguroComboBox.setWidthFull();
            topoRow.add(seguroComboBox);
            topoRow.setFlexGrow(1, seguroComboBox);
        }
        layout.add(topoRow);

        boolean mostraQuotas = campoAtivo(CampoAluno.QUOTAS);

        if (mostraQuotas) {
            Div grupoQuota = criarGrupoQuotaSeguro("Quota", dataQuotaPagamento, dataExpiracaoQuota);
            Div grupoSeguro = criarGrupoQuotaSeguro("Seguro Desportivo", dataSeguroPagamento, dataExpiracaoSeguro);

            HorizontalLayout gruposRow = new HorizontalLayout(grupoQuota, grupoSeguro);
            gruposRow.setWidthFull();
            gruposRow.setSpacing(true);
            gruposRow.setFlexGrow(1, grupoQuota);
            gruposRow.setFlexGrow(1, grupoSeguro);
            layout.add(gruposRow);
        }
        return layout;
    }

    // Agrupa visualmente a data de pagamento com a respetiva data de expiração
    // (mesma coluna nas duas secções), para ficar claro a que cobrança pertence cada data.
    private Div criarGrupoQuotaSeguro(String titulo, DatePicker pagamento, DatePicker expiracao) {
        H4 h = new H4(titulo);
        h.getStyle().set("margin", "0 0 0.5rem 0");

        HorizontalLayout datas = new HorizontalLayout(pagamento, expiracao);
        datas.setWidthFull();
        datas.setSpacing(true);
        datas.setFlexGrow(1, pagamento);
        datas.setFlexGrow(1, expiracao);

        VerticalLayout conteudo = new VerticalLayout(h, datas);
        conteudo.setPadding(false);
        conteudo.setSpacing(true);

        Div grupo = new Div(conteudo);
        grupo.setWidthFull();
        grupo.getStyle().set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "8px")
                .set("padding", "1rem")
                .set("box-sizing", "border-box");
        return grupo;
    }

    private VerticalLayout criarTabTurmas() {
        VerticalLayout layout = new VerticalLayout(gridTurmas);
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setSizeFull();
        return layout;
    }

    public void abrir(Aluno aluno) {
        this.alunoAtual = (aluno != null) ? aluno : new Aluno();

        // 1. Lógica de dados e Grid
        if (aluno == null) {
            alunoAtual.setAtivo(true);
            alunoAtual.setSocio(false);
            alunoAtual.setCrianca(false);
            gridTurmas.setItems(Collections.emptyList());
        } else {
            List<AlunoTurma> inscricoes = alunoTurmaRepository.findByAluno(alunoAtual);
            gridTurmas.setItems(inscricoes);
        }

        // 2. Binder e Foto
        binder.setBean(alunoAtual);

        fotoBytes = null;
        fotoMimeType = null;
        fotoAlterada = false;
        if (alunoAtual.getFotoChave() != null && !alunoAtual.getFotoChave().isBlank()) {
            imagePreview.setSrc(storageService.gerarUrlTemporario(alunoAtual.getFotoChave(), Duration.ofHours(2)));
        } else {
            imagePreview.setSrc("");
        }

        // 3. REGRAS PARA EVITAR DUPLICADOS (Apenas um diálogo)
        if (dialog == null) {
            dialog = new Dialog();
            dialog.setWidth("1050px");
            dialog.setHeight("750px");
            dialog.add(this); // Adiciona o layout (this) apenas na criação

            // Configuração única dos botões do footer
            Button guardar = new Button("Guardar", VaadinIcon.CHECK.create(), e -> {
                if (binder.validate().isOk()) {
                    if (alunoAtual.getStudio() == null) {
                        alunoAtual.setStudio(TenantContext.getCurrentStudio());
                    }
                    if (fotoAlterada) {
                        String chaveAntiga = alunoAtual.getFotoChave();
                        Studio studio = alunoAtual.getStudio();
                        String chave = "alunos/" + (studio != null ? studio.getSlug() : "sem-estudio")
                                + "/" + UUID.randomUUID() + ".jpg";
                        try {
                            storageService.upload(chave, fotoMimeType != null ? fotoMimeType : "image/jpeg",
                                    new java.io.ByteArrayInputStream(fotoBytes), fotoBytes.length);
                        } catch (Exception ex) {
                            Notification.show("Erro ao guardar a foto: " + ex.getMessage(), 5000,
                                    Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                            return;
                        }
                        alunoAtual.setFotoChave(chave);
                        if (chaveAntiga != null && !chaveAntiga.isBlank()) {
                            try { storageService.apagar(chaveAntiga); } catch (Exception ignored) { }
                        }
                    }
                    alunoRepository.save(alunoAtual);
                    Notification.show("Guardado com sucesso!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    if (onSaveCallback != null) onSaveCallback.run();
                    dialog.close();
                } else {
                    Notification.show("Erro: Verifique os campos obrigatórios")
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

            Button cancelar = new Button("Sair", e -> dialog.close());
            dialog.getFooter().add(cancelar, guardar);
        }

        // 4. Atualiza o título e abre (Sempre fora do IF para refletir o estado atual)
        dialog.setHeaderTitle(aluno == null ? "Novo Registo" : "Editar Aluno");
        dialog.open();
    }

    private byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        is.transferTo(out);
        return out.toByteArray();
    }

    private String getImageSrc(byte[] img) {
        return img == null || img.length == 0 ? "" : "data:image/png;base64," + Base64.getEncoder().encodeToString(img);
    }

    public void abrirNovoFormulario(Aluno aluno) {
        abrir(null);
    }

    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
    }

    public void abrirFormulario(Aluno aluno) {
        abrir(aluno);
    }

    public void eliminarAluno(Aluno aluno) {
        alunoRepository.delete(aluno);
    }

    public void setAluno(Aluno aluno) {
        if (aluno == null)
            return;
        this.binder.setBean(aluno);
        System.out.println("A editar Aluno ID: " + aluno.getId());
        this.abrirFormulario(aluno);
    }
}
