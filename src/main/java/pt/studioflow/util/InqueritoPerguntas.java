package pt.studioflow.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serializa/lê a lista de perguntas de um {@link pt.studioflow.model.Inquerito}
 * a partir do campo de texto JSON {@code [{"id":"q1","texto":"...","tipo":"ESCALA"}, ...]}.
 *
 * <p>Parser manual (sem Jackson) para seguir o mesmo padrão já usado em
 * {@code InqueritosView#parseRespostas}.
 */
public final class InqueritoPerguntas {

    public enum Tipo { ESCALA, TEXTO }

    public record Pergunta(String id, String texto, Tipo tipo) {
    }

    private static final Pattern PERGUNTA_PATTERN = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"texto\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*,\\s*\"tipo\"\\s*:\\s*\"([^\"]*)\"\\s*\\}");

    private InqueritoPerguntas() {
    }

    public static List<Pergunta> parse(String json) {
        List<Pergunta> lista = new ArrayList<>();
        if (json == null || json.isBlank()) return lista;
        Matcher m = PERGUNTA_PATTERN.matcher(json);
        while (m.find()) {
            String id = m.group(1);
            String texto = unescape(m.group(2));
            Tipo tipo = "TEXTO".equals(m.group(3)) ? Tipo.TEXTO : Tipo.ESCALA;
            lista.add(new Pergunta(id, texto, tipo));
        }
        return lista;
    }

    public static String toJson(List<Pergunta> perguntas) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < perguntas.size(); i++) {
            Pergunta p = perguntas.get(i);
            sb.append("  {\"id\":\"q").append(i + 1).append("\",\"texto\":\"")
                    .append(escape(p.texto())).append("\",\"tipo\":\"").append(p.tipo()).append("\"}");
            if (i < perguntas.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    /** Serializa as respostas de um aluno: {@code {"q1":"4","q2":"Muito satisfeito"}}. */
    public static String toRespostasJson(Map<String, String> respostas) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> e : respostas.entrySet()) {
            if (i++ > 0) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":\"").append(escape(e.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
