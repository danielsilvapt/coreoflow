package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pt.studioflow.model.Studio;
import pt.studioflow.util.LogoUrl;

/**
 * Assinatura HTML comum a todos os emails da plataforma: o logo do estúdio (ou o
 * nome, se não tiver logo) e, por baixo e mais pequeno, "powered by CoreoFlow".
 *
 * <p>O logo é referenciado por URL absoluto ({@code app.base-url}/logos/…) porque
 * é servido publicamente por {@code WebMvcConfig} — não vale a pena anexá-lo
 * inline em cada mensagem.
 */
@Component
public class EmailAssinatura {

    private final String baseUrl;

    public EmailAssinatura(@Value("${app.base-url:https://app.coreoflow.me}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Bloco HTML de assinatura para o estúdio indicado (tolera {@code null}). */
    public String html(Studio studio) {
        String nome = studio != null && studio.getNome() != null ? escape(studio.getNome()) : "";
        String logoPath = studio != null ? LogoUrl.normalizar(studio.getLogoPath()) : null;

        String marca;
        if (logoPath != null && !logoPath.isBlank()) {
            marca = "<img src=\"" + baseUrl + "/" + logoPath + "\" alt=\"" + nome + "\""
                    + " style=\"max-height:56px;max-width:200px;object-fit:contain;\">";
        } else if (!nome.isBlank()) {
            marca = "<div style=\"font-size:16px;font-weight:bold;color:#333333;\">" + nome + "</div>";
        } else {
            marca = "";
        }

        return "<div style=\"margin-top:32px;padding-top:16px;border-top:1px solid #e0e0e0;"
                + "font-family:Arial,Helvetica,sans-serif;\">"
                + marca
                + "<div style=\"margin-top:8px;font-size:11px;color:#999999;\">"
                + "powered by <span style=\"font-weight:bold;color:#777777;\">CoreoFlow</span>"
                + "</div>"
                + "</div>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
