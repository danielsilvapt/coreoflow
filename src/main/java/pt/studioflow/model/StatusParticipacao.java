package pt.studioflow.model;

public enum StatusParticipacao {
    CONFIRMADO("Confirmado", "var(--lumo-success-color)"),
    PENDENTE("Pendente", "var(--lumo-warning-color)"),
    NAO_VAI("Não Participa", "var(--lumo-error-color)"),
    AGUARDA_COREOGRAFIA("Em Preparação", "var(--lumo-primary-color)");

    private final String label;
    private final String color;

    StatusParticipacao(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() { return label; }
    public String getColor() { return color; }
}