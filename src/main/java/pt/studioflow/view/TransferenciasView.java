package pt.studioflow.view;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;


import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinServletRequest;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Transferencia;
import pt.studioflow.model.User;
import pt.studioflow.repository.TransferenciaRepository;
import pt.studioflow.repository.UserRepository;
import pt.studioflow.service.EmailService;

@PageTitle("Transferências | CoreoFlow")
@Route(value = "financeiro-transferencias", layout = MainLayout.class)
@RolesAllowed({ "ADMIN", "PROF" })
public class TransferenciasView extends VerticalLayout {

    private final TransferenciaRepository repo;
    private final UserRepository userRepository;
    private final EmailService emailService;


    private Grid<Transferencia> grid;
    private String userEmailLogado = ""; // Vai guardar o email descoberto na DB
    private String usernameLogado;
    private HorizontalLayout cardsLayout;

    public TransferenciasView(TransferenciaRepository repo, UserRepository userRepository, EmailService emailService) {
        this.repo = repo;
        this.userRepository = userRepository;
        this.emailService = emailService;

        // 1. Recuperar o código de utilizador (username) do Spring Security
        this.usernameLogado = VaadinServletRequest.getCurrent().getHttpServletRequest().getUserPrincipal().getName();

        // 2. Procurar na tabela de "users" o e-mail real associado a este código
        userRepository.findByUsername(usernameLogado).ifPresent(user -> {
            this.userEmailLogado = user.getEmail();
        });

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        Div cssInjetado = new Div();
        cssInjetado.getElement().setProperty("innerHTML", "<style>"
                + ".kpi-card { transition: transform 0.2s ease, box-shadow 0.2s ease; }"
                + ".kpi-card:hover { transform: translateY(-3px); box-shadow: 0 6px 15px rgba(0,0,0,0.08) !important; }"
                + "</style>");
        add(cssInjetado);

        H2 titulo = new H2("Controlo de Transferências Bancárias");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        Button btnNova = ViewUtils.botaoNovo("Registar Transferência", e -> abrirDialogNovaTransferencia());
        btnNova.setVisible(userEmailLogado.equalsIgnoreCase(TenantContext.getCurrentStudio().getEmailCriadorTransferencias()) || usernameLogado.contains("admin") || usernameLogado.contains("raquelbranco") || usernameLogado.contains("verafortes") || usernameLogado.contains("danielsilva"));

        add(ViewUtils.toolbar(btnNova));

        cardsLayout = new HorizontalLayout();
        cardsLayout.setWidthFull();
        cardsLayout.getStyle().set("gap", "16px").set("flex-wrap", "wrap");
        add(cardsLayout);

        configurarGrid();
        atualizarTudo();
    }


    private void atualizarTudo() {
        cardsLayout.removeAll();
        long totalAguardaAprovacao = repo.findAll().stream()
                .filter(t -> "AGUARDA_ASSINATURAS".equals(t.getEstado())
                        || "AGUARDA_UMA_ASSINATURA".equals(t.getEstado()))
                .count();
        long totalAguardaPagamento = repo.findAll().stream().filter(t -> "AGUARDA_PAGAMENTO".equals(t.getEstado()))
                .count();

        cardsLayout.add(
                criarCardKpi("PENDENTES DE ASSINATURA", String.valueOf(totalAguardaAprovacao), "#df9a00", "#fffbeb"));
        cardsLayout.add(criarCardKpi("PRONTOS A PAGAR", String.valueOf(totalAguardaPagamento), "#2b6cb0", "#ebf8ff"));

        grid.setItems(repo.findAll());
    }

    private Div criarCardKpi(String labelText, String valorText, String corTexto, String corFundo) {
        Div card = new Div();
        card.addClassName("kpi-card");
        card.getStyle()
                .set("flex", "1 1 200px")
                .set("background-color", corFundo)
                .set("padding", "16px")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 5px rgba(0,0,0,0.03)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center");

        Span lbl = new Span(labelText);
        lbl.getStyle().set("font-size", "0.75rem").set("font-weight", "700").set("color", "#718096");
        Span val = new Span(valorText);
        val.getStyle().set("font-size", "1.8rem").set("font-weight", "800").set("color", corTexto);

        card.add(lbl, val);
        return card;
    }

    private void configurarGrid() {
        grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_ROW_STRIPES);
        grid.getStyle().set("border-radius", "12px").set("box-shadow", "0 4px 12px rgba(0,0,0,0.04)");
        grid.setSizeFull();

        grid.addColumn(t -> t.getUrgencia()).setHeader("Prioridade").setAutoWidth(true);
        grid.addColumn(t -> t.getCategoria()).setHeader("Categoria").setAutoWidth(true);
        grid.addColumn(t -> t.getDescricao()).setHeader("Descrição / Beneficiário").setAutoWidth(true);
        grid.addColumn(t -> String.format("%.2f €", t.getValor())).setHeader("Montante").setAutoWidth(true);
        grid.addColumn(t -> t.getIban()).setHeader("IBAN").setAutoWidth(true);

        grid.addComponentColumn(t -> {
            String textoEstado = t.getEstado().replace("_", " ");
            if ("AGUARDA_ASSINATURAS".equals(t.getEstado())) {
                textoEstado = "❌ SEM ASSINATURAS";
            } else if ("AGUARDA_UMA_ASSINATURA".equals(t.getEstado())) {
                String quemFalta = (t.getAssinadoPor1() == null) ? "Daniel" : "Vera";
                textoEstado = "⏱️ FALTA ASSINAR: " + quemFalta;
            } else if ("AGUARDA_PAGAMENTO".equals(t.getEstado())) {
                textoEstado = "✅ AUTORIZADO";
            }

            Span badge = new Span(textoEstado);
            badge.getStyle().set("padding", "4px 8px").set("border-radius", "20px").set("font-size", "0.7rem")
                    .set("font-weight", "700");

            if ("AGUARDA_ASSINATURAS".equals(t.getEstado()) || "AGUARDA_UMA_ASSINATURA".equals(t.getEstado())) {
                badge.getStyle().set("background-color", "#feebc8").set("color", "#c05621");
            } else if ("AGUARDA_PAGAMENTO".equals(t.getEstado())) {
                badge.getStyle().set("background-color", "#bee3f8").set("color", "#2b6cb0");
            } else if ("PAGO".equals(t.getEstado())) {
                badge.getStyle().set("background-color", "#c6f6d5").set("color", "#22543d");
            } else {
                badge.getStyle().set("background-color", "#fed7d7").set("color", "#9b2c2c");
            }
            return badge;
        }).setHeader("Estado").setAutoWidth(true);

        grid.addComponentColumn(t -> {
            if (t.getNomeFicheiroComprovativo() != null && !t.getNomeFicheiroComprovativo().isBlank()) {
                Button btnVer = new Button(new Icon(VaadinIcon.EYE));
                btnVer.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                btnVer.getElement().setProperty("title", "Ver Comprovativo");
                btnVer.addClickListener(e -> abrirDialogVerComprovativo(t));
                return btnVer;
            }
            return new Span("-");
        }).setHeader("Comprovativo").setAutoWidth(true);

        grid.addComponentColumn(t -> {
            HorizontalLayout acoes = new HorizontalLayout();
            acoes.getStyle().set("flex-wrap", "wrap").set("gap", "4px");

            boolean podeAssinar = "AGUARDA_ASSINATURAS".equals(t.getEstado())
                    || "AGUARDA_UMA_ASSINATURA".equals(t.getEstado());

            if (podeAssinar && !userEmailLogado.isBlank()) {
                if (userEmailLogado.equalsIgnoreCase(TenantContext.getCurrentStudio().getEmailAssinante1()) && t.getAssinadoPor1() == null) {
                    Button b = new Button("Marcar como assinado", e -> processarCorridaAssinatura(t, 1));
                    b.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
                    acoes.add(b);
                }
                if (userEmailLogado.equalsIgnoreCase(TenantContext.getCurrentStudio().getEmailAssinante2()) && t.getAssinadoPor2() == null) {
                    Button b = new Button("Marcar como assinado", e -> processarCorridaAssinatura(t, 2));
                    b.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
                    acoes.add(b);
                }
            }

            if ("AGUARDA_PAGAMENTO".equals(t.getEstado())
                    && (userEmailLogado.equalsIgnoreCase(TenantContext.getCurrentStudio().getEmailCriadorTransferencias()) || usernameLogado.contains("admin"))) {
                Button bComp = new Button("Anexar Comprovativo", e -> abrirDialogAnexarComprovativo(t));
                bComp.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                acoes.add(bComp);
            }

            return acoes;
        }).setHeader("Ações Disponíveis").setAutoWidth(true);

        add(grid);
    }

    private void processarCorridaAssinatura(Transferencia t, int assinanteNum) {
        if (assinanteNum == 1) {
            t.setAssinadoPor1(userEmailLogado);
        } else if (assinanteNum == 2) {
            t.setAssinadoPor2(userEmailLogado);
        }

        if (t.getAssinadoPor1() != null && t.getAssinadoPor2() != null) {
            t.setEstado("AGUARDA_PAGAMENTO");
            repo.save(t);
            emailService.notificarGeralProntoParaPagamento(t.getDescricao(), t.getValor().toString(), TenantContext.getCurrentStudio().getEmailCriadorTransferencias());
            Notification.show("Circuito concluído! Autorização de tesouraria enviada para o Geral.");
        } else {
            t.setEstado("AGUARDA_UMA_ASSINATURA");
            repo.save(t);

            String emailFalta = (assinanteNum == 1) ? TenantContext.getCurrentStudio().getEmailAssinante2() : TenantContext.getCurrentStudio().getEmailAssinante1();
            String quemJaAssinou = (assinanteNum == 1) ? "Daniel Silva" : "Vera Fortes";

            emailService.notificarSegundoAssinanteFaltaUma(t.getDescricao(), t.getValor().toString(), emailFalta,
                    quemJaAssinou);
            Notification.show("Assinatura guardada. O outro validador foi alertado por email.");
        }

        atualizarTudo();
    }

    private void abrirDialogNovaTransferencia() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Inserir Registo de Lançamento Bancário");
        dialog.setWidth("460px");
        dialog.setMaxWidth("100%");

        ComboBox<String> urgencia = new ComboBox<>("Nível de Urgência", "Normal", "Urgente");
        urgencia.setValue("Normal");
        urgencia.setWidthFull();

        ComboBox<String> categoria = new ComboBox<>("Categoria", "Honorários", "Despesas Administrativas", "Eventos",
                "Outras");
        categoria.setWidthFull();

        TextField desc = new TextField("Beneficiário / Descrição");
        desc.setWidthFull();

        BigDecimalField valor = new BigDecimalField("Valor (€)");
        valor.setWidthFull();

        TextField iban = new TextField("IBAN Destinatário");
        iban.setWidthFull();

        VerticalLayout form = new VerticalLayout(urgencia, categoria, desc, valor, iban);
        form.setPadding(false);
        dialog.add(form);

        Button btnSalvar = new Button("Submeter para Validação", e -> {
            if (categoria.isEmpty() || desc.isEmpty() || valor.isEmpty() || iban.isEmpty()) {
                Notification.show("Por favor, preencha todos os campos obrigatórios.");
                return;
            }

            Transferencia t = new Transferencia();
            t.setUrgencia(urgencia.getValue());
            t.setCategoria(categoria.getValue());
            t.setDescricao(desc.getValue());
            t.setValor(valor.getValue());
            t.setIban(iban.getValue());
            t.setEstado("AGUARDA_ASSINATURAS");
            t.setCriadoPor(usernameLogado);
            t.setDataCriacao(LocalDateTime.now());

            repo.save(t);

            emailService.notificarAssinantesNovaTransferencia(t.getDescricao(), t.getValor().toString(), t.getIban(),
                    TenantContext.getCurrentStudio().getEmailAssinante1(), TenantContext.getCurrentStudio().getEmailAssinante2());

            dialog.close();
            atualizarTudo();
            Notification.show("Lançamento registado e alertas de e-mail disparados.");
        });
        btnSalvar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(new Button("Voltar", e -> dialog.close()), btnSalvar);
        dialog.open();
    }

    // CORREÇÃO: Escrita física do ficheiro em disco para evitar PDFs
    // vazios/corrompidos
    private void abrirDialogAnexarComprovativo(Transferencia t) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Finalizar Liquidação (Upload do Comprovativo)");
        dialog.setWidth("400px");
        dialog.setMaxWidth("100%");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("application/pdf", "image/jpeg", "image/png");
        upload.setMaxFiles(1);

        VerticalLayout vl = new VerticalLayout(new Span("Associe o documento oficial emitido pelo banco:"), upload);
        vl.setPadding(false);
        dialog.add(vl);

        Button btnFinalizar = new Button("Concluir Processo", e -> {
            if (buffer.getFileName().isEmpty()) {
                Notification.show("O upload do ficheiro é obrigatório.");
                return;
            }

            // Bloco de persistência física no disco
            try {
                File dir = new File("uploads");
                if (!dir.exists()) {
                    dir.mkdirs(); // Cria a pasta "uploads" na raíz do projeto caso não exista
                }

                // Nome único baseado no timestamp para evitar colisões de ficheiros com o mesmo
                // nome
                String nomeFicheiroUnico = System.currentTimeMillis() + "_" + buffer.getFileName();
                File targetFile = new File(dir, nomeFicheiroUnico);

                // Copiar os bytes da memória temporária para o ficheiro físico
                try (InputStream is = buffer.getInputStream();
                        FileOutputStream os = new FileOutputStream(targetFile)) {
                    byte[] bBuffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = is.read(bBuffer)) != -1) {
                        os.write(bBuffer, 0, bytesRead);
                    }
                }

                t.setNomeFicheiroComprovativo(nomeFicheiroUnico);
                t.setEstado("PAGO");
                repo.save(t);

                emailService.notificarEnvolvidosPagamentoEfetuado(t.getDescricao(), t.getValor().toString(),
                        TenantContext.getCurrentStudio().getEmailCriadorTransferencias(),
                        TenantContext.getCurrentStudio().getEmailAssinante1(), TenantContext.getCurrentStudio().getEmailAssinante2());

                dialog.close();
                atualizarTudo();
                Notification.show("Liquidação concluída e ficheiro guardado com sucesso!");

            } catch (IOException ex) {
                Notification.show("Erro crítico do sistema ao gravar o documento no disco.");
                ex.printStackTrace();
            }
        });
        btnFinalizar.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        dialog.getFooter().add(new Button("Voltar", e -> dialog.close()), btnFinalizar);
        dialog.open();
    }

    private void abrirDialogVerComprovativo(Transferencia t) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Visualizar Documento de Liquidação");
        dialog.setWidth("380px");
        dialog.setMaxWidth("100%");

        VerticalLayout layout = new VerticalLayout();
        layout.setSpacing(true);
        layout.setPadding(true);

        // Limpar o prefixo de timestamp para exibir um nome amigável ao utilizador
        String nomeExibicao = t.getNomeFicheiroComprovativo()
                .substring(t.getNomeFicheiroComprovativo().indexOf("_") + 1);
        Span info = new Span("Ficheiro anexado: " + nomeExibicao);
        info.getStyle().set("font-size", "0.9rem").set("color", "#4a5568");

        StreamResource resource = new StreamResource(nomeExibicao, () -> {
            try {
                File file = new File("uploads/" + t.getNomeFicheiroComprovativo());
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                Notification.show("Erro: O ficheiro físico já não se encontra no servidor.");
                return null;
            }
        });

        Anchor link = new Anchor(resource, "Abrir Comprovativo ↗");
        link.setTarget("_blank");
        link.getStyle()
                .set("display", "inline-block")
                .set("background-color", "#3182ce")
                .set("color", "white")
                .set("padding", "10px 16px")
                .set("border-radius", "6px")
                .set("font-weight", "600")
                .set("text-decoration", "none")
                .set("text-align", "center")
                .set("width", "100%");

        layout.add(info, link);
        dialog.add(layout);

        Button btnFechar = new Button("Fechar", e -> dialog.close());
        btnFechar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(btnFechar);

        dialog.open();
    }
}