package pt.studioflow.model;

/**
 * Cards de KPI mostrados no Dashboard inicial para o ADMIN.
 * O SuperAdmin escolhe, por estúdio, quais ficam visíveis na StudioAdminView.
 */
public enum DashboardCard {

    ALUNOS_ATIVOS("Alunos Ativos"),
    ANIVERSARIOS("Aniversários"),
    NOVOS_ALUNOS("Novos Alunos"),
    RENOVACOES("Renovações"),
    ALUNOS_RISCO("Alunos em Risco"),
    SEGUROS_EXPIRADOS("Seguros Expirados"),
    ASSIDUIDADE_GLOBAL("Assiduidade Global"),
    PREVISAO_MENSAL("Previsão Mensal"),
    TOTAL_PAGO("Total Pago"),
    DIVIDA_MES("Dívida do Mês"),
    DIVIDA_TOTAL("Dívida Total"),
    MODALIDADES("Modalidades"),
    RENTABILIDADE("Rentabilidade");

    private final String label;

    DashboardCard(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
