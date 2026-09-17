package pt.studioflow.model;

/** Tipo de um {@link Convite} (evento/atuação) — usado, por exemplo, para decidir quem recebe cópia dos emails. */
public enum TipoEvento {
    COMPETICAO("Competição"),
    SOCIAL("Social"),
    ESPETACULO("Espetáculo"),
    WORKSHOP("Workshop"),
    OUTRO("Outro");

    private final String label;

    TipoEvento(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
