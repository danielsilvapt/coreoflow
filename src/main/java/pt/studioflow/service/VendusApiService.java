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
        // Usa a API /ws/v1.1/ (a mesma do recibo). O /api/v1.1/ responde 200 vazio a qualquer
        // chave, mesmo inválida, por isso não serve para testar.
        try {
            int caixas = listarCaixas(apiKey.trim()).size();
            return "Ligação OK: " + caixas + " caixa(s) disponíveis.";
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

    // =====================================================================
    // API v1.1 com chave explícita (usada fora de um pedido do estúdio, ex.:
    // webhook do Mollie, onde não há TenantContext). Ver ReciboVendusService.
    // =====================================================================

    private static final String WS = "https://www.vendus.pt/ws/v1.1/";
    final RestTemplate rest = new RestTemplate(); // package-private: testes (MockRestServiceServer)

    /** Opção de uma lista do Vendus (método de pagamento, caixa). */
    public record Opcao(String id, String nome) {}

    /** Dados de faturação do cliente (aluno ou encarregado de educação). */
    public record DadosCliente(String nif, String nome, String email, String morada, String codigoPostal,
            String localidade, String referenciaExterna) {}

    /** Documento emitido. */
    public record Documento(String id, String numero) {}

    /** Erro devolvido pela API Vendus, já com a mensagem legível. */
    public static class VendusException extends RuntimeException {
        public VendusException(String msg) { super(msg); }
    }

    private HttpHeaders headers(String apiKey) {
        HttpHeaders h = new HttpHeaders();
        h.setBasicAuth(apiKey, "");
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getLista(String apiKey, String caminho, Map<String, String> params) {
        org.springframework.web.util.UriComponentsBuilder b =
                org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(WS + caminho);
        params.forEach(b::queryParam);
        try {
            ResponseEntity<List> r = rest.exchange(b.encode().build().toUri(), HttpMethod.GET,
                    new HttpEntity<>(headers(apiKey)), List.class);
            return r.getBody() != null ? (List<Map<String, Object>>) r.getBody() : List.of();
        } catch (HttpClientErrorException.NotFound e) {
            return List.of(); // o Vendus responde 404 "No data" a uma pesquisa sem resultados
        } catch (HttpClientErrorException e) {
            throw new VendusException(mensagemErro(e));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> enviar(String apiKey, HttpMethod metodo, String caminho, Map<String, Object> corpo) {
        try {
            ResponseEntity<Map> r = rest.exchange(WS + caminho, metodo, new HttpEntity<>(corpo, headers(apiKey)),
                    Map.class);
            return r.getBody() != null ? (Map<String, Object>) r.getBody() : Map.of();
        } catch (HttpClientErrorException e) {
            throw new VendusException(mensagemErro(e));
        }
    }

    /** "errors":[{"code":"A001","message":"AUTH"}] → "AUTH (A001)"; senão o corpo/estado. */
    private static String mensagemErro(HttpClientErrorException e) {
        String corpo = e.getResponseBodyAsString();
        try {
            Map<?, ?> m = new com.fasterxml.jackson.databind.ObjectMapper().readValue(corpo, Map.class);
            if (m.get("errors") instanceof List<?> erros && !erros.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Object o : erros) {
                    if (o instanceof Map<?, ?> er) {
                        if (sb.length() > 0) sb.append("; ");
                        sb.append(er.get("message"));
                        if (er.get("code") != null) sb.append(" (").append(er.get("code")).append(")");
                    }
                }
                return "Vendus " + e.getStatusCode().value() + ": " + sb;
            }
        } catch (Exception ignorado) {
            // corpo não é JSON
        }
        return "Vendus " + e.getStatusCode().value() + ": "
                + (corpo == null || corpo.isBlank() ? e.getStatusText() : corpo);
    }

    private static String texto(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String nomeDe(Map<String, Object> m) {
        Object n = m.get("title") != null ? m.get("title") : m.get("name");
        return n != null ? String.valueOf(n) : String.valueOf(m.get("id"));
    }

    /** Métodos de pagamento configurados na conta Vendus. */
    public List<Opcao> listarMetodosPagamento(String apiKey) {
        return getLista(apiKey, "documents/paymentmethods/", Map.of("per_page", "100")).stream()
                .map(m -> new Opcao(texto(m.get("id")), nomeDe(m))).toList();
    }

    /** Caixas (registers) da conta Vendus. */
    public List<Opcao> listarCaixas(String apiKey) {
        return getLista(apiKey, "registers/", Map.of("per_page", "100")).stream()
                .map(m -> new Opcao(texto(m.get("id")), nomeDe(m))).toList();
    }

    /** NIF português válido (9 dígitos, sem o "consumidor final" 999999990, que o Vendus não aceita). */
    public static String nifValido(String nif) {
        if (nif == null) return null;
        String limpo = nif.replaceAll("[^0-9]", "");
        return limpo.length() == 9 && !"999999990".equals(limpo) ? limpo : null;
    }

    /**
     * Devolve o ID do cliente Vendus, criando-o se não existir. Procura primeiro pelo NIF
     * (o mesmo encarregado com vários educandos é um só cliente) e, sem NIF, pela referência
     * externa do CoreoFlow. Garante que o cliente tem o email e o envio de faturas ligado,
     * para o Vendus enviar o recibo.
     */
    public String obterOuCriarCliente(String apiKey, DadosCliente c) {
        String nif = nifValido(c.nif());
        List<Map<String, Object>> encontrados = nif != null
                ? getLista(apiKey, "clients/", Map.of("fiscal_id", nif))
                : getLista(apiKey, "clients/", Map.of("external_reference", c.referenciaExterna()));
        // A pesquisa pode ser aproximada: confirma-se o campo exato.
        Map<String, Object> cliente = encontrados.stream()
                .filter(m -> nif != null ? nif.equals(texto(m.get("fiscal_id")))
                        : c.referenciaExterna().equals(texto(m.get("external_reference"))))
                .findFirst().orElse(null);
        boolean temEmail = c.email() != null && !c.email().isBlank();

        if (cliente == null) {
            Map<String, Object> corpo = new java.util.LinkedHashMap<>();
            if (nif != null) corpo.put("fiscal_id", nif);
            corpo.put("name", c.nome());
            if (c.morada() != null && !c.morada().isBlank()) corpo.put("address", c.morada());
            if (c.codigoPostal() != null && !c.codigoPostal().isBlank()) corpo.put("postalcode", c.codigoPostal());
            if (c.localidade() != null && !c.localidade().isBlank()) corpo.put("city", c.localidade());
            if (temEmail) {
                corpo.put("email", c.email());
                corpo.put("send_email", "yes");
            }
            corpo.put("country", "PT");
            corpo.put("external_reference", c.referenciaExterna());
            String id = texto(enviar(apiKey, HttpMethod.POST, "clients/", corpo).get("id"));
            if (id == null) throw new VendusException("Vendus não devolveu o ID do cliente criado.");
            return id;
        }

        String id = texto(cliente.get("id"));
        if (temEmail && (!c.email().equalsIgnoreCase(String.valueOf(cliente.get("email")))
                || !"yes".equals(texto(cliente.get("send_email"))))) {
            enviar(apiKey, HttpMethod.PATCH, "clients/" + id, Map.of("email", c.email(), "send_email", "yes"));
        }
        return id;
    }

    /** Documento já emitido com esta referência externa (evita duplicados em repetições). */
    public java.util.Optional<Documento> documentoPorReferencia(String apiKey, String referencia, boolean testes) {
        return getLista(apiKey, "documents/", Map.of("external_reference", referencia,
                        "mode", testes ? "tests" : "normal")).stream()
                .filter(m -> referencia.equals(texto(m.get("external_reference"))))
                .filter(m -> !"A".equals(texto(m.get("status")))) // anulados não contam
                .map(m -> new Documento(texto(m.get("id")), texto(m.get("number"))))
                .findFirst();
    }

    /**
     * Emite uma Fatura-Recibo (FR) já paga, com uma linha de serviço. Taxa/isenção nulas usam a
     * predefinida da conta Vendus; método de pagamento/caixa nulos usam os predefinidos.
     */
    public Documento emitirFaturaRecibo(String apiKey, String clienteId, String titulo, java.math.BigDecimal valor,
            String metodoPagamentoId, String registerId, String taxa, String isencao, String referencia,
            boolean testes) {
        String preco = valor.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("reference", "COREOFLOW-SERV");
        item.put("title", titulo.length() > 150 ? titulo.substring(0, 150) : titulo);
        item.put("qty", 1);
        item.put("gross_price", preco);
        item.put("type_id", "S");
        if (taxa != null && !taxa.isBlank()) {
            item.put("tax_id", taxa);
            if ("ISE".equals(taxa) && isencao != null && !isencao.isBlank()) item.put("tax_exemption", isencao);
        }

        Map<String, Object> pagamento = new java.util.LinkedHashMap<>();
        if (metodoPagamentoId != null && !metodoPagamentoId.isBlank()) pagamento.put("id", metodoPagamentoId);
        pagamento.put("amount", preco);

        Map<String, Object> corpo = new java.util.LinkedHashMap<>();
        corpo.put("type", "FR");
        corpo.put("mode", testes ? "tests" : "normal");
        if (registerId != null && !registerId.isBlank()) corpo.put("register_id", registerId);
        corpo.put("client", Map.of("id", clienteId));
        corpo.put("items", List.of(item));
        corpo.put("payments", List.of(pagamento));
        corpo.put("external_reference", referencia);

        Map<String, Object> r = enviar(apiKey, HttpMethod.POST, "documents/", corpo);
        String numero = texto(r.get("number"));
        if (numero == null) throw new VendusException("Vendus não devolveu o número do documento.");
        return new Documento(texto(r.get("id")), numero);
    }
}
