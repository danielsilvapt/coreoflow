package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;
import pt.studioflow.model.Mensalidade;

import java.util.function.Consumer;

public class ObservacoesForm extends FormLayout {

    private final TextArea observacoesField = new TextArea("Observações");
    private final Binder<Mensalidade> binder = new Binder<>(Mensalidade.class);
    private final Dialog dialog = new Dialog();

    private Consumer<Mensalidade> saveListener;
    private Runnable closeListener;

    private Mensalidade mensalidadeAtual;

    public ObservacoesForm() {
        // Configura campo
        observacoesField.setPlaceholder("Insira algumas observações...");
        observacoesField.setMaxLength(500);
        observacoesField.setHeight("120px");

        this.add(observacoesField);

        // Binder apenas para observações
        binder.forField(observacoesField)
                .bind(Mensalidade::getObservacoes, Mensalidade::setObservacoes);

        // Botões
        Button saveBtn = new Button("Guardar", e -> salvar());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancelar", e -> fechar());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(saveBtn, cancelBtn);
        dialog.add(this);
    }

    public void abrir(Mensalidade m) {
        this.mensalidadeAtual = m;
        observacoesField.setValue(m.getObservacoes() != null ? m.getObservacoes() : "");
        dialog.setHeaderTitle("Observações da Mensalidade");
        dialog.open();
    }

    private void salvar() {
        binder.writeBeanIfValid(mensalidadeAtual);
        if (saveListener != null) {
            saveListener.accept(mensalidadeAtual);
        }
        dialog.close();
    }

    private void fechar() {
        if (closeListener != null) closeListener.run();
        dialog.close();
    }

    public void addSaveListener(Consumer<Mensalidade> listener) {
        this.saveListener = listener;
    }

    public void addCloseListener(Runnable listener) {
        this.closeListener = listener;
    }
}
