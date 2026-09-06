package pt.studioflow.model;

/** Estado de um {@link Convite} (evento/atuação) ao longo do seu ciclo de vida. */
public enum EstadoEvento {
    PLANEADO("Planeado", "var(--lumo-contrast-60pct)"),
    CONFIRMADO("Confirmado", "var(--lumo-primary-color)"),
    CONCLUIDO("Concluído", "var(--lumo-success-color)"),
    CANCELADO("Cancelado", "var(--lumo-error-color)");

    private final String label;
    private final String color;

    EstadoEvento(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }
}
