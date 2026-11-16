package pt.studioflow.util;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;

import pt.studioflow.model.Studio;

/**
 * Cabeçalho de marca dos PDFs: o logótipo do estúdio (quando existe) ou, em
 * alternativa, o nome do estúdio em texto. Nunca usa uma marca fixa.
 */
public final class PdfLogo {

    private PdfLogo() {
    }

    /**
     * Acrescenta o logo do estúdio ao documento; se não houver logo (ou falhar a
     * leitura) escreve o nome do estúdio. Se nem o estúdio for conhecido, não
     * acrescenta nada.
     */
    public static void adicionar(Document doc, Studio studio, float tamanho, int tamanhoFonteNome) {
        if (studio == null) {
            return;
        }
        try {
            byte[] bytes = lerLogo(studio.getLogoPath());
            if (bytes != null) {
                Image img = Image.getInstance(bytes);
                img.scaleToFit(tamanho, tamanho);
                img.setAlignment(Element.ALIGN_CENTER);
                doc.add(img);
                return;
            }
        } catch (Exception ignored) {
            // formato não suportado / ficheiro ilegível → usa o nome
        }
        try {
            if (studio.getNome() != null && !studio.getNome().isBlank()) {
                Paragraph brand = new Paragraph(studio.getNome().toUpperCase(),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, tamanhoFonteNome));
                brand.setAlignment(Element.ALIGN_CENTER);
                doc.add(brand);
            }
        } catch (Exception ignored) {
            // sem cabeçalho de marca
        }
    }

    /** Lê o logo a partir de URL absoluto, ./uploads/ ou classpath static/. */
    private static byte[] lerLogo(String logoPath) throws Exception {
        if (logoPath == null || logoPath.isBlank()) {
            return null;
        }
        String lp = logoPath.trim();
        if (lp.startsWith("http://") || lp.startsWith("https://")) {
            try (InputStream in = URI.create(lp).toURL().openStream()) {
                return in.readAllBytes();
            }
        }
        String limpo = LogoUrl.normalizar(lp);
        if (limpo.contains("?")) {
            limpo = limpo.substring(0, limpo.indexOf('?'));
        }
        Path ficheiro = Paths.get("uploads", limpo.split("/"));
        if (Files.exists(ficheiro)) {
            return Files.readAllBytes(ficheiro);
        }
        try (InputStream in = PdfLogo.class.getResourceAsStream("/static/" + limpo)) {
            return in != null ? in.readAllBytes() : null;
        }
    }
}
