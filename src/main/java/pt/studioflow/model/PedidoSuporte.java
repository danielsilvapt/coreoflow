package pt.studioflow.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Pedido de suporte submetido por um utilizador. É sempre gravado (mesmo que o
 * envio do email falhe), para nunca se perder um reporte.
 */
@Entity
public class PedidoSuporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 150)
    private String studio;

    @Column(length = 150)
    private String utilizador;

    @Column(length = 200)
    private String emailUtilizador;

    @Column(length = 60)
    private String tipo;

    @Column(length = 200)
    private String assunto;

    @Column(length = 4000)
    private String descricao;

    private LocalDateTime dataCriacao = LocalDateTime.now();

    /** Se o email de notificação foi entregue ao servidor de correio. */
    private boolean emailEnviado = false;

    private boolean resolvido = false;

    @Column(length = 4000)
    private String resposta;

    private LocalDateTime dataResposta;

    public Long getId() {
        return id;
    }

    public String getStudio() {
        return studio;
    }

    public void setStudio(String studio) {
        this.studio = studio;
    }

    public String getUtilizador() {
        return utilizador;
    }

    public void setUtilizador(String utilizador) {
        this.utilizador = utilizador;
    }

    public String getEmailUtilizador() {
        return emailUtilizador;
    }

    public void setEmailUtilizador(String emailUtilizador) {
        this.emailUtilizador = emailUtilizador;
    }

    public String getResposta() {
        return resposta;
    }

    public void setResposta(String resposta) {
        this.resposta = resposta;
    }

    public LocalDateTime getDataResposta() {
        return dataResposta;
    }

    public void setDataResposta(LocalDateTime dataResposta) {
        this.dataResposta = dataResposta;
    }

    public boolean isRespondido() {
        return resposta != null && !resposta.isBlank();
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getAssunto() {
        return assunto;
    }

    public void setAssunto(String assunto) {
        this.assunto = assunto;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public LocalDateTime getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public boolean isEmailEnviado() {
        return emailEnviado;
    }

    public void setEmailEnviado(boolean emailEnviado) {
        this.emailEnviado = emailEnviado;
    }

    public boolean isResolvido() {
        return resolvido;
    }

    public void setResolvido(boolean resolvido) {
        this.resolvido = resolvido;
    }
}
