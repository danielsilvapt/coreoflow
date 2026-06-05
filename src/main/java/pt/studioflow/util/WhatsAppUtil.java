package pt.studioflow.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WhatsAppUtil {

    public static String gerarLinkMensagem(String telemovel, String mensagem) {
        // Remove espaços e garante o prefixo de Portugal se necessário
        String num = telemovel.replaceAll("\\s+", "");
        if (!num.startsWith("351") && num.length() == 9) {
            num = "351" + num;
        }

        String encodedMsg = URLEncoder.encode(mensagem, StandardCharsets.UTF_8);
        return "https://wa.me/" + num + "?text=" + encodedMsg;
    }
}