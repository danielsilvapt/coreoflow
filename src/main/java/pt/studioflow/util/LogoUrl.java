package pt.studioflow.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolve o URL de um logo de estúdio para uma forma servível pelo browser e
 * acrescenta um parâmetro de versão (?v=&lt;mtime&gt;) quando o ficheiro existe em
 * disco.
 *
 * <p>Os logos carregados são guardados com um nome fixo por slug
 * (LogoUploadService) e servidos de {@code ./uploads/}. Como o nome não muda ao
 * trocar de logo, o browser servia a versão anterior em cache — o sufixo de
 * versão baseado na data de modificação do ficheiro força o refresh.
 */
public final class LogoUrl {

    private static final String UPLOADS_DIR = "uploads";

    private LogoUrl() {
    }

    /** Limpa prefixos de classpath inválidos e devolve um path web relativo. */
    public static String normalizar(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        return path
                .replaceFirst("^src/main/resources/static/", "")
                .replaceFirst("^static/", "")
                .replaceFirst("^/", "");
    }

    /** {@link #normalizar(String)} + sufixo de versão quando o ficheiro existe em disco. */
    public static String comVersao(String path) {
        String limpo = normalizar(path);
        if (limpo == null || limpo.isBlank() || limpo.contains("?")) {
            return limpo;
        }
        try {
            Path ficheiro = Paths.get(UPLOADS_DIR, limpo.split("/"));
            if (Files.exists(ficheiro)) {
                return limpo + "?v=" + Files.getLastModifiedTime(ficheiro).toMillis();
            }
        } catch (Exception ignored) {
            // path inválido ou sem acesso ao disco - devolve o URL sem versão
        }
        return limpo;
    }
}
