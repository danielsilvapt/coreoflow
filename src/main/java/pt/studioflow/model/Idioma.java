package pt.studioflow.model;

/**
 * Idiomas disponíveis no formulário público de inscrição/renovação.
 * Configurável por estúdio em Studio.idiomasDisponiveis.
 */
public enum Idioma {

    PT("Português"),
    EN("English"),
    FR("Français");

    private final String label;

    Idioma(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
