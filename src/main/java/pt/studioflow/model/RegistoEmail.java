package pt.studioflow.model;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Histórico das comunicações enviadas no Centro de Comunicação: quem enviou,
 * para que alunos, em que turma, o quê, e se o servidor de email (SMTP) aceitou
 * o envio. O estado de entrega a cada aluno fica em {@link RegistoEmailDestinatario},
 * atualizado pelos eventos do webhook do Brevo.
 */
@Entity
@Table(name = "registo_email", indexes = {
        @Index(name = "idx_remail_data", columnList = "data"),
        @Index(name = "idx_remail_studio", columnList = "studioId"),
        @Index(name = "idx_remail_utilizador", columnList = "utilizador"),
        @Index(name = "idx_remail_msgid", columnList = "messageId")
})
public class RegistoEmail {

    public enum Estado {
        /** Aceite pelo servidor de email (SMTP) para entrega. */
        ENVIADO("Enviado", "#27AE60"),
        /** O servidor de email recusou ou não foi possível contactá-lo. */
        FALHOU("Falhou", "#E74C3C"),
        /** Não enviado: o envio de emails está desativado para o estúdio. */
        BLOQUEADO("Não enviado", "#F39C12");

        private final String label;
        private final String cor;

        Estado(String label, String cor) {
            this.label = label;
            this.cor = cor;
        }

        public String getLabel() {
            return label;
        }

        public String getCor() {
            return cor;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime data;

    private Long studioId;

    /** Username (sem o prefixo do estúdio) de quem enviou. */
    @Column(length = 150)
    private String utilizador;

    /** Nome de quem enviou, no momento do envio. */
    @Column(length = 150)
    private String utilizadorNome;

    @Column(length = 500)
    private String assunto;

    /** Nº de alunos com email a quem a mensagem foi dirigida. */
    private int numDestinatarios;

    /** Contadores do estado de entrega (atualizados pelo webhook do Brevo). */
    private int numEntregues;
    private int numAbertos;
    private int numFalhados;

    /** Alunos selecionados sem email registado (não receberam), um por linha. */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String semEmail;

    /** Turma selecionada (ou "Todos os alunos"). */
    @Column(length = 200)
    private String turma;

    /** Professor colocado em cópia, se algum. */
    @Column(length = 150)
    private String professorCopia;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String mensagem;

    /** Nomes dos ficheiros anexados, separados por vírgula. */
    @Column(length = 1000)
    private String anexos;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Estado estado;

    @Column(length = 2000)
    private String erro;

    /** Message-ID do email (sem "<>", minúsculas) — liga os eventos do Brevo a este envio. */
    @Column(length = 250)
    private String messageId;

    public Long getId() { return id; }

    public LocalDateTime getData() { return data; }
    public void setData(LocalDateTime data) { this.data = data; }

    public Long getStudioId() { return studioId; }
    public void setStudioId(Long studioId) { this.studioId = studioId; }

    public String getUtilizador() { return utilizador; }
    public void setUtilizador(String utilizador) { this.utilizador = utilizador; }

    public String getUtilizadorNome() { return utilizadorNome; }
    public void setUtilizadorNome(String utilizadorNome) { this.utilizadorNome = utilizadorNome; }

    public String getAssunto() { return assunto; }
    public void setAssunto(String assunto) { this.assunto = assunto; }

    public int getNumDestinatarios() { return numDestinatarios; }
    public void setNumDestinatarios(int numDestinatarios) { this.numDestinatarios = numDestinatarios; }

    public int getNumEntregues() { return numEntregues; }
    public void setNumEntregues(int numEntregues) { this.numEntregues = numEntregues; }

    public int getNumAbertos() { return numAbertos; }
    public void setNumAbertos(int numAbertos) { this.numAbertos = numAbertos; }

    public int getNumFalhados() { return numFalhados; }
    public void setNumFalhados(int numFalhados) { this.numFalhados = numFalhados; }

    public String getSemEmail() { return semEmail; }
    public void setSemEmail(String semEmail) { this.semEmail = semEmail; }

    public String getTurma() { return turma; }
    public void setTurma(String turma) { this.turma = turma; }

    public String getProfessorCopia() { return professorCopia; }
    public void setProfessorCopia(String professorCopia) { this.professorCopia = professorCopia; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public String getAnexos() { return anexos; }
    public void setAnexos(String anexos) { this.anexos = anexos; }

    public Estado getEstado() { return estado; }
    public void setEstado(Estado estado) { this.estado = estado; }

    public String getErro() { return erro; }
    public void setErro(String erro) { this.erro = erro; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public List<String> getListaSemEmail() {
        if (semEmail == null || semEmail.isBlank()) {
            return List.of();
        }
        return Arrays.stream(semEmail.split("\n")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    /** Quem enviou, para mostrar: nome ou username. */
    public String getRemetenteApresentacao() {
        if (utilizadorNome != null && !utilizadorNome.isBlank()) {
            return utilizadorNome;
        }
        return utilizador != null ? utilizador : "—";
    }

    /** Normaliza um Message-ID para comparação: sem "<>", espaços nem maiúsculas. */
    public static String normalizarMessageId(String id) {
        if (id == null) {
            return null;
        }
        String s = id.trim();
        if (s.startsWith("<")) {
            s = s.substring(1);
        }
        if (s.endsWith(">")) {
            s = s.substring(0, s.length() - 1);
        }
        s = s.trim().toLowerCase();
        return s.isEmpty() ? null : s;
    }
}
