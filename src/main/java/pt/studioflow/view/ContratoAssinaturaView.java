package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.ContratoDigital;
import pt.studioflow.repository.ContratoDigitalRepository;

import java.time.LocalDateTime;

@Route(value = "contrato", layout = MainLayout.class)
@PageTitle("Assinar Contrato | CoreoFlow")
@RolesAllowed({"ALUNO", "ADMIN"})
public class ContratoAssinaturaView extends VerticalLayout implements HasUrlParameter<Long> {

    private final ContratoDigitalRepository contratoRepo;

    public ContratoAssinaturaView(ContratoDigitalRepository contratoRepo) {
        this.contratoRepo = contratoRepo;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void setParameter(BeforeEvent event, Long id) {
        removeAll();
        if (id == null) { add(new Span("Contrato não encontrado.")); return; }

        contratoRepo.findById(id).ifPresentOrElse(this::mostrarContrato,
                () -> add(new Span("Contrato não encontrado.")));
    }

    private void mostrarContrato(ContratoDigital c) {
        boolean jaAssinado = "ASSINADO".equals(c.getEstado());

        H2 titulo = new H2("Contrato de " + c.getTipo() + " · " + c.getAnoLetivo());
        titulo.getStyle().set("margin-top", "0");

        com.vaadin.flow.component.orderedlayout.Scroller scroller =
                new com.vaadin.flow.component.orderedlayout.Scroller(
                        new Html("<div style='padding:16px;font-family:sans-serif;line-height:1.6;background:white;"
                                + "border-radius:12px;box-shadow:0 2px 8px rgba(0,0,0,0.07)'>"
                                + c.getConteudo() + "</div>"));
        scroller.setWidth("100%");
        scroller.setHeight("420px");

        add(titulo, scroller);

        if (jaAssinado) {
            Span ok = new Span("✅ Contrato assinado digitalmente em "
                    + c.getDataAssinatura().toString().replace("T", " às "));
            ok.getStyle().set("color", "#27AE60").set("font-weight", "700")
                    .set("font-size", "14px").set("margin-top", "16px");
            add(ok);
            return;
        }

        Checkbox aceitar = new Checkbox("Li e aceito os termos do presente contrato");
        aceitar.getStyle().set("margin-top", "16px");

        Button assinar = new Button("Assinar Digitalmente", e -> {
            if (!aceitar.getValue()) {
                Notification.show("Tens de aceitar os termos para assinar.");
                return;
            }
            c.setEstado("ASSINADO");
            c.setDataAssinatura(LocalDateTime.now());
            contratoRepo.save(c);
            removeAll();
            Span confirmacao = new Span("✅ Contrato assinado com sucesso em "
                    + LocalDateTime.now().toString().replace("T", " às "));
            confirmacao.getStyle().set("color", "#27AE60").set("font-weight", "700").set("font-size", "16px");
            add(titulo, confirmacao);
            Button voltar = new Button("Voltar ao Portal", ev -> getUI().ifPresent(ui -> ui.navigate("portal")));
            voltar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            add(voltar);
            Notification.show("Contrato assinado!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        assinar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        assinar.getStyle().set("background-color", ViewUtils.corPrimaria()).set("margin-top", "8px");

        add(aceitar, assinar);
    }
}
