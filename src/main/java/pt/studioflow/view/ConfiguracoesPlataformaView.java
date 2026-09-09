package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.ConfiguracaoPlataforma;
import pt.studioflow.model.PedidoSuporte;
import pt.studioflow.repository.PedidoSuporteRepository;
import pt.studioflow.service.SuporteService;
import pt.studioflow.util.DataUtil;

@Route(value = "admin/configuracoes", layout = MainLayout.class)
@PageTitle("Configurações da Plataforma | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class ConfiguracoesPlataformaView extends VerticalLayout {

    private final SuporteService suporteService;
    private final PedidoSuporteRepository pedidoRepo;

    public ConfiguracoesPlataformaView(SuporteService suporteService, PedidoSuporteRepository pedidoRepo) {
        this.suporteService = suporteService;
        this.pedidoRepo = pedidoRepo;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Configurações da Plataforma");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        add(criarFormConfig());
        add(new H3("Pedidos de suporte"));
        add(criarGridPedidos());
    }

    private VerticalLayout criarFormConfig() {
        ConfiguracaoPlataforma c = suporteService.getConfig();

        EmailField emailSuporte = new EmailField("Email de suporte");
        emailSuporte.setValue(c.getEmailSuporte() != null ? c.getEmailSuporte() : "");
        emailSuporte.setWidthFull();
        emailSuporte.setHelperText("Para onde vão os pedidos de suporte submetidos pelos utilizadores. "
                + "Os pedidos são sempre gravados mesmo que o email falhe.");

        Checkbox avisoAtivo = new Checkbox("Mostrar aviso global a todos os utilizadores");
        avisoAtivo.setValue(c.isAvisoGlobalAtivo());
        TextArea aviso = new TextArea("Texto do aviso global");
        aviso.setValue(c.getAvisoGlobal() != null ? c.getAvisoGlobal() : "");
        aviso.setWidthFull();
        aviso.setHelperText("Ex.: manutenção agendada, nova funcionalidade, etc.");

        Checkbox novosEstudios = new Checkbox("Permitir criação de novos estúdios");
        novosEstudios.setValue(c.isPermitirNovosEstudios());

        Button guardar = new Button("Guardar", VaadinIcon.CHECK.create(), e -> {
            c.setEmailSuporte(emailSuporte.getValue() != null ? emailSuporte.getValue().trim() : null);
            c.setAvisoGlobalAtivo(avisoAtivo.getValue());
            c.setAvisoGlobal(aviso.getValue());
            c.setPermitirNovosEstudios(novosEstudios.getValue());
            suporteService.guardarConfig(c);
            Notification.show("Configurações guardadas.").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout card = new VerticalLayout(emailSuporte, avisoAtivo, aviso, novosEstudios, guardar);
        card.setPadding(true);
        card.setSpacing(true);
        card.setMaxWidth("640px");
        card.getStyle().set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.07)");
        return card;
    }

    private Grid<PedidoSuporte> criarGridPedidos() {
        Grid<PedidoSuporte> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setItems(pedidoRepo.findAllByOrderByDataCriacaoDesc());
        grid.setHeight("420px");

        grid.addColumn(p -> p.getDataCriacao() != null ? DataUtil.formatar(p.getDataCriacao()) : "")
                .setHeader("Data").setAutoWidth(true);
        grid.addColumn(PedidoSuporte::getStudio).setHeader("Estúdio").setAutoWidth(true);
        grid.addColumn(PedidoSuporte::getUtilizador).setHeader("Utilizador").setAutoWidth(true);
        grid.addColumn(PedidoSuporte::getTipo).setHeader("Tipo").setAutoWidth(true);
        grid.addColumn(PedidoSuporte::getAssunto).setHeader("Assunto").setFlexGrow(1);
        grid.addComponentColumn(p -> {
            Span s = new Span(p.isEmailEnviado() ? "✉️ enviado" : "⚠️ só BD");
            s.getStyle().set("font-size", "12px").set("color", p.isEmailEnviado() ? "#27AE60" : "#E67E22");
            return s;
        }).setHeader("Email").setAutoWidth(true);
        grid.addComponentColumn(p -> {
            Checkbox resolvido = new Checkbox(p.isResolvido());
            resolvido.addValueChangeListener(e -> {
                p.setResolvido(e.getValue());
                pedidoRepo.save(p);
            });
            return resolvido;
        }).setHeader("Resolvido").setAutoWidth(true);

        grid.setItemDetailsRenderer(new com.vaadin.flow.data.renderer.ComponentRenderer<>(p -> {
            Span d = new Span(p.getDescricao());
            d.getStyle().set("white-space", "pre-wrap").set("padding", "8px 12px").set("display", "block");
            return d;
        }));
        return grid;
    }
}
