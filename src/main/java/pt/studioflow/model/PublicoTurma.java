package pt.studioflow.model;

/** Para quem é a turma, usado para filtrar as modalidades visíveis na inscrição pública conforme a data de nascimento. */
public enum PublicoTurma {
    CRIANCA("Só crianças"),
    ADULTO("Só adultos"),
    AMBAS("Crianças e adultos");

    private final String label;

    PublicoTurma(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
