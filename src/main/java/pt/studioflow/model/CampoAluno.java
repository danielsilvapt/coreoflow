package pt.studioflow.model;

/**
 * Campos opcionais do formulário de aluno.
 * O SuperAdmin ativa/desativa por estúdio.
 * Campos core (nome, telemóvel, email, data nascimento, tipo) são sempre visíveis.
 */
public enum CampoAluno {
    GENERO("Género"),
    MORADA("Morada (rua, CP, localidade)"),
    NACIONALIDADE("Nacionalidade"),
    N_IDENTIFICACAO("Nº Identificação (CC/BI)"),
    N_CONTRIBUINTE("Nº Contribuinte (NIF)"),
    SOCIO("Sócio / Nº Sócio"),
    SEGURO_DESPORTIVO("Seguro Desportivo"),
    QUOTAS("Datas de Quotas e Seguro"),
    OBSERVACOES("Observações");

    private final String label;
    CampoAluno(String label) { this.label = label; }
    public String getLabel() { return label; }
}
