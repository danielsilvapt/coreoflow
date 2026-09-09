package pt.studioflow.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pt.studioflow.config.TenantContext;
import pt.studioflow.model.ConfiguracaoPlataforma;
import pt.studioflow.model.PlanoDanca;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.ConfiguracaoPlataformaRepository;
import pt.studioflow.repository.PlanoDancaRepository;

/**
 * Orquestra o Treinador de Dança IA: gera planos de aprendizagem com a GROQ,
 * enriquece cada passo com vídeos do YouTube e mantém o chat com o treinador.
 */
@Service
public class TreinadorDancaService {

    private final GroqService groq;
    private final YoutubeService youtube;
    private final PlanoDancaRepository planoRepo;
    private final ConfiguracaoPlataformaRepository configRepo;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public TreinadorDancaService(GroqService groq, YoutubeService youtube,
                                 PlanoDancaRepository planoRepo,
                                 ConfiguracaoPlataformaRepository configRepo) {
        this.groq = groq;
        this.youtube = youtube;
        this.planoRepo = planoRepo;
        this.configRepo = configRepo;
    }

    // ---- DTOs consumidos pela view ----

    public record Passo(String nome, String descricao, String errosComuns, String dica,
                        String pesquisaYoutube, List<YoutubeService.Video> videos) {
    }

    public record Combinacao(String nome, String descricao) {
    }

    public record PlanoConteudo(String resumo, int duracaoSemanas, List<String> aquecimento,
                                List<Passo> passos, List<Combinacao> combinacoes,
                                List<String> praticaSemanal) {
    }

    // ---- Disponibilidade ----

    public boolean disponivel() {
        ConfiguracaoPlataforma c = configRepo.findById(1L).orElse(null);
        if (c == null || !c.treinadorIaDisponivel()) {
            return false;
        }
        Studio s = TenantContext.getCurrentStudio();
        return s == null || s.hasModulo(pt.studioflow.model.StudioModulo.TREINADOR_IA);
    }

    // ---- Geração de plano ----

    private static final String SISTEMA_PLANO = """
            És um professor de dança experiente e motivador. Crias planos de
            aprendizagem progressivos, seguros e realistas para serem seguidos
            numa app. Respondes SEMPRE em português de Portugal e SEMPRE num
            único objeto JSON válido, sem texto à volta, com este formato exato:
            {
              "resumo": "1-2 frases sobre o plano",
              "duracaoSemanas": <inteiro>,
              "aquecimento": ["exercício 1", "exercício 2", ...],
              "passos": [
                {
                  "nome": "nome do passo/movimento",
                  "descricao": "como executar, 2-4 frases",
                  "errosComuns": "erros típicos a evitar",
                  "dica": "uma dica prática",
                  "pesquisaYoutube": "termo de pesquisa em inglês para um tutorial deste passo"
                }
              ],
              "combinacoes": [ { "nome": "nome da combinação", "descricao": "que passos junta e como" } ],
              "praticaSemanal": ["Semana 1: ...", "Semana 2: ...", ...]
            }
            Entre 5 e 10 passos, ordenados do mais básico ao mais avançado.
            """;

    @Transactional
    public PlanoDanca criarPlano(String estilo, String nivel, String objetivo, String paraQuem,
                                 String email, String nome) {
        Studio studio = TenantContext.getCurrentStudio();
        String pedido = "Estilo: " + estilo + "\nNível: " + nivel
                + "\nObjetivo: " + (objetivo == null || objetivo.isBlank() ? "evolução geral" : objetivo)
                + "\nCria o plano em JSON.";

        String jsonPlano = groq.perguntarJson(SISTEMA_PLANO, pedido);
        ObjectNode raiz;
        try {
            JsonNode n = mapper.readTree(jsonPlano);
            raiz = n.isObject() ? (ObjectNode) n : mapper.createObjectNode();
        } catch (Exception e) {
            throw new IllegalStateException("A IA devolveu um plano inválido. Tenta novamente.", e);
        }

        // Enriquecer cada passo com vídeos do YouTube
        JsonNode passos = raiz.path("passos");
        if (passos.isArray()) {
            for (JsonNode passo : passos) {
                String q = passo.path("pesquisaYoutube").asText("");
                if (q.isBlank()) {
                    q = passo.path("nome").asText("") + " " + estilo + " tutorial";
                }
                List<YoutubeService.Video> videos = youtube.procurar(q, 2);
                ArrayNode arr = mapper.createArrayNode();
                for (YoutubeService.Video v : videos) {
                    ObjectNode vn = mapper.createObjectNode();
                    vn.put("id", v.id());
                    vn.put("titulo", v.titulo());
                    vn.put("canal", v.canal());
                    arr.add(vn);
                }
                ((ObjectNode) passo).set("videos", arr);
            }
        }

        PlanoDanca p = new PlanoDanca();
        p.setStudio(studio);
        p.setCriadoPorEmail(email);
        p.setCriadoPorNome(nome);
        p.setParaQuem(paraQuem);
        p.setEstilo(estilo);
        p.setNivel(nivel);
        p.setObjetivo(objetivo);
        try {
            p.setConteudoJson(mapper.writeValueAsString(raiz));
        } catch (Exception e) {
            p.setConteudoJson(jsonPlano);
        }
        p.setChatJson("[]");
        return planoRepo.save(p);
    }

    // ---- Leitura ----

    public List<PlanoDanca> listar(boolean todosDoEstudio, String emailProprio) {
        Studio studio = TenantContext.getCurrentStudio();
        if (studio == null) {
            return List.of();
        }
        return todosDoEstudio
                ? planoRepo.findByStudioOrderByDataCriacaoDesc(studio)
                : planoRepo.findByStudioAndCriadoPorEmailOrderByDataCriacaoDesc(studio, emailProprio);
    }

    public PlanoDanca obter(Long id) {
        Studio studio = TenantContext.getCurrentStudio();
        PlanoDanca p = planoRepo.findById(id).orElse(null);
        if (p == null || studio == null || p.getStudio() == null
                || !studio.getId().equals(p.getStudio().getId())) {
            return null;
        }
        return p;
    }

    public PlanoConteudo conteudo(PlanoDanca p) {
        try {
            JsonNode r = mapper.readTree(p.getConteudoJson() == null ? "{}" : p.getConteudoJson());
            List<String> aquecimento = textos(r.path("aquecimento"));
            List<String> pratica = textos(r.path("praticaSemanal"));
            List<Passo> passos = new ArrayList<>();
            for (JsonNode pn : r.path("passos")) {
                List<YoutubeService.Video> vids = new ArrayList<>();
                for (JsonNode vn : pn.path("videos")) {
                    vids.add(new YoutubeService.Video(vn.path("id").asText(""),
                            vn.path("titulo").asText(""), vn.path("canal").asText("")));
                }
                passos.add(new Passo(pn.path("nome").asText(""), pn.path("descricao").asText(""),
                        pn.path("errosComuns").asText(""), pn.path("dica").asText(""),
                        pn.path("pesquisaYoutube").asText(""), vids));
            }
            List<Combinacao> combos = new ArrayList<>();
            for (JsonNode cn : r.path("combinacoes")) {
                combos.add(new Combinacao(cn.path("nome").asText(""), cn.path("descricao").asText("")));
            }
            int semanas = r.path("duracaoSemanas").asInt(0);
            return new PlanoConteudo(r.path("resumo").asText(""), semanas, aquecimento, passos, combos, pratica);
        } catch (Exception e) {
            return new PlanoConteudo("", 0, List.of(), List.of(), List.of(), List.of());
        }
    }

    private List<String> textos(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode n : arr) {
                out.add(n.asText(""));
            }
        }
        return out;
    }

    // ---- Progresso ----

    @Transactional
    public void marcarPasso(Long planoId, int idx, boolean dominado) {
        PlanoDanca p = obter(planoId);
        if (p != null) {
            p.definirPassoDominado(idx, dominado);
            planoRepo.save(p);
        }
    }

    @Transactional
    public void apagar(Long planoId) {
        PlanoDanca p = obter(planoId);
        if (p != null) {
            planoRepo.delete(p);
        }
    }

    // ---- Chat com o treinador ----

    public record MensagemChat(String autor, String texto) {
    }

    public List<MensagemChat> historicoChat(PlanoDanca p) {
        List<MensagemChat> out = new ArrayList<>();
        try {
            for (JsonNode n : mapper.readTree(p.getChatJson() == null ? "[]" : p.getChatJson())) {
                out.add(new MensagemChat(n.path("autor").asText("user"), n.path("texto").asText("")));
            }
        } catch (Exception ignore) {
            // histórico ilegível — recomeça vazio
        }
        return out;
    }

    // ---- Modo prática (feedback de movimento por câmara) ----

    private static final String SISTEMA_PRATICA = """
            És o treinador de dança que acompanha este aluno. Recebes MÉTRICAS
            aproximadas do movimento dele, captadas pela câmara durante uma
            prática curta de um passo — NÃO vês o vídeo. Dá 3 a 5 pontos de
            feedback curtos, concretos e encorajadores, em português de Portugal,
            ligados à técnica do passo. Se uma métrica estiver baixa, diz o que
            fazer para melhorar; se estiver boa, reforça. Não inventes pormenores
            visuais que as métricas não suportam. Termina com uma frase de ânimo.
            """;

    private static final String GUIA_METRICAS = """
            Como ler as métricas (0 a 1, salvo indicação):
            - movimento: quantidade de movimento (0 = parado, 1 = muito ativo)
            - amplitude: alcance dos braços e pernas (passos pequenos vs. amplos)
            - simetria: 1 = usa os dois lados do corpo por igual
            - postura: 1 = tronco direito e ombros nivelados
            - ritmo: fração de movimentos no tempo do metrónomo (null = sem metrónomo)
            - variabilidade: irregularidade da velocidade do movimento
            """;

    public String contextoPassoPratica(Long planoId, int passoIdx) {
        PlanoDanca p = obter(planoId);
        if (p == null) {
            return "";
        }
        PlanoConteudo c = conteudo(p);
        StringBuilder sb = new StringBuilder("Estilo ").append(p.getEstilo())
                .append(", nível ").append(p.getNivel()).append(". ");
        if (passoIdx >= 0 && passoIdx < c.passos().size()) {
            Passo passo = c.passos().get(passoIdx);
            sb.append("Passo: ").append(passo.nome()).append(". ").append(passo.descricao())
                    .append(" Erros comuns: ").append(passo.errosComuns())
                    .append(" Dica: ").append(passo.dica());
        }
        return sb.toString();
    }

    public String analisarMovimento(String contextoPasso, String metricasJson) {
        String prompt = "PASSO A PRATICAR: " + contextoPasso
                + "\n\nMÉTRICAS captadas (JSON): " + metricasJson
                + "\n\n" + GUIA_METRICAS;
        return groq.perguntar(SISTEMA_PRATICA, prompt);
    }

    @Transactional
    public String responder(Long planoId, String pergunta) {
        PlanoDanca p = obter(planoId);
        if (p == null) {
            throw new IllegalStateException("Plano não encontrado.");
        }
        PlanoConteudo c = conteudo(p);
        StringBuilder ctx = new StringBuilder();
        ctx.append("És o treinador de dança que criou este plano. Responde curto, prático e ")
                .append("motivador, em português de Portugal.\n\n")
                .append("PLANO — estilo ").append(p.getEstilo()).append(", nível ").append(p.getNivel())
                .append(". ").append(c.resumo()).append("\nPassos: ");
        for (Passo passo : c.passos()) {
            ctx.append("• ").append(passo.nome()).append("; ");
        }

        List<GroqService.ChatMsg> hist = new ArrayList<>();
        for (MensagemChat m : historicoChat(p)) {
            hist.add(new GroqService.ChatMsg(m.autor().equals("treinador") ? "assistant" : "user", m.texto()));
        }
        hist.add(GroqService.ChatMsg.utilizador(pergunta));

        String resposta = groq.conversar(ctx.toString(), hist, false);

        try {
            ArrayNode arr = (ArrayNode) mapper.readTree(p.getChatJson() == null ? "[]" : p.getChatJson());
            arr.add(mapper.createObjectNode().put("autor", "user").put("texto", pergunta));
            arr.add(mapper.createObjectNode().put("autor", "treinador").put("texto", resposta));
            p.setChatJson(mapper.writeValueAsString(arr));
            planoRepo.save(p);
        } catch (Exception e) {
            System.err.println("TreinadorDancaService: falha a guardar chat - " + e.getMessage());
        }
        return resposta;
    }
}
