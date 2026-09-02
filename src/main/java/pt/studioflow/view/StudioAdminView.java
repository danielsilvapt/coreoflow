package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.NumberField;
import pt.studioflow.model.CampoAluno;
import pt.studioflow.model.Idioma;
import pt.studioflow.model.Studio;
import pt.studioflow.model.StudioModulo;
import pt.studioflow.service.LogoUploadService;
import pt.studioflow.service.StudioService;
import pt.studioflow.view.component.ColorPickerField;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vista de administração de estúdios (SUPERADMIN only).
 */
@Route(value = "admin/studios", layout = MainLayout.class)
@PageTitle("Gestão de Estúdios | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class StudioAdminView extends VerticalLayout {

    private final StudioService studioService;
    private final LogoUploadService logoUploadService;
    private final Grid<Studio> grid = new Grid<>(Studio.class, false);

    public StudioAdminView(StudioService studioService, LogoUploadService logoUploadService) {
        this.studioService = studioService;
        this.logoUploadService = logoUploadService;
        setSizeFull();
        setPadding(true);

        H2 titulo = new H2("Gestão de Estúdios");

        Button btnNovo = new Button("Novo Estúdio", VaadinIcon.PLUS.create());
        btnNovo.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        btnNovo.addClickListener(e -> abrirFormulario(new Studio()));

        HorizontalLayout toolbar = new HorizontalLayout(titulo, btnNovo);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setJustifyContentMode(JustifyContentMode.BETWEEN);

        configurarGrid();
        add(toolbar, grid);
        carregarDados();
    }

    private void configurarGrid() {
        grid.addComponentColumn(studio -> {
            Button edit = new Button(VaadinIcon.EDIT.create());
            edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            edit.addClickListener(e -> abrirFormulario(studio));

            Button toggle = new Button(studio.isAtivo() ? VaadinIcon.PAUSE.create() : VaadinIcon.PLAY.create());
            toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            toggle.addClickListener(e -> {
                studio.setAtivo(!studio.isAtivo());
                studioService.save(studio);
                carregarDados();
            });

            return new HorizontalLayout(edit, toggle);
        }).setHeader("Ações").setWidth("120px").setFlexGrow(0);

        grid.addColumn(Studio::getId).setHeader("ID").setWidth("60px").setFlexGrow(0);

        grid.addComponentColumn(studio -> {
            if (studio.getLogoPath() != null && !studio.getLogoPath().isBlank()) {
                Image img = new Image(pt.studioflow.util.LogoUrl.comVersao(studio.getLogoPath()), studio.getNome());
                img.setHeight("32px");
                img.getStyle().set("object-fit", "contain");
                return img;
            }
            return new Span("—");
        }).setHeader("Logo").setWidth("80px").setFlexGrow(0);

        grid.addColumn(Studio::getNome).setHeader("Nome").setFlexGrow(2);
        grid.addColumn(Studio::getSlug).setHeader("Slug (login)").setFlexGrow(1);
        grid.addColumn(Studio::getEmailContacto).setHeader("Email").setFlexGrow(2);

        grid.addComponentColumn(studio -> {
            Span badge = new Span(studio.isAtivo() ? "Ativo" : "Inativo");
            badge.getStyle()
                    .set("background", studio.isAtivo() ? "#27AE60" : "#E74C3C")
                    .set("color", "white")
                    .set("padding", "2px 10px")
                    .set("border-radius", "12px")
                    .set("font-size", "12px");
            return badge;
        }).setHeader("Estado").setWidth("100px").setFlexGrow(0);

        grid.setSizeFull();
    }

    private void carregarDados() {
        grid.setItems(studioService.findAll());
    }

    private void abrirFormulario(Studio studio) {
        Dialog dialog = new Dialog();
        dialog.setWidth("720px");
        dialog.setMaxWidth("100%");
        dialog.setHeaderTitle(studio.getId() == null ? "Novo Estúdio" : "Editar: " + studio.getNome());

        // --- Logotipo ---
        H4 secLogo = new H4("Logotipo");
        secLogo.getStyle().set("margin", "8px 0 4px 0");

        Image logoPreview = new Image();
        logoPreview.setHeight("56px");
        logoPreview.getStyle().set("object-fit", "contain").set("display",
                (studio.getLogoPath() != null && !studio.getLogoPath().isBlank()) ? "block" : "none");
        if (studio.getLogoPath() != null && !studio.getLogoPath().isBlank()) {
            logoPreview.setSrc(pt.studioflow.util.LogoUrl.comVersao(studio.getLogoPath()));
            logoPreview.setAlt(studio.getNome());
        }

        // Guardar o logo path temporariamente durante o upload
        final String[] pendingLogoPath = { studio.getLogoPath() };

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("image/png", "image/jpeg", "image/jpg", "image/svg+xml");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(2 * 1024 * 1024); // 2MB
        upload.setUploadButton(new Button("Escolher imagem"));
        upload.setDropLabel(new Span("ou arrastar aqui (PNG, JPG, SVG • máx 2MB)"));

        String slugParaUpload = (studio.getSlug() != null && !studio.getSlug().isBlank())
                ? studio.getSlug() : "studio-" + System.currentTimeMillis();

        upload.addSucceededListener(event -> {
            try {
                String path = logoUploadService.saveLogo(slugParaUpload, buffer.getInputStream(), event.getMIMEType());
                pendingLogoPath[0] = path;
                logoPreview.setSrc(path + "?t=" + System.currentTimeMillis());
                logoPreview.getStyle().set("display", "block");
                Notification.show("Logo carregado — guarda o estúdio para confirmar")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Erro ao guardar logo: " + ex.getMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        upload.addFileRejectedListener(event ->
                Notification.show("Ficheiro rejeitado: " + event.getErrorMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR));

        VerticalLayout logoSection = new VerticalLayout(secLogo, logoPreview, upload);
        logoSection.setPadding(false);
        logoSection.setSpacing(false);
        logoSection.getStyle().set("gap", "8px");

        // --- Formulário principal ---
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        TextField nome = new TextField("Nome");
        nome.setValue(studio.getNome() != null ? studio.getNome() : "");
        nome.setRequired(true);

        TextField slug = new TextField("Slug (login)");
        slug.setValue(studio.getSlug() != null ? studio.getSlug() : "");
        slug.setHelperText("Ex: obidosdance — usado no formato slug:username para login");
        slug.setRequired(true);

        TextField email = new TextField("Email de Contacto");
        email.setValue(studio.getEmailContacto() != null ? studio.getEmailContacto() : "");

        ColorPickerField corPrimaria = new ColorPickerField("Cor Primária",
                studio.getCorPrimaria() != null ? studio.getCorPrimaria() : "#4A90E2");
        ColorPickerField corSecundaria = new ColorPickerField("Cor Secundária",
                studio.getCorSecundaria() != null ? studio.getCorSecundaria() : "#2D3436");

        TextField vendusApiKey = new TextField("Chave API Vendus");
        vendusApiKey.setValue(studio.getVendusApiKey() != null ? studio.getVendusApiKey() : "");

        TextField emailCriador = new TextField("Email Criador Transferências");
        emailCriador.setValue(studio.getEmailCriadorTransferencias() != null ? studio.getEmailCriadorTransferencias() : "");

        TextField emailAssinante1 = new TextField("Email Assinante 1");
        emailAssinante1.setValue(studio.getEmailAssinante1() != null ? studio.getEmailAssinante1() : "");

        TextField emailAssinante2 = new TextField("Email Assinante 2");
        emailAssinante2.setValue(studio.getEmailAssinante2() != null ? studio.getEmailAssinante2() : "");

        NumberField mensalidadeCrianca1x = new NumberField("Mensalidade Criança 1x/sem");
        mensalidadeCrianca1x.setValue(studio.getMensalidadeCrianca1x());

        NumberField mensalidadeCrianca2x = new NumberField("Mensalidade Criança 2x/sem");
        mensalidadeCrianca2x.setValue(studio.getMensalidadeCrianca2x());

        NumberField mensalidadeAdulto1x = new NumberField("Mensalidade Adulto 1x/sem");
        mensalidadeAdulto1x.setValue(studio.getMensalidadeAdulto1x());

        NumberField mensalidadeAdulto2x = new NumberField("Mensalidade Adulto 2x/sem");
        mensalidadeAdulto2x.setValue(studio.getMensalidadeAdulto2x());

        NumberField naoSocioAdicional = new NumberField("Adicional Não Sócio (€)");
        naoSocioAdicional.setValue(studio.getMensalidadeNaoSocioAdicional());

        NumberField descontoFamiliares = new NumberField("Desconto Familiares (€)");
        descontoFamiliares.setValue(studio.getDescontoFamiliaresEuros());

        NumberField descontoDirecao = new NumberField("Desconto Direção (%)");
        descontoDirecao.setValue(studio.getDescontoDirecaoPercentagem());

        NumberField descontoMaisModal = new NumberField("Desconto +Modalidades (%)");
        descontoMaisModal.setValue(studio.getDescontoMaisModalidadesPercentagem());

        NumberField descontoMais65 = new NumberField("Desconto +65 anos (%)");
        descontoMais65.setValue(studio.getDescontoMais65Percentagem());

        NumberField taxaInscricao = new NumberField("Taxa de Inscrição (€)");
        taxaInscricao.setValue(studio.getTaxaInscricao() != null ? studio.getTaxaInscricao() : 0.0);
        taxaInscricao.setMin(0);

        NumberField taxaRenovacao = new NumberField("Taxa de Renovação (€)");
        taxaRenovacao.setValue(studio.getTaxaRenovacao() != null ? studio.getTaxaRenovacao() : 0.0);
        taxaRenovacao.setMin(0);

        Checkbox ativo = new Checkbox("Estúdio ativo");
        ativo.setValue(studio.isAtivo());

        form.add(nome, slug, email, corPrimaria, corSecundaria, vendusApiKey, emailCriador,
                emailAssinante1, emailAssinante2,
                mensalidadeCrianca1x, mensalidadeCrianca2x,
                mensalidadeAdulto1x, mensalidadeAdulto2x, naoSocioAdicional,
                descontoFamiliares, descontoDirecao, descontoMaisModal, descontoMais65,
                taxaInscricao, taxaRenovacao,
                ativo);

        // --- Campos do aluno ---
        H4 secCampos = new H4("Campos do Formulário de Aluno");
        secCampos.getStyle().set("margin", "16px 0 4px 0");

        CheckboxGroup<CampoAluno> camposGroup = new CheckboxGroup<>();
        camposGroup.setLabel("Seleciona os campos opcionais visíveis no formulário de aluno");
        camposGroup.setItems(CampoAluno.values());
        camposGroup.setItemLabelGenerator(CampoAluno::getLabel);
        camposGroup.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        camposGroup.setWidthFull();

        Set<CampoAluno> camposSel;
        if (studio.getCamposAluno() == null || studio.getCamposAluno().isBlank()) {
            camposSel = new HashSet<>(Arrays.asList(CampoAluno.values()));
        } else {
            camposSel = Arrays.stream(studio.getCamposAluno().split(","))
                    .map(String::trim)
                    .filter(s -> { try { CampoAluno.valueOf(s); return true; } catch (Exception ex) { return false; } })
                    .map(CampoAluno::valueOf)
                    .collect(Collectors.toSet());
        }
        camposGroup.setValue(camposSel);

        // --- Faturação automática ---
        H4 secFat = new H4("Faturação");
        secFat.getStyle().set("margin", "16px 0 4px 0");
        Checkbox faturacaoAuto = new Checkbox("Emitir fatura Vendus automaticamente ao marcar mensalidade como Faturado");
        faturacaoAuto.setValue(studio.isFaturacaoAutomatica());
        faturacaoAuto.getStyle().set("font-size", "13px");

        // --- Remuneração de professores ---
        H4 secRemun = new H4("Remuneração de Professores");
        secRemun.getStyle().set("margin", "16px 0 4px 0");

        Span remunHint = new Span("Valores por defeito do estúdio. Cada professor pode sobrepô-los na página Professores.");
        remunHint.getStyle().set("font-size", "12px").set("color", "#888");

        ComboBox<String> tipoRemun = new ComboBox<>("Tipo de remuneração");
        tipoRemun.setItems("HORA", "PERCENTAGEM");
        tipoRemun.setItemLabelGenerator(t -> "HORA".equals(t) ? "Valor por hora (€/h)" : "Percentagem da mensalidade (%)");
        tipoRemun.setValue(studio.getTipoRemuneracaoProf() != null ? studio.getTipoRemuneracaoProf() : "HORA");
        tipoRemun.setWidthFull();

        // --- Grupo HORA (€/h por tipo de atividade) ---
        NumberField valorRemun = new NumberField("€/hora — aula regular");
        valorRemun.setValue(studio.getValorRemuneracaoProf() != null ? studio.getValorRemuneracaoProf() : 0.0);
        valorRemun.setMin(0);

        NumberField valorEnsaio = new NumberField("€/hora — ensaio");
        valorEnsaio.setValue(studio.getValorHoraEnsaioProf());
        valorEnsaio.setMin(0);
        valorEnsaio.setHelperText("Vazio = usa o valor da aula regular");

        NumberField valorPrivada = new NumberField("€/hora — privada / workshop");
        valorPrivada.setValue(studio.getValorHoraPrivadaProf());
        valorPrivada.setMin(0);
        valorPrivada.setHelperText("Vazio = usa o valor da aula regular");

        FormLayout grupoHora = new FormLayout(valorRemun, valorEnsaio, valorPrivada);
        grupoHora.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 3));
        grupoHora.setWidthFull();

        // --- Grupo PERCENTAGEM (% por frequência semanal) ---
        NumberField perc1x = new NumberField("% — aluno 1x/semana");
        perc1x.setValue(studio.getPercProf1x());
        perc1x.setMin(0);
        perc1x.setMax(100);

        NumberField perc2x = new NumberField("% — aluno 2x/semana");
        perc2x.setValue(studio.getPercProf2x());
        perc2x.setMin(0);
        perc2x.setMax(100);

        NumberField perc3x = new NumberField("% — aluno 3x/semana");
        perc3x.setValue(studio.getPercProf3x());
        perc3x.setMin(0);
        perc3x.setMax(100);

        NumberField percOutras = new NumberField("% — outras frequências");
        percOutras.setValue(studio.getPercProfOutras());
        percOutras.setMin(0);
        percOutras.setMax(100);
        percOutras.setHelperText("Usada quando a frequência do aluno não tem valor próprio. Ensaios/privadas são sempre pagos à hora.");

        FormLayout grupoPerc = new FormLayout(perc1x, perc2x, perc3x, percOutras);
        grupoPerc.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));
        grupoPerc.setWidthFull();

        Runnable ajustarVisibilidadeRemun = () -> {
            boolean hora = !"PERCENTAGEM".equals(tipoRemun.getValue());
            grupoHora.setVisible(hora);
            grupoPerc.setVisible(!hora);
        };
        tipoRemun.addValueChangeListener(e -> ajustarVisibilidadeRemun.run());
        ajustarVisibilidadeRemun.run();

        // --- Módulos ativos ---
        H4 secModulos = new H4("Módulos Ativos");
        secModulos.getStyle().set("margin", "12px 0 4px 0");

        CheckboxGroup<StudioModulo> modulosGroup = new CheckboxGroup<>();
        modulosGroup.setLabel("Seleciona os módulos visíveis para este estúdio");
        modulosGroup.setItems(StudioModulo.values());
        modulosGroup.setItemLabelGenerator(StudioModulo::getLabel);
        modulosGroup.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        modulosGroup.setWidthFull();

        // Pré-selecionar: se nulo/vazio = todos; senão, os que estão na string
        Set<StudioModulo> selecionados;
        if (studio.getModulosAtivos() == null || studio.getModulosAtivos().isBlank()) {
            selecionados = new HashSet<>(Arrays.asList(StudioModulo.values()));
        } else {
            selecionados = Arrays.stream(studio.getModulosAtivos().split(","))
                    .map(String::trim)
                    .filter(s -> {
                        try { StudioModulo.valueOf(s); return true; } catch (Exception ex) { return false; }
                    })
                    .map(StudioModulo::valueOf)
                    .collect(Collectors.toSet());
        }
        modulosGroup.setValue(selecionados);

        // --- Idiomas do formulário público ---
        H4 secIdiomas = new H4("Idiomas do Formulário Público");
        secIdiomas.getStyle().set("margin", "12px 0 4px 0");

        CheckboxGroup<Idioma> idiomasGroup = new CheckboxGroup<>();
        idiomasGroup.setLabel("Idiomas disponíveis na inscrição/renovação online");
        idiomasGroup.setItems(Idioma.values());
        idiomasGroup.setItemLabelGenerator(Idioma::getLabel);
        idiomasGroup.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        idiomasGroup.setWidthFull();
        idiomasGroup.setValue(new HashSet<>(studio.getIdiomasDisponiveisList()));

        VerticalLayout content = new VerticalLayout(
                logoSection, form,
                secCampos, camposGroup,
                secFat, faturacaoAuto,
                secRemun, remunHint, tipoRemun, grupoHora, grupoPerc,
                secModulos, modulosGroup,
                secIdiomas, idiomasGroup);
        content.setPadding(false);
        content.setSpacing(true);

        Button salvar = new Button("Guardar", e -> {
            if (nome.getValue().isBlank() || slug.getValue().isBlank()) {
                Notification.show("Nome e Slug são obrigatórios.").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            studio.setNome(nome.getValue().trim());
            studio.setSlug(slug.getValue().trim().toLowerCase());
            studio.setEmailContacto(email.getValue().trim());
            studio.setCorPrimaria(corPrimaria.getValue());
            studio.setCorSecundaria(corSecundaria.getValue());
            studio.setLogoPath(pendingLogoPath[0]);
            studio.setVendusApiKey(vendusApiKey.getValue().trim());
            studio.setEmailCriadorTransferencias(emailCriador.getValue().trim());
            studio.setEmailAssinante1(emailAssinante1.getValue().trim());
            studio.setEmailAssinante2(emailAssinante2.getValue().trim());
            studio.setMensalidadeCrianca1x(mensalidadeCrianca1x.getValue());
            studio.setMensalidadeCrianca2x(mensalidadeCrianca2x.getValue());
            studio.setMensalidadeAdulto1x(mensalidadeAdulto1x.getValue());
            studio.setMensalidadeAdulto2x(mensalidadeAdulto2x.getValue());
            studio.setMensalidadeNaoSocioAdicional(naoSocioAdicional.getValue());
            studio.setDescontoFamiliaresEuros(descontoFamiliares.getValue());
            studio.setDescontoDirecaoPercentagem(descontoDirecao.getValue());
            studio.setDescontoMaisModalidadesPercentagem(descontoMaisModal.getValue());
            studio.setDescontoMais65Percentagem(descontoMais65.getValue());
            studio.setTaxaInscricao(taxaInscricao.getValue());
            studio.setTaxaRenovacao(taxaRenovacao.getValue());
            studio.setAtivo(ativo.getValue());

            // Faturação
            studio.setFaturacaoAutomatica(faturacaoAuto.getValue() ? true : false);

            // Campos do aluno
            studio.setCamposAluno(camposGroup.getValue().stream()
                    .map(CampoAluno::name).collect(Collectors.joining(",")));

            // Remuneração de professores (valores por defeito do estúdio)
            studio.setTipoRemuneracaoProf(tipoRemun.getValue());
            studio.setValorRemuneracaoProf(valorRemun.getValue());
            studio.setValorHoraEnsaioProf(valorEnsaio.getValue());
            studio.setValorHoraPrivadaProf(valorPrivada.getValue());
            studio.setPercProf1x(perc1x.getValue());
            studio.setPercProf2x(perc2x.getValue());
            studio.setPercProf3x(perc3x.getValue());
            studio.setPercProfOutras(percOutras.getValue());

            // Módulos
            String modulosStr = modulosGroup.getValue().stream()
                    .map(StudioModulo::name).collect(Collectors.joining(","));
            studio.setModulosAtivos(modulosStr);

            // Idiomas
            studio.setIdiomasDisponiveis(idiomasGroup.getValue().stream()
                    .map(Idioma::name).collect(Collectors.joining(",")));

            studioService.save(studio);
            Notification.show("Estúdio guardado!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            carregarDados();
        });
        salvar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), salvar);
        dialog.open();
    }
}
