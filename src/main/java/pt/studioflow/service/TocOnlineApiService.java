package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import pt.studioflow.model.Studio;
import pt.studioflow.repository.StudioRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integração com a API do TOCOnline (OAuth2 authorization_code, por estúdio).
 *
 * <p><b>Importante:</b> ao contrário do {@link VendusApiService} (API key estática,
 * já validado em produção), esta integração ainda não foi testada contra uma conta
 * real do TOCOnline — não existe sandbox público documentado. Os endpoints e o
 * formato dos pedidos ({@link #emitirFatura}, {@link #testarLigacao}) foram inferidos
 * da documentação pública e de bibliotecas open-source de terceiros (ex:
 * django-toconline), não da especificação OpenAPI completa. É esperado precisar de
 * um pequeno ciclo de ajuste (como aconteceu historicamente com o Vendus) assim que
 * houver credenciais reais para testar.
 *
 * <p>Cada estúdio gera as suas próprias credenciais no TOCOnline em
 * "Empresa &gt; Dados API", convidando o "CoreoFlow" como integrador, e cola o
 * Client ID/Secret no StudioAdminView. O fluxo "Ligar ao TOCOnline" (botão no
 * admin) faz depois o authorization_code redirect para obter e guardar o
 * access/refresh token desse estúdio especificamente.
 */
@Service
public class TocOnlineApiService {

    private static final String OAUTH_URL = "https://app10.toconline.pt/oauth";
    private static final String API_URL = "https://api10.toconline.pt/api/v1";

    private final StudioRepository studioRepository;
    private final String appBaseUrl;

    /** state -> pedido pendente, para validar o callback OAuth. Expira em 15 min. */
    private final Map<String, PedidoLigacao> pedidosPendentes = new ConcurrentHashMap<>();

    private record PedidoLigacao(Long studioId, LocalDateTime expiraEm) {}

    public TocOnlineApiService(StudioRepository studioRepository,
            @Value("${app.base-url:https://app.coreoflow.me}") String appBaseUrl) {
        this.studioRepository = studioRepository;
        this.appBaseUrl = appBaseUrl;
    }

    private String redirectUri() {
        return appBaseUrl + "/toconline/callback";
    }

    /** URL para onde redirecionar o admin do estúdio para autorizar o CoreoFlow (botão "Ligar ao TOCOnline"). */
    public String gerarUrlAutorizacao(Studio studio) {
        if (studio.getTocOnlineClientId() == null || studio.getTocOnlineClientId().isBlank()) {
            throw new IllegalStateException("Configure primeiro o Client ID/Secret do TOCOnline para este estúdio.");
        }
        String state = UUID.randomUUID().toString();
        limparPedidosExpirados();
        pedidosPendentes.put(state, new PedidoLigacao(studio.getId(), LocalDateTime.now().plusMinutes(15)));

        return UriComponentsBuilder.fromHttpUrl(OAUTH_URL + "/auth")
                .queryParam("client_id", studio.getTocOnlineClientId())
                .queryParam("redirect_uri", redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", "commercial")
                .queryParam("state", state)
                .build().toUriString();
    }

    /** Chamado pelo TocOnlineCallbackView quando o TOCOnline redireciona de volta com o código. */
    public String concluirLigacao(String code, String state) {
        PedidoLigacao pedido = pedidosPendentes.remove(state);
        if (pedido == null || pedido.expiraEm().isBefore(LocalDateTime.now())) {
            return "Pedido de ligação inválido ou expirado. Volta a clicar em \"Ligar ao TOCOnline\".";
        }
        Studio studio = studioRepository.findById(pedido.studioId()).orElse(null);
        if (studio == null) {
            return "Estúdio não encontrado.";
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("scope", "commercial");
            guardarTokens(studio, trocarToken(studio, form));
            return "Ligado ao TOCOnline com sucesso!";
        } catch (Exception e) {
            return "Erro ao ligar ao TOCOnline: " + e.getMessage();
        }
    }

    private void limparPedidosExpirados() {
        LocalDateTime agora = LocalDateTime.now();
        pedidosPendentes.values().removeIf(p -> p.expiraEm().isBefore(agora));
    }

    private Map<String, Object> trocarToken(Studio studio, MultiValueMap<String, String> form) {
        RestTemplate rt = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(studio.getTocOnlineClientId(), studio.getTocOnlineClientSecret());
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
        ResponseEntity<Map> resp = rt.postForEntity(OAUTH_URL + "/token", entity, Map.class);
        return resp.getBody();
    }

    @SuppressWarnings("unchecked")
    private void guardarTokens(Studio studio, Map<String, Object> body) {
        if (body == null || body.get("access_token") == null) {
            throw new IllegalStateException("Resposta inesperada do TOCOnline ao obter o token.");
        }
        studio.setTocOnlineAccessToken((String) body.get("access_token"));
        if (body.get("refresh_token") != null) {
            studio.setTocOnlineRefreshToken((String) body.get("refresh_token"));
        }
        int expiresIn = body.get("expires_in") != null ? ((Number) body.get("expires_in")).intValue() : 14400;
        // Margem de 60s para nunca usar um token já expirado por causa da latência do pedido.
        studio.setTocOnlineTokenExpiraEm(LocalDateTime.now().plusSeconds(Math.max(60, expiresIn - 60)));
        studioRepository.save(studio);
    }

    /** Access token válido para este estúdio, renovando-o via refresh_token se necessário. */
    private String getAccessToken(Studio studio) {
        if (!studio.isTocOnlineLigado()) {
            throw new IllegalStateException("Este estúdio ainda não ligou a conta TOCOnline.");
        }
        if (studio.getTocOnlineAccessToken() != null && studio.getTocOnlineTokenExpiraEm() != null
                && studio.getTocOnlineTokenExpiraEm().isAfter(LocalDateTime.now())) {
            return studio.getTocOnlineAccessToken();
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", studio.getTocOnlineRefreshToken());
        form.add("scope", "commercial");
        guardarTokens(studio, trocarToken(studio, form));
        return studio.getTocOnlineAccessToken();
    }

    /** Testa a ligação atual (token válido/renovável + pedido simples à API). Usado pelo botão "Testar Ligação". */
    public String testarLigacao(Studio studio) {
        try {
            String token = getAccessToken(studio);
            RestTemplate rt = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = rt.exchange(API_URL + "/companies", HttpMethod.GET, entity, String.class);
            return "Ligação OK: " + resp.getStatusCode();
        } catch (HttpClientErrorException e) {
            return "Falha na API: " + e.getStatusCode() + " " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "Falha na ligação: " + e.getMessage();
        }
    }

    /**
     * Emite uma fatura (documento "FT") para o cliente identificado pelo NIF - o
     * TOCOnline cria/associa o cliente automaticamente pelo NIF, não é preciso
     * procurar/criar o cliente à parte (ao contrário do Vendus).
     */
    public String emitirFatura(Studio studio, String nifCliente, String nomeCliente, String descricao, double valor) {
        try {
            String token = getAccessToken(studio);
            RestTemplate rt = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.valueOf("application/vnd.api+json"));

            String jsonBody = String.format(java.util.Locale.US, """
                    {
                      "data": {
                        "type": "sales_documents",
                        "attributes": {
                          "document_type": "FT",
                          "date": "%s",
                          "customer_tax_registration_number": "%s",
                          "customer_business_name": "%s",
                          "lines": [
                            { "description": "%s", "quantity": 1, "unit_price": %.2f, "tax_id": "NOR" }
                          ]
                        }
                      }
                    }
                    """, LocalDate.now(), nifCliente, nomeCliente.replace("\"", "'"),
                    descricao.replace("\"", "'"), valor);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<Map> resp = rt.postForEntity(API_URL + "/commercial/sales_documents", entity, Map.class);
            if (resp.getStatusCode().is2xxSuccessful()) {
                return "Sucesso! Fatura TOCOnline criada.";
            }
            return "Erro: resposta " + resp.getStatusCode();
        } catch (HttpClientErrorException e) {
            return "Erro TOCOnline: " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "Erro técnico: " + e.getMessage();
        }
    }
}
