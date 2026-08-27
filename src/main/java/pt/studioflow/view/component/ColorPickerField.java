package pt.studioflow.view.component;

import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Input;

/**
 * Campo de cor hexadecimal (input nativo type="color") compatível com o
 * Binder do Vaadin. Reutilizável em qualquer entidade com um campo de cor
 * (ex: Turma.cor, Studio.corPrimaria/corSecundaria).
 */
public class ColorPickerField extends CustomField<String> {

    private final Input colorInput = new Input();

    public ColorPickerField(String label, String corPorOmissao) {
        setLabel(label);
        colorInput.setType("color");
        colorInput.setValue(corPorOmissao);
        colorInput.getStyle()
                .set("width", "48px")
                .set("height", "36px")
                .set("border", "none")
                .set("padding", "2px")
                .set("cursor", "pointer")
                .set("border-radius", "6px");
        colorInput.addValueChangeListener(e -> setModelValue(e.getValue(), true));
        add(colorInput);
    }

    public ColorPickerField(String label) {
        this(label, "#4A90E2");
    }

    @Override
    protected String generateModelValue() {
        return colorInput.getValue();
    }

    @Override
    protected void setPresentationValue(String value) {
        colorInput.setValue(value != null && !value.isBlank() ? value : "#4A90E2");
    }
}
