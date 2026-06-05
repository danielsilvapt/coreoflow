package pt.studioflow.service;

import pt.studioflow.config.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@Service
public class VendusApiService {

    /** Obtém a chave API Vendus do estúdio atual (multi-tenant). */
    private String getApiKey() {
        pt.studioflow.model.Studio studio = TenantContext.getCurrentStudio();
        return (studio != null && studio.getVendusApiKey() != null) ? studio.getVendusApiKey() : "";
    }

    private final String BASE_URL = "https://www.vendus.pt/api/v1.1/";

    /**
     * Testa a ligação à API Vendus usando a chave do studio atual.
     * @param getApiKey() chave da API do studio (pt.studioflow.model.Studio.getVendusApiKey())
     */
    public String testarLigacao(String apiKey) {
        try {

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(apiKey, "");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "documents?per_page=1",
                    HttpMethod.GET,
                    entity,
                    String.class);

            return "Ligação OK: " + response.getStatusCode();
        } catch (Exception e) {
            return "Falha na API: " + e.getMessage();
        }
    }

    public String obterClientePorNIF(String nif) {
        try {
            if (nif == null || nif.isEmpty())
                return "NIF não fornecido.";
            String nifLimpo = nif.replaceAll("\\s+", "");

            RestTemplate restTemplate = new RestTemplate();

            // Na v1.0 o URL base é /ws/clients/
            // A chave API é passada como parâmetro ?api_key=
            // O filtro de NIF é feito com o parâmetro 'fiscal_id'
            String url = "https://www.vendus.pt/ws/clients/?api_key=" + getApiKey() + "&fiscal_id=" + nifLimpo;

            // Na v1.0, a resposta vem geralmente num Array de objetos
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            List<Map<String, Object>> clientes = (List<Map<String, Object>>) response.getBody();

            if (clientes != null && !clientes.isEmpty()) {
                Map<String, Object> cliente = clientes.get(0);
                String nome = (String) cliente.get("name");
                String idVendus = String.valueOf(cliente.get("id")); // O ID pode vir como Integer ou String
                return "Sucesso (v1.0)! Encontrado: " + nome + " (ID Vendus: " + idVendus + ")";
            } else {
                return "Cliente não encontrado no Vendus (v1.0) com NIF: " + nifLimpo;
            }
        } catch (Exception e) {
            return "Erro na v1.0: " + e.getMessage();
        }
    }

    public String obterUltimoDocumento(String clienteId) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            // URL da v1.0 para documentos
            // client_id: filtra os docs do aluno
            // per_page=1: queremos apenas o último
            String url = "https://www.vendus.pt/ws/documents/" +
                    "?api_key=" + getApiKey() +
                    "&client_id=" + clienteId +
                    "&per_page=1";

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            List<Map<String, Object>> documentos = (List<Map<String, Object>>) response.getBody();

            if (documentos != null && !documentos.isEmpty()) {
                Map<String, Object> doc = documentos.get(0);

                String numero = (String) doc.get("number");
                String data = (String) doc.get("date");
                String total = String.valueOf(doc.get("amount_gross"));
                String status = (String) doc.get("status"); // "on" = válido, "void" = anulado

                return "Último Doc: " + numero +
                        "\nData: " + data +
                        "\nValor: " + total + "€" +
                        "\nEstado: " + (status.equals("on") ? "Válido" : "Anulado");
            } else {
                return "Este aluno ainda não tem documentos emitidos no Vendus.";
            }
        } catch (Exception e) {
            return "Erro ao obter documento: " + e.getMessage();
        }
    }

    public String obterEstadoDocumento(String documentoId) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(getApiKey(), "");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        // Endpoint: documents/{id}
        String url = BASE_URL + "documents/" + documentoId;

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
            // No JSON retornado, procura o campo "status".
            // "on" = Válido, "void" = Anulado
        } catch (Exception e) {
            return "Erro ao buscar documento: " + e.getMessage();
        }
    }

    public String criarFatura(String clienteId, String itemNome, double valor, String emailCliente) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://www.vendus.pt/ws/documents/?api_key=" + getApiKey();

            // --- ADICIONADO: "output" para envio de email ---
            // Se passares um array com "email", o Vendus envia automaticamente.
            String jsonBody = String.format(java.util.Locale.US, """
                    {
                        "type": "FT",
                        "register_id": "1",
                        "client": {
                            "id": "%s"
                        },
                        "items": [
                            {
                                "title": "%s",
                                "qty": "1",
                                "gross_price": "%.2f",
                                "tax_id": "NOR"
                            }
                        ],
                        "output": [
                            {
                                "type": "email",
                                "address": "%s"
                            }
                        ]
                    }
                    """, clienteId, itemNome, valor, emailCliente);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> resBody = response.getBody();

            if (resBody != null && resBody.containsKey("id")) {
                return "Fatura " + resBody.get("number") + " criada e enviada para " + emailCliente;
            }

            return "Fatura criada, mas houve um problema no retorno dos dados.";

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return "Erro Vendus: " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "Erro técnico: " + e.getMessage();
        }
    }

    public String consultarDocumento(String documentoId) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(getApiKey(), "");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        String url = BASE_URL + "documents/" + documentoId;

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
            // No JSON, verifica: "status" ("on" ou "void") e "system_status" ("settled" se
            // paga)
        } catch (Exception e) {
            return "Erro: " + e.getMessage();
        }
    }

    public String listarUltimosClientes() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(getApiKey(), "");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = "https://www.vendus.pt/api/v1.1/clients";

            // Usamos ResponseEntity para ver o código de estado real (200, 403, etc)
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            System.out.println("Código de Resposta: " + response.getStatusCode());
            System.out.println("Corpo da Resposta: " + response.getBody());

            return "Resposta bruta da API: " + response.getBody();
        } catch (Exception e) {
            return "Erro técnico: " + e.getMessage();
        }
    }

    public String diagnosticoGeral() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(getApiKey(), "");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Teste 1: Lojas (Geralmente todos têm acesso)
            String resLojas = restTemplate.exchange("https://www.vendus.pt/api/v1.1/stores",
                    HttpMethod.GET, entity, String.class).getBody();

            // Teste 2: Produtos
            String resProdutos = restTemplate.exchange("https://www.vendus.pt/api/v1.1/products?per_page=1",
                    HttpMethod.GET, entity, String.class).getBody();

            return "--- DIAGNÓSTICO ---\n" +
                    "Lojas: " + (resLojas != null ? resLojas : "null") + "\n" +
                    "Produtos: " + (resProdutos != null ? resProdutos : "null");
        } catch (Exception e) {
            return "Erro no diagnóstico: " + e.getMessage();
        }
    }

    public String listarRegisters() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            // Endpoint v1.0 para listar caixas/pontos de venda
            String url = "https://www.vendus.pt/ws/registers/?api_key=" + getApiKey();

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            List<Map<String, Object>> registers = (List<Map<String, Object>>) response.getBody();

            if (registers != null && !registers.isEmpty()) {
                StringBuilder sb = new StringBuilder("--- CAIXAS ENCONTRADAS ---\n\n");
                for (Map<String, Object> reg : registers) {
                    sb.append("📦 Nome: ").append(reg.get("name"))
                            .append("\n🆔 ID (register_id): ").append(reg.get("id"))
                            .append("\nEstado: ").append(reg.get("status"))
                            .append("\n------------------\n");
                }
                return sb.toString();
            }
            return "Nenhuma caixa (register) encontrada na tua conta Vendus.";
        } catch (Exception e) {
            return "Erro ao listar registers: " + e.getMessage();
        }
    }

    public String criarFaturaComVendusPay(String clienteId, String descricaoMensalidade, double valor) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://www.vendus.pt/ws/v1.1/documents/?api_key=" + getApiKey();

            String meuRegisterId = "68092483";
            String dataVencimento = LocalDate.now().plusDays(7).toString();

            // Estrutura validada pelos erros anteriores:
            // 1. 'payments' é permitido (apenas com 'id' e 'amount').
            // 2. 'ifthenpay' é o gatilho para gerar o link eletrónico.
            // 3. Removido 'send_email' e 'payment_method_id' que causavam 403.

            int clientID = 68107283;

            System.out.println("CLIENT ID para o NIF 250535327 -> " + clientID);

            String jsonBody = String.format(java.util.Locale.US, """
                    {
                      "type": "FT",
                      "register_id" : 68092483,
                      "client": { "id": "%s" },
                      "date_due": "%s",
                      "items": [
                        {
                          "gross_price": 0.01,
                          "qty": 1,
                          "title" : "Aulas de Dança"
                        }
                      ],
                      "ifthenpay": "no",
                      "payments": [
                            {
                            "method": 68092473,
                            "amount": 0,
                            "days_due": "7"
                            }
                        ]                    
                    }
                    """, clientID, dataVencimento);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> resBody = response.getBody();

            if (response.getStatusCode().is2xxSuccessful() && resBody != null) {
                // Se o VendusPay estiver bem configurado, o PDF gerado no link abaixo
                // deverá conter o identificador de pagamento.
                String pdfUrl = resBody.get("output_data") != null ? resBody.get("output_data").toString()
                        : "Link não disponível";
                return "Sucesso! Fatura gerada. PDF: " + pdfUrl;
            }

            return "Erro na resposta.";

        } catch (HttpClientErrorException e) {
            return "Erro API Vendus: " + e.getResponseBodyAsString();
        } catch (Exception e) {
            return "Erro: " + e.getMessage();
        }
    }

    public String listarMetodosPagamento() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://www.vendus.pt/ws/payment_methods/?api_key=" + getApiKey();
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody().toString();
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    public String buscarClientePorNIF(String nif) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            // Endpoint v1.1 para listagem de clientes
            // Usamos o parâmetro 'number' para filtrar pelo NIF
            String url = "https://www.vendus.pt/ws/v1.1/clients/?api_key=" + getApiKey() + "&number=" + nif;

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            List<Map<String, Object>> clientes = response.getBody();

            if (clientes != null && !clientes.isEmpty()) {
                // O Vendus devolve uma lista. Se houver correspondência,
                // o primeiro resultado é o nosso cliente.
                Map<String, Object> cliente = clientes.get(0);
                return cliente.get("id").toString();
            }

            return null; // Cliente não encontrado
        } catch (Exception e) {
            System.err.println("Erro ao buscar cliente: " + e.getMessage());
            return null;
        }
    }

}
