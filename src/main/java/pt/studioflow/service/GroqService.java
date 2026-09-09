package pt.studioflow.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.studioflow.model.ConfiguracaoPlataforma;
import pt.studioflow.repository.ConfiguracaoPlataformaRepository;

/**
 * Cliente da API GROQ (compatível com a API OpenAI de chat completions).
 * A chave e o modelo vêm da {@link ConfiguracaoPlataforma} (superadmin).
 */
@Service
public class GroqService {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private final ConfiguracaoPlataformaRepository configRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate rest = new RestTemplate();

    public GroqService(ConfiguracaoPlataformaRepository configRepo) {
        this.configRepo = configRepo;
    }

    public record ChatMsg(String role, String conteudo) {
        public static ChatMsg utilizador(String c) {
            return new ChatMsg("user", c);
        }

        public static ChatMsg assistente(String c) {
            return new ChatMsg("assistant", c);
        }
    }

    public boolean disponivel() {
        ConfiguracaoPlataforma c = configRepo.findById(1L).orElse(null);
        return c != null && c.getGroqApiKey() != null && !c.getGroqApiKey().isBlank();
    }

    /**
     * Envia uma conversa ao modelo e devolve o texto da resposta.
     *
     * @param sistema  instrução de sistema (persona / regras)
     * @param historico mensagens user/assistant por ordem cronológica
     * @param json     se true, pede resposta em JSON (response_format json_object)
     */
    public String conversar(String sistema, List<ChatMsg> historico, boolean json) {
        ConfiguracaoPlataforma cfg = configRepo.findById(1L).orElseThrow(
                () -> new IllegalStateException("Configuração da plataforma não encontrada."));
        String key = cfg.getGroqApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Chave GROQ não configurada.");
        }

        List<Map<String, String>> mensagens = new ArrayList<>();
        if (sistema != null && !sistema.isBlank()) {
            mensagens.add(Map.of("role", "system", "content", sistema));
        }
        for (ChatMsg m : historico) {
            mensagens.add(Map.of("role", m.role(), "content", m.conteudo()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", cfg.getGroqModelo());
        body.put("messages", mensagens);
        body.put("temperature", json ? 0.4 : 0.7);
        body.put("max_tokens", 4096);
        if (json) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(key);

        try {
            String raw = rest.postForObject(ENDPOINT, new HttpEntity<>(mapper.writeValueAsString(body), headers),
                    String.class);
            JsonNode root = mapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("Resposta vazia da IA.");
            }
            return content.asText();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw new IllegalStateException("GROQ " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Falha a contactar a IA: " + e.getMessage(), e);
        }
    }

    /** Pergunta simples (uma mensagem de utilizador), resposta em texto. */
    public String perguntar(String sistema, String pergunta) {
        return conversar(sistema, List.of(ChatMsg.utilizador(pergunta)), false);
    }

    /** Pergunta simples com resposta forçada a JSON (response_format json_object). */
    public String perguntarJson(String sistema, String pergunta) {
        return conversar(sistema, List.of(ChatMsg.utilizador(pergunta)), true);
    }
}
