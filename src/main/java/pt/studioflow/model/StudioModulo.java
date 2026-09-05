package pt.studioflow.model;

/**
 * Módulos opcionais de cada estúdio.
 * O SuperAdmin ativa/desativa por estúdio na StudioAdminView.
 * Os módulos core (Dashboard, Alunos, Config básica) são sempre visíveis.
 */
public enum StudioModulo {

    LISTA_ESPERA("Lista de Espera"),
    MAPA_SALAS("Mapa de Salas"),
    COMUNICACAO("Comunicação"),
    SOCIOS("Sócios"),
    INSCRICOES("Validar Inscrições"),
    TRANSFERENCIAS("Transferências"),
    FINANCEIRO("Financeiro"),
    ANIVERSARIOS("Aniversários (CRM)"),
    MENSALIDADES("Mensalidades"),
    PRESENCAS("Presenças"),
    REGISTO_HORAS("Registo de Horas"),
    EVENTOS("Eventos"),
    NOTIFICACOES("Notificações"),
    RELATORIOS("Relatórios"),
    AVALIACOES("Avaliações de Alunos"),
    CAMPANHAS("Campanhas Email/WhatsApp"),
    CONTRATOS("Contratos Digitais"),
    PLANO_AULAS("Plano de Aulas"),
    LOJA("Loja / Merchandising"),
    SUBSIDIOS("Subsídios e Apoios"),
    REFERENCIAS("Referências"),
    INQUERITOS("Inquéritos de Satisfação"),
    VIDEOS_AULA("Vídeos das Aulas");

    private final String label;

    StudioModulo(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
