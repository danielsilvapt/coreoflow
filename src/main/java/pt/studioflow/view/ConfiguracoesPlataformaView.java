package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.ConfiguracaoPlataforma;
import pt.studioflow.service.GroqService;
import pt.studioflow.service.SuporteService;

@Route(value = "admin/configuracoes", layout = MainLayout.class)
@PageTitle("Configurações da Plataforma | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class ConfiguracoesPlataformaView extends VerticalLayout {

    private final SuporteService suporteService;
    private final GroqService groqService;

    public ConfiguracoesPlataformaView(SuporteService suporteService, GroqService groqService) {
        this.suporteService = suporteService;
        this.groqService = groqService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Configurações da Plataforma");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        ConfiguracaoPlataforma c = suporteService.getConfig();

        add(seccao("Suporte",
                campoSuporte(c)));
        add(seccao("Aviso global",
                campoAviso(c)));
        add(seccao("Emails da plataforma",
                campoEmails(c)));
        add(seccao("Onboarding",
                campoOnboarding(c)));
        add(seccao("Treinador de Dança IA",
                campoIa(c)));

        Button guardar = new Button("Guardar tudo", VaadinIcon.CHECK.create(), e -> {
            aplicarSuporte(c);
            aplicarAviso(c);
            aplicarEmails(c);
            aplicarOnboarding(c);
            aplicarIa(c);
            suporteService.guardarConfig(c);
            Notification.show("Configurações guardadas.").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(guardar);
    }

    // ---- Suporte ----
    private EmailField emailSuporte;

    private VerticalLayout campoSuporte(ConfiguracaoPlataforma c) {
        emailSuporte = new EmailField("Email de suporte");
        emailSuporte.setValue(nvl(c.getEmailSuporte()));
        emailSuporte.setWidthFull();
        emailSuporte.setHelperText("Para onde vão os pedidos submetidos pelos utilizadores. "
                + "São sempre gravados em BD mesmo que o email falhe. Geridos em Suporte.");
        return grupo(emailSuporte);
    }

    private void aplicarSuporte(ConfiguracaoPlataforma c) {
        c.setEmailSuporte(trimOrNull(emailSuporte.getValue()));
    }

    // ---- Aviso global ----
    private Checkbox avisoAtivo;
    private TextArea aviso;
    private ComboBox<String> avisoModo;

    private VerticalLayout campoAviso(ConfiguracaoPlataforma c) {
        avisoAtivo = new Checkbox("Mostrar aviso global a todos os utilizadores");
        avisoAtivo.setValue(c.isAvisoGlobalAtivo());
        aviso = new TextArea("Texto do aviso");
        aviso.setValue(nvl(c.getAvisoGlobal()));
        aviso.setWidthFull();
        aviso.setHelperText("Ex.: manutenção agendada, nova funcionalidade, etc.");
        avisoModo = new ComboBox<>("Como mostrar");
        avisoModo.setItems("Faixa no topo", "Popup ao entrar", "Ambos");
        avisoModo.setValue(switch (c.getAvisoGlobalModo()) {
            case "POPUP" -> "Popup ao entrar";
            case "AMBOS" -> "Ambos";
            default -> "Faixa no topo";
        });
        avisoModo.setWidth("220px");
        return grupo(avisoAtivo, aviso, avisoModo);
    }

    private void aplicarAviso(ConfiguracaoPlataforma c) {
        c.setAvisoGlobalAtivo(avisoAtivo.getValue());
        c.setAvisoGlobal(aviso.getValue());
        c.setAvisoGlobalModo(switch (avisoModo.getValue() == null ? "" : avisoModo.getValue()) {
            case "Popup ao entrar" -> "POPUP";
            case "Ambos" -> "AMBOS";
            default -> "BANNER";
        });
    }

    // ---- Emails ----
    private TextField nomeRemetente;
    private EmailField bccSuporte;

    private VerticalLayout campoEmails(ConfiguracaoPlataforma c) {
        nomeRemetente = new TextField("Nome do remetente");
        nomeRemetente.setValue(nvl(c.getNomeRemetenteEmails()));
        nomeRemetente.setHelperText("Aparece como remetente nas respostas de suporte enviadas ao cliente.");
        nomeRemetente.setWidth("320px");
        bccSuporte = new EmailField("Email em cópia (BCC) do suporte");
        bccSuporte.setValue(nvl(c.getEmailBccSuporte()));
        bccSuporte.setHelperText("Opcional. Recebe cópia de todos os pedidos e respostas de suporte.");
        bccSuporte.setWidthFull();
        return grupo(nomeRemetente, bccSuporte);
    }

    private void aplicarEmails(ConfiguracaoPlataforma c) {
        c.setNomeRemetenteEmails(trimOrNull(nomeRemetente.getValue()));
        c.setEmailBccSuporte(trimOrNull(bccSuporte.getValue()));
    }

    // ---- Onboarding ----
    private Checkbox novosEstudios;

    private VerticalLayout campoOnboarding(ConfiguracaoPlataforma c) {
        novosEstudios = new Checkbox("Permitir criação de novos estúdios");
        novosEstudios.setValue(c.isPermitirNovosEstudios());
        return grupo(novosEstudios);
    }

    private void aplicarOnboarding(ConfiguracaoPlataforma c) {
        c.setPermitirNovosEstudios(novosEstudios.getValue());
    }

    // ---- Treinador de Dança IA ----
    private Checkbox iaAtiva;
    private com.vaadin.flow.component.textfield.PasswordField groqKey;
    private ComboBox<String> groqModelo;
    private com.vaadin.flow.component.textfield.PasswordField youtubeKey;

    private VerticalLayout campoIa(ConfiguracaoPlataforma c) {
        iaAtiva = new Checkbox("Ativar o Treinador IA (depois escolhe-se por estúdio em Estúdios → módulos)");
        iaAtiva.setValue(c.isTreinadorIaAtivo());

        groqKey = new com.vaadin.flow.component.textfield.PasswordField("Chave da API GROQ");
        groqKey.setValue(nvl(c.getGroqApiKey()));
        groqKey.setWidthFull();
        groqKey.setHelperText("console.groq.com → API Keys. Necessária para gerar planos e para o chat.");

        groqModelo = new ComboBox<>("Modelo GROQ");
        groqModelo.setAllowCustomValue(true);
        groqModelo.setItems("llama-3.1-8b-instant", "llama-3.3-70b-versatile",
                "meta-llama/llama-4-scout-17b-16e-instruct", "openai/gpt-oss-20b", "openai/gpt-oss-120b");
        groqModelo.addCustomValueSetListener(e -> groqModelo.setValue(e.getDetail()));
        groqModelo.setValue(nvl(c.getGroqModelo()));
        groqModelo.setWidth("360px");
        groqModelo.setHelperText("Usa \"Ver modelos da chave\" para confirmar quais funcionam.");

        Button verModelos = new Button("Ver modelos da chave", VaadinIcon.LIST.create(), e -> {
            try {
                java.util.List<String> ms = groqService.listarModelos();
                groqModelo.setItems(ms);
                Notification.show("Modelos disponíveis: " + String.join(", ", ms), 8000,
                        Notification.Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 7000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        verModelos.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button testar = new Button("Guardar e testar", VaadinIcon.MAGIC.create(), e -> {
            try {
                aplicarIa(c);
                suporteService.guardarConfig(c);
                String r = groqService.perguntar("Responde apenas com a palavra OK.", "Diz OK.");
                Notification.show("GROQ (" + c.getGroqModelo() + ") respondeu: " + r)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 8000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        testar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        youtubeKey = new com.vaadin.flow.component.textfield.PasswordField("Chave da API YouTube Data v3 (opcional)");
        youtubeKey.setValue(nvl(c.getYoutubeApiKey()));
        youtubeKey.setWidthFull();
        youtubeKey.setHelperText("Google Cloud Console → APIs → ativar \"YouTube Data API v3\" → Credenciais → "
                + "Chave de API. Sem ela, os planos são criados na mesma mas sem vídeos sugeridos.");

        com.vaadin.flow.component.orderedlayout.HorizontalLayout botoes =
                new com.vaadin.flow.component.orderedlayout.HorizontalLayout(groqModelo, verModelos, testar);
        botoes.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.END);
        botoes.getStyle().set("flex-wrap", "wrap");

        return grupo(iaAtiva, groqKey, botoes, youtubeKey);
    }

    private void aplicarIa(ConfiguracaoPlataforma c) {
        c.setTreinadorIaAtivo(iaAtiva.getValue());
        c.setGroqApiKey(trimOrNull(groqKey.getValue()));
        c.setGroqModelo(trimOrNull(groqModelo.getValue()));
        c.setYoutubeApiKey(trimOrNull(youtubeKey.getValue()));
    }

    // ---- helpers ----
    private VerticalLayout seccao(String titulo, com.vaadin.flow.component.Component conteudo) {
        H3 h = new H3(titulo);
        h.getStyle().set("margin", "0 0 6px 0").set("font-size", "15px");
        VerticalLayout card = new VerticalLayout(h, conteudo);
        card.setPadding(true);
        card.setSpacing(false);
        card.setWidthFull();
        card.getStyle().set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.07)");
        return card;
    }

    private VerticalLayout grupo(com.vaadin.flow.component.Component... comps) {
        VerticalLayout v = new VerticalLayout(comps);
        v.setPadding(false);
        v.setSpacing(true);
        return v;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private static String trimOrNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
