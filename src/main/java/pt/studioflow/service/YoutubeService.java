package pt.studioflow.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pt.studioflow.model.ConfiguracaoPlataforma;
import pt.studioflow.repository.ConfiguracaoPlataformaRepository;

/**
 * Procura vídeos de tutoriais no YouTube (Data API v3). Se não houver chave
 * configurada, {@link #procurar} devolve lista vazia — o plano funciona na
 * mesma, apenas sem vídeos sugeridos.
 */
@Service
public class YoutubeService {

    private static final String ENDPOINT = "https://www.googleapis.com/youtube/v3/search";

    private final ConfiguracaoPlataformaRepository configRepo;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate rest;

    public YoutubeService(ConfiguracaoPlataformaRepository configRepo) {
        this.configRepo = configRepo;
        org.springframework.http.client.SimpleClientHttpRequestFactory f =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        f.setConnectTimeout(8_000);
        f.setReadTimeout(12_000);
        this.rest = new RestTemplate(f);
    }

    public record Video(String id, String titulo, String canal) {
        public String urlEmbed() {
            return "https://www.youtube.com/embed/" + id;
        }

        public String urlWatch() {
            return "https://www.youtube.com/watch?v=" + id;
        }

        public String thumbnail() {
            return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
        }
    }

    public boolean disponivel() {
        ConfiguracaoPlataforma c = configRepo.findById(1L).orElse(null);
        return c != null && c.getYoutubeApiKey() != null && !c.getYoutubeApiKey().isBlank();
    }

    /** Até {@code max} vídeos para a pesquisa dada. Nunca lança — devolve [] em erro. */
    public List<Video> procurar(String query, int max) {
        List<Video> out = new ArrayList<>();
        ConfiguracaoPlataforma cfg = configRepo.findById(1L).orElse(null);
        if (cfg == null || cfg.getYoutubeApiKey() == null || cfg.getYoutubeApiKey().isBlank()
                || query == null || query.isBlank()) {
            return out;
        }
        try {
            String url = ENDPOINT + "?part=snippet&type=video&videoEmbeddable=true&maxResults="
                    + Math.max(1, Math.min(max, 5))
                    + "&safeSearch=strict&relevanceLanguage=pt"
                    + "&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&key=" + cfg.getYoutubeApiKey();
            String raw = rest.getForObject(url, String.class);
            JsonNode items = mapper.readTree(raw).path("items");
            for (JsonNode it : items) {
                String id = it.path("id").path("videoId").asText(null);
                if (id == null) {
                    continue;
                }
                JsonNode sn = it.path("snippet");
                out.add(new Video(id, sn.path("title").asText(""), sn.path("channelTitle").asText("")));
            }
        } catch (Exception e) {
            System.err.println("YoutubeService: falha na pesquisa '" + query + "' - " + e.getMessage());
        }
        return out;
    }
}
