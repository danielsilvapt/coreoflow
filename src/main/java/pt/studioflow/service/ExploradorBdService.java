package pt.studioflow.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Explorador de base de dados (só leitura) para o superadmin.
 *
 * <p>Gera apenas SELECTs. Nomes de tabelas/colunas são sempre validados contra o
 * {@code information_schema} antes de entrarem no SQL; valores de filtro vão sempre
 * como parâmetros. A transação é {@code readOnly}, o que no MySQL abre a ligação em
 * modo READ ONLY — mesmo que algo escapasse, a BD recusaria escritas.</p>
 */
@Service
@Transactional(readOnly = true)
public class ExploradorBdService {

    /** Colunas cujo conteúdo nunca é mostrado (hashes, tokens, segredos). */
    private static final List<String> PADROES_SENSIVEIS = List.of(
            "password", "senha", "secret", "token", "api_key", "apikey", "hash", "otp", "totp");

    public static final String MASCARA = "••••••";

    private static final Set<String> TIPOS_TEXTO = Set.of(
            "char", "varchar", "tinytext", "text", "mediumtext", "longtext", "enum", "set");

    public record Tabela(String nome, long linhasAprox, String comentario) {}

    public record Coluna(String nome, String tipo, String tipoCompleto, boolean nulo,
                         boolean chavePrimaria, String fkTabela, String fkColuna) {
        public boolean texto() {
            return TIPOS_TEXTO.contains(tipo);
        }

        public boolean sensivel() {
            String n = nome.toLowerCase(Locale.ROOT);
            return PADROES_SENSIVEIS.stream().anyMatch(n::contains);
        }

        public boolean estrangeira() {
            return fkTabela != null;
        }
    }

    /** Uma tabela que referencia outra: {@code tabela.coluna -> alvo.colunaAlvo}. */
    public record Referencia(String tabela, String coluna, String colunaAlvo) {}

    public enum Operador {
        IGUAL("é igual a", true),
        DIFERENTE("é diferente de", true),
        CONTEM("contém", true),
        NAO_CONTEM("não contém", true),
        COMECA("começa por", true),
        TERMINA("termina em", true),
        MAIOR("maior que", true),
        MAIOR_IGUAL("maior ou igual a", true),
        MENOR("menor que", true),
        MENOR_IGUAL("menor ou igual a", true),
        VAZIO("está vazio", false),
        NAO_VAZIO("não está vazio", false);

        private final String label;
        private final boolean precisaValor;

        Operador(String label, boolean precisaValor) {
            this.label = label;
            this.precisaValor = precisaValor;
        }

        public String getLabel() {
            return label;
        }

        public boolean precisaValor() {
            return precisaValor;
        }
    }

    public record Filtro(String coluna, Operador operador, String valor) {
        public String descricao() {
            return coluna + " " + operador.getLabel() + (operador.precisaValor() ? " \"" + valor + "\"" : "");
        }
    }

    public record Ordem(String coluna, boolean ascendente) {}

    private final JdbcTemplate jdbc;
    private final Map<String, List<Coluna>> cacheColunas = new ConcurrentHashMap<>();
    private volatile Set<String> nomesTabelas;

    public ExploradorBdService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ esquema

    public List<Tabela> tabelas() {
        List<Tabela> res = jdbc.query("""
                SELECT TABLE_NAME, COALESCE(TABLE_ROWS, 0) AS LINHAS, TABLE_COMMENT
                  FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
                 ORDER BY TABLE_NAME""",
                (rs, i) -> new Tabela(rs.getString(1), rs.getLong(2), rs.getString(3)));
        nomesTabelas = res.stream().map(Tabela::nome).collect(Collectors.toUnmodifiableSet());
        return res;
    }

    /** Esquecer metadados em cache (depois de migrações, por exemplo). */
    public void limparCache() {
        cacheColunas.clear();
        nomesTabelas = null;
    }

    public List<Coluna> colunas(String tabela) {
        validarTabela(tabela);
        return cacheColunas.computeIfAbsent(tabela, t -> {
            Map<String, String[]> fks = new java.util.HashMap<>();
            jdbc.query("""
                    SELECT COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
                      FROM information_schema.KEY_COLUMN_USAGE
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                       AND REFERENCED_TABLE_NAME IS NOT NULL""",
                    rs -> {
                        fks.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3)});
                    }, t);
            return jdbc.query("""
                    SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
                      FROM information_schema.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                     ORDER BY ORDINAL_POSITION""",
                    (rs, i) -> {
                        String nome = rs.getString(1);
                        String[] fk = fks.get(nome);
                        return new Coluna(nome, rs.getString(2).toLowerCase(Locale.ROOT), rs.getString(3),
                                "YES".equals(rs.getString(4)), "PRI".equals(rs.getString(5)),
                                fk != null ? fk[0] : null, fk != null ? fk[1] : null);
                    }, t);
        });
    }

    /** Tabelas que têm chaves estrangeiras a apontar para {@code tabela}. */
    public List<Referencia> referenciadoPor(String tabela) {
        validarTabela(tabela);
        return jdbc.query("""
                SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_COLUMN_NAME
                  FROM information_schema.KEY_COLUMN_USAGE
                 WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = ?
                 ORDER BY TABLE_NAME, COLUMN_NAME""",
                (rs, i) -> new Referencia(rs.getString(1), rs.getString(2), rs.getString(3)), tabela);
    }

    /** Coluna "legível" de uma tabela (nome, titulo, email…) para mostrar ao lado de ids. */
    public Optional<String> colunaRotulo(String tabela) {
        List<String> nomes = colunas(tabela).stream().map(Coluna::nome).toList();
        for (String c : List.of("nome", "name", "titulo", "title", "designacao", "descricao", "username", "email", "slug")) {
            if (nomes.contains(c)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ dados

    public long contar(String tabela, List<Filtro> filtros, String pesquisa) {
        List<Object> params = new ArrayList<>();
        String where = construirWhere(tabela, filtros, pesquisa, params);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + q(tabela) + where, Long.class, params.toArray());
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> linhas(String tabela, List<Filtro> filtros, String pesquisa,
                                            List<Ordem> ordens, int offset, int limite) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(q(tabela))
                .append(construirWhere(tabela, filtros, pesquisa, params))
                .append(construirOrderBy(tabela, ordens))
                .append(" LIMIT ? OFFSET ?");
        params.add(Math.max(1, Math.min(limite, 1000)));
        params.add(Math.max(0, offset));
        return mascarar(tabela, jdbc.queryForList(sql.toString(), params.toArray()));
    }

    /** Percorre todas as linhas do filtro (até {@code maximo}) — usado na exportação. */
    public void percorrer(String tabela, List<Filtro> filtros, String pesquisa, List<Ordem> ordens,
                          int maximo, Consumer<Map<String, Object>> consumidor) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT * FROM " + q(tabela) + construirWhere(tabela, filtros, pesquisa, params)
                + construirOrderBy(tabela, ordens) + " LIMIT " + Math.max(1, maximo);
        Set<String> sensiveis = colunasSensiveis(tabela);
        jdbc.query(sql, rs -> {
            var md = rs.getMetaData();
            Map<String, Object> linha = new LinkedHashMap<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String nome = md.getColumnLabel(i);
                linha.put(nome, sensiveis.contains(nome) && rs.getObject(i) != null ? MASCARA : rs.getObject(i));
            }
            consumidor.accept(linha);
        }, params.toArray());
    }

    /** Devolve {id -> rótulo} para os ids dados, usando a coluna legível da tabela. */
    public Map<String, String> rotulos(String tabela, String colunaId, Set<Object> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Optional<String> rotulo = colunaRotulo(tabela);
        if (rotulo.isEmpty() || colunaDesconhecida(tabela, colunaId)) {
            return Map.of();
        }
        String marcadores = ids.stream().map(x -> "?").collect(Collectors.joining(","));
        Map<String, String> res = new java.util.HashMap<>();
        jdbc.query("SELECT " + q(colunaId) + ", " + q(rotulo.get()) + " FROM " + q(tabela)
                        + " WHERE " + q(colunaId) + " IN (" + marcadores + ")",
                rs -> {
                    res.put(String.valueOf(rs.getObject(1)), rs.getString(2));
                }, ids.toArray());
        return res;
    }

    // ------------------------------------------------------------------ interno

    private String construirWhere(String tabela, List<Filtro> filtros, String pesquisa, List<Object> params) {
        List<Coluna> cols = colunas(tabela);
        List<String> cond = new ArrayList<>();

        for (Filtro f : filtros == null ? List.<Filtro>of() : filtros) {
            if (colunaDesconhecida(tabela, f.coluna())) {
                throw new IllegalArgumentException("Coluna inválida: " + f.coluna());
            }
            String c = q(f.coluna());
            String v = f.valor() == null ? "" : f.valor();
            switch (f.operador()) {
                case IGUAL -> { cond.add(c + " = ?"); params.add(v); }
                case DIFERENTE -> { cond.add("(" + c + " <> ? OR " + c + " IS NULL)"); params.add(v); }
                case CONTEM -> { cond.add(c + " LIKE ?"); params.add("%" + like(v) + "%"); }
                case NAO_CONTEM -> { cond.add("(" + c + " NOT LIKE ? OR " + c + " IS NULL)"); params.add("%" + like(v) + "%"); }
                case COMECA -> { cond.add(c + " LIKE ?"); params.add(like(v) + "%"); }
                case TERMINA -> { cond.add(c + " LIKE ?"); params.add("%" + like(v)); }
                case MAIOR -> { cond.add(c + " > ?"); params.add(v); }
                case MAIOR_IGUAL -> { cond.add(c + " >= ?"); params.add(v); }
                case MENOR -> { cond.add(c + " < ?"); params.add(v); }
                case MENOR_IGUAL -> { cond.add(c + " <= ?"); params.add(v); }
                case VAZIO -> cond.add("(" + c + " IS NULL OR " + c + " = '')");
                case NAO_VAZIO -> cond.add("(" + c + " IS NOT NULL AND " + c + " <> '')");
            }
        }

        if (pesquisa != null && !pesquisa.isBlank()) {
            String termo = "%" + like(pesquisa.trim()) + "%";
            List<String> ors = new ArrayList<>();
            for (Coluna col : cols) {
                if (col.sensivel()) {
                    continue;
                }
                if (col.texto()) {
                    ors.add(q(col.nome()) + " LIKE ?");
                    params.add(termo);
                } else if (col.chavePrimaria() && pesquisa.trim().matches("\\d+")) {
                    ors.add(q(col.nome()) + " = ?");
                    params.add(pesquisa.trim());
                }
            }
            if (!ors.isEmpty()) {
                cond.add("(" + String.join(" OR ", ors) + ")");
            }
        }
        return cond.isEmpty() ? "" : " WHERE " + String.join(" AND ", cond);
    }

    private String construirOrderBy(String tabela, List<Ordem> ordens) {
        List<String> partes = new ArrayList<>();
        for (Ordem o : ordens == null ? List.<Ordem>of() : ordens) {
            if (!colunaDesconhecida(tabela, o.coluna())) {
                partes.add(q(o.coluna()) + (o.ascendente() ? " ASC" : " DESC"));
            }
        }
        if (partes.isEmpty()) {
            // por omissão: mais recentes primeiro pela chave primária
            colunas(tabela).stream().filter(Coluna::chavePrimaria).findFirst()
                    .ifPresent(pk -> partes.add(q(pk.nome()) + " DESC"));
        }
        return partes.isEmpty() ? "" : " ORDER BY " + String.join(", ", partes);
    }

    private List<Map<String, Object>> mascarar(String tabela, List<Map<String, Object>> linhas) {
        Set<String> sensiveis = colunasSensiveis(tabela);
        if (!sensiveis.isEmpty()) {
            for (Map<String, Object> l : linhas) {
                for (String s : sensiveis) {
                    if (l.get(s) != null) {
                        l.put(s, MASCARA);
                    }
                }
            }
        }
        return linhas;
    }

    private Set<String> colunasSensiveis(String tabela) {
        return colunas(tabela).stream().filter(Coluna::sensivel).map(Coluna::nome).collect(Collectors.toSet());
    }

    private void validarTabela(String tabela) {
        Set<String> nomes = nomesTabelas;
        if (nomes == null || !nomes.contains(tabela)) {
            tabelas(); // refresca a cache (pode ser uma tabela criada entretanto)
            nomes = nomesTabelas;
        }
        if (tabela == null || !nomes.contains(tabela)) {
            throw new IllegalArgumentException("Tabela inválida: " + tabela);
        }
    }

    private boolean colunaDesconhecida(String tabela, String coluna) {
        return colunas(tabela).stream().noneMatch(c -> c.nome().equals(coluna));
    }

    /** Cita um identificador já validado. */
    private static String q(String identificador) {
        return "`" + identificador.replace("`", "``") + "`";
    }

    private static String like(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
