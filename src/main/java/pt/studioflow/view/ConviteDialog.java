package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.timepicker.TimePicker;
import pt.studioflow.model.Convite;
import pt.studioflow.repository.ConviteRepository;
import java.time.Duration;

public class ConviteDialog extends Dialog {

    public ConviteDialog(ConviteRepository repository, Runnable onSave) {
        // 1. Configurar o Diálogo
        setHeaderTitle("Novo Evento - CoreoFlow");
        setWidth("500px");

        // 2. Criar os componentes dentro do construtor para evitar problemas de referência
        TextField evento = new TextField("Nome do Evento");
        DatePicker data = new DatePicker("Data");
        TimePicker hora = new TimePicker("Hora"); 
        TextField local = new TextField("Local");
        TextArea observacoes = new TextArea("Observações");

        // 3. Configuração de Estilo Forçada
        evento.setWidthFull();
        data.setWidthFull();
        
        // Configuração da Hora
        hora.setWidthFull();
        hora.setStep(Duration.ofMinutes(15));
        hora.setPlaceholder("HH:mm");
        hora.getElement().setAttribute("required", true); // Força o browser a reconhecer o campo

        local.setWidthFull();
        observacoes.setWidthFull();
        observacoes.setMinHeight("100px");

        // 4. Layout Vertical Simples (Sem FormLayout)
        VerticalLayout mainLayout = new VerticalLayout();
        mainLayout.add(evento, data, hora, local, observacoes);
        mainLayout.setPadding(true);
        mainLayout.setSpacing(true);
        
        add(mainLayout);

        // 5. Botões
        Button btnGuardar = new Button("Guardar", e -> {
            if (evento.isEmpty()) {
                evento.setInvalid(true);
                return;
            }
            Convite c = new Convite();
            c.setEvento(evento.getValue());
            c.setData(data.getValue());
            c.setHora(hora.getValue()); // Verifica se tens setHora na tua classe Convite!
            c.setLocal(local.getValue());
            c.setObservacoes(observacoes.getValue());

            repository.save(c);
            onSave.run();
            close();
        });
        btnGuardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        
        getFooter().add(new Button("Cancelar", i -> close()), btnGuardar);
    }
}