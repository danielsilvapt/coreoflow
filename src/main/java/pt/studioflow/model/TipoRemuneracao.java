package pt.studioflow.model;

/** Modo de remuneração de um professor. */
public enum TipoRemuneracao {
    /** Valor fixo por hora, por tipo de atividade. */
    HORA,
    /** Percentagem da mensalidade de cada aluno, consoante a frequência semanal. */
    PERCENTAGEM;

    /** Converte a string guardada em Studio/Professor (null/inválido → HORA). */
    public static TipoRemuneracao from(String valor) {
        return "PERCENTAGEM".equalsIgnoreCase(valor) ? PERCENTAGEM : HORA;
    }
}
