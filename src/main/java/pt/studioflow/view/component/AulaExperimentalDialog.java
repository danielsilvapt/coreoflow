package pt.studioflow.view.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import pt.studioflow.model.AulaExperimental;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.service.AulaExperimentalService;
import pt.studioflow.service.AulaExperimentalService.Sessao;

import java.util.List;
import java.util.function.Consumer;

/**
 * Janela para a secretaria registar uma aula experimental (ex.: contacto telefónico):
 * dados do interessado, turma e a sessão concreta (dia + hora) em que vem.
 */
public class AulaExperimentalDialog extends Dialog {

    public AulaExperimentalDialog(AulaExperimentalService service, Studio studio,
            Consumer<AulaExperimental> onMarcada) {
        setHeaderTitle("Marcar aula experimental");
        setWidth("min(640px, 96vw)");
        addClassName("cf-exp-dialog");

        RadioButtonGroup<AulaExperimental.Origem> origem = new RadioButtonGroup<>("Como chegou o contacto?");
        origem.setItems(AulaExperimental.Origem.TELEFONE, AulaExperimental.Origem.PRESENCIAL,
                AulaExperimental.Origem.EMAIL, AulaExperimental.Origem.OUTRO);
        origem.setItemLabelGenerator(AulaExperimental.Origem::getLabel);
        origem.setValue(AulaExperimental.Origem.TELEFONE);

        TextField nome = new TextField("Nome");
        nome.setRequiredIndicatorVisible(true);
        nome.setPlaceholder("Nome do aluno (ou da criança)");

        TextField telemovel = new TextField("Telemóvel");
        telemovel.setPrefixComponent(VaadinIcon.PHONE.create());
        EmailField email = new EmailField("Email");

        ComboBox<Turma> turma = new ComboBox<>("Turma");
        turma.setRequiredIndicatorVisible(true);
        turma.setItemLabelGenerator(t -> t.getDescricao()
                + (t.getModalidade() != null && t.getModalidade().getDescricao() != null
                        && !t.getModalidade().getDescricao().equalsIgnoreCase(t.getDescricao())
                        ? " · " + t.getModalidade().getDescricao() : ""));
        List<Turma> turmas = service.turmasComSessoes(studio);
        turma.setItems(turmas);

        ComboBox<Sessao> sessao = new ComboBox<>("Dia e hora");
        sessao.setRequiredIndicatorVisible(true);
        sessao.setItemLabelGenerator(Sessao::label);
        sessao.setEnabled(false);
        sessao.setHelperText("Escolhe primeiro a turma");

        turma.addValueChangeListener(e -> {
            sessao.clear();
            if (e.getValue() == null) {
                sessao.setEnabled(false);
                return;
            }
            List<Sessao> lista = service.sessoesDisponiveis(e.getValue());
            sessao.setItems(lista);
            sessao.setEnabled(!lista.isEmpty());
            sessao.setHelperText(lista.isEmpty() ? "Sem sessões nas próximas semanas"
                    : "Próximas " + AulaExperimentalService.DIAS_A_FRENTE / 7 + " semanas");
            if (!lista.isEmpty()) sessao.setValue(lista.get(0));
        });

        TextArea obs = new TextArea("Observações");
        obs.setPlaceholder("Idade, experiência, como soube do estúdio…");
        obs.setMaxLength(1000);

        FormLayout form = new FormLayout(origem, nome, telemovel, email, turma, sessao, obs);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("480px", 2));
        form.setColspan(origem, 2);
        form.setColspan(nome, 2);
        form.setColspan(obs, 2);
        if (turmas.isEmpty()) {
            Span aviso = new Span("Não há turmas ativas com horário definido no mapa de salas.");
            aviso.addClassName("cf-tone-warning");
            add(aviso);
        }
        add(form);

        Button marcar = new Button("Marcar aula", VaadinIcon.CHECK.create(), e -> {
            try {
                AulaExperimental ae = service.marcar(studio, new AulaExperimentalService.Pedido(
                        nome.getValue(), telemovel.getValue(), email.getValue(), sessao.getValue(),
                        origem.getValue(), obs.getValue()));
                Notification.show("Aula experimental marcada: " + ae.getNomeCompleto() + " — "
                        + sessao.getValue().label(), 4000, Notification.Position.BOTTOM_START)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                close();
                if (onMarcada != null) onMarcada.accept(ae);
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage(), 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        marcar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        getFooter().add(new Button("Cancelar", e -> close()), marcar);
        nome.focus();
    }
}
