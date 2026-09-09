package pt.studioflow.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Registo de log da aplicação (nível WARN e superior), gravado por um appender
 * do Logback. Consultável pelo superadmin em /admin/logs.
 */
@Entity
@Table(name = "log_entry", indexes = {
        @Index(name = "idx_log_data", columnList = "data"),
        @Index(name = "idx_log_nivel", columnList = "nivel"),
        @Index(name = "idx_log_studio", columnList = "studioSlug")
})
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime data;

    @Column(length = 10)
    private String nivel;

    @Column(length = 255)
    private String logger;

    @Column(length = 120)
    private String thread;

    @Column(length = 80)
    private String studioSlug;

    @Column(length = 2000)
    private String mensagem;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String stacktrace;

    public Long getId() {
        return id;
    }

    public LocalDateTime getData() {
        return data;
    }

    public void setData(LocalDateTime data) {
        this.data = data;
    }

    public String getNivel() {
        return nivel;
    }

    public void setNivel(String nivel) {
        this.nivel = nivel;
    }

    public String getLogger() {
        return logger;
    }

    public void setLogger(String logger) {
        this.logger = logger;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    public String getStudioSlug() {
        return studioSlug;
    }

    public void setStudioSlug(String studioSlug) {
        this.studioSlug = studioSlug;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public String getStacktrace() {
        return stacktrace;
    }

    public void setStacktrace(String stacktrace) {
        this.stacktrace = stacktrace;
    }

    /** Logger curto: só a última parte (classe). */
    public String getLoggerCurto() {
        if (logger == null) {
            return "";
        }
        int i = logger.lastIndexOf('.');
        return i >= 0 ? logger.substring(i + 1) : logger;
    }
}
