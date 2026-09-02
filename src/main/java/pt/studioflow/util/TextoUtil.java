package pt.studioflow.util;

import java.text.Normalizer;

/** Utilitários de normalização de texto (comparação de nomes sem acentos/maiúsculas). */
public final class TextoUtil {

    private TextoUtil() {}

    /** Remove acentos, passa a minúsculas e faz trim. Null → "". */
    public static String normalizar(String t) {
        if (t == null) return "";
        String s = Normalizer.normalize(t, Normalizer.Form.NFD);
        return s.replaceAll("[^\\p{ASCII}]", "").toLowerCase().trim();
    }

    /** True se os dois textos, normalizados, forem iguais. */
    public static boolean nomeIgual(String a, String b) {
        return normalizar(a).equals(normalizar(b));
    }

    /** True se {@code texto} normalizado contém {@code parte} normalizada (não vazia). */
    public static boolean contemNome(String texto, String parte) {
        String p = normalizar(parte);
        return !p.isEmpty() && normalizar(texto).contains(p);
    }
}
