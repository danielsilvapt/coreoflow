package pt.studioflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remove definitivamente um estúdio e todos os dados que dele dependem (SUPERADMIN).
 *
 * As dependências são descobertas a partir das chaves estrangeiras reais da BD
 * (information_schema), por isso novas tabelas ficam cobertas sem alterar este código:
 * parte-se de studios.id e segue-se cada FK para baixo até não haver novas linhas.
 *
 * Salvaguardas:
 * - se alguma linha a apagar pertencer a outro estúdio (coluna FK para studios com
 *   outro id), aborta sem apagar nada;
 * - tudo corre numa transação; depois de apagar verifica-se que não ficaram
 *   referências órfãs novas e, se ficarem, faz rollback.
 */
@Service
public class StudioRemocaoService {

    private static final Logger log = LoggerFactory.getLogger(StudioRemocaoService.class);
    private static final String TABELA_STUDIOS = "studios";
    private static final int LOTE = 1000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public StudioRemocaoService(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** FK de uma coluna: filho.coluna -> pai.colunaPai. */
    private record Fk(String filho, String coluna, String pai, String colunaPai) {
    }

    /** Linhas a apagar: por tabela, as condições "coluna IN valores" (OR entre elas). */
    private static final class Plano {
        final Map<String, Map<String, Set<Object>>> condicoes = new LinkedHashMap<>();

        boolean adicionar(String tabela, String coluna, Collection<Object> valores) {
            Set<Object> set = condicoes.computeIfAbsent(tabela, t -> new LinkedHashMap<>())
                    .computeIfAbsent(coluna, c -> new LinkedHashSet<>());
            return set.addAll(valores);
        }
    }

    /** Pré-visualização: nº de linhas a apagar por tabela (apenas tabelas com linhas). */
    public Map<String, Long> contarDados(Long studioId) {
        List<Fk> fks = carregarFks();
        Plano plano = planear(studioId, fks);
        Map<String, Long> contagens = new LinkedHashMap<>();
        for (String tabela : plano.condicoes.keySet()) {
            long n = contar(tabela, plano.condicoes.get(tabela));
            if (n > 0) {
                contagens.put(tabela, n);
            }
        }
        return contagens;
    }

    /**
     * Apaga o estúdio e todas as linhas dependentes. Lança IllegalStateException (sem
     * apagar nada) se encontrar dados de outro estúdio ou se ficassem referências órfãs.
     */
    public Map<String, Long> remover(Long studioId) {
        Map<String, Long> apagadas = tx.execute(status -> {
            List<Fk> fks = carregarFks();
            Plano plano = planear(studioId, fks);
            Set<String> tabelasAfetadas = plano.condicoes.keySet();

            // Referências órfãs que já existam antes (dados antigos) não devem bloquear a remoção
            Map<Fk, Long> orfasAntes = contarOrfas(fks, tabelasAfetadas);

            Map<String, Long> resultado = new LinkedHashMap<>();
            jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (Map.Entry<String, Map<String, Set<Object>>> e : plano.condicoes.entrySet()) {
                    long n = 0;
                    for (Map.Entry<String, Set<Object>> cond : e.getValue().entrySet()) {
                        for (List<Object> lote : lotes(cond.getValue())) {
                            n += jdbc.update("DELETE FROM " + q(e.getKey()) + " WHERE " + q(cond.getKey())
                                    + " IN (" + marcadores(lote.size()) + ")", lote.toArray());
                        }
                    }
                    if (n > 0) {
                        resultado.put(e.getKey(), n);
                    }
                }

                Map<Fk, Long> orfasDepois = contarOrfas(fks, tabelasAfetadas);
                for (Map.Entry<Fk, Long> o : orfasDepois.entrySet()) {
                    long antes = orfasAntes.getOrDefault(o.getKey(), 0L);
                    if (o.getValue() > antes) {
                        Fk fk = o.getKey();
                        throw new IllegalStateException("A remoção deixaria " + (o.getValue() - antes)
                                + " registo(s) de " + fk.filho() + "." + fk.coluna()
                                + " a apontar para " + fk.pai() + " apagado(s). Nada foi apagado.");
                    }
                }
            } finally {
                // a variável é da ligação, que volta ao pool: repor sempre
                jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return resultado;
        });
        log.warn("Estúdio {} removido definitivamente. Linhas apagadas: {}", studioId, apagadas);
        return apagadas;
    }

    // ---------- descoberta de dependências ----------

    private List<Fk> carregarFks() {
        return jdbc.query(
                "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME "
                        + "FROM information_schema.KEY_COLUMN_USAGE "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL",
                (rs, i) -> new Fk(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    private Plano planear(Long studioId, List<Fk> fks) {
        Plano plano = new Plano();
        plano.adicionar(TABELA_STUDIOS, "id", List.of(studioId));

        Map<String, List<Fk>> filhosPorPai = new HashMap<>();
        for (Fk fk : fks) {
            filhosPorPai.computeIfAbsent(fk.pai(), p -> new ArrayList<>()).add(fk);
        }

        // Propagar pelas FKs até não aparecerem novas linhas
        List<String> fila = new ArrayList<>(List.of(TABELA_STUDIOS));
        while (!fila.isEmpty()) {
            String pai = fila.remove(0);
            for (Fk fk : filhosPorPai.getOrDefault(pai, List.of())) {
                List<Object> valores = selecionarColuna(pai, fk.colunaPai(), plano.condicoes.get(pai));
                if (!valores.isEmpty() && plano.adicionar(fk.filho(), fk.coluna(), valores)
                        && !fila.contains(fk.filho())) {
                    fila.add(fk.filho());
                }
            }
        }

        // Salvaguarda: nenhuma linha a apagar pode pertencer a outro estúdio
        for (Fk fk : fks) {
            if (!TABELA_STUDIOS.equals(fk.pai()) || !plano.condicoes.containsKey(fk.filho())) {
                continue;
            }
            long alheias = contar(fk.filho(), plano.condicoes.get(fk.filho()),
                    q(fk.coluna()) + " IS NOT NULL AND " + q(fk.coluna()) + " <> ?", studioId);
            if (alheias > 0) {
                throw new IllegalStateException(alheias + " registo(s) de " + fk.filho()
                        + " ligados a este estúdio pertencem a outro estúdio. Nada foi apagado.");
            }
        }
        return plano;
    }

    private List<Object> selecionarColuna(String tabela, String coluna, Map<String, Set<Object>> condicoes) {
        Set<Object> valores = new LinkedHashSet<>();
        for (Map.Entry<String, Set<Object>> cond : condicoes.entrySet()) {
            for (List<Object> lote : lotes(cond.getValue())) {
                valores.addAll(jdbc.queryForList("SELECT DISTINCT " + q(coluna) + " FROM " + q(tabela)
                        + " WHERE " + q(cond.getKey()) + " IN (" + marcadores(lote.size()) + ") AND "
                        + q(coluna) + " IS NOT NULL", Object.class, lote.toArray()));
            }
        }
        return new ArrayList<>(valores);
    }

    private long contar(String tabela, Map<String, Set<Object>> condicoes) {
        return contar(tabela, condicoes, null);
    }

    /** Conta linhas distintas que cumprem alguma das condições (e o filtro extra, se dado). */
    private long contar(String tabela, Map<String, Set<Object>> condicoes, String extra, Object... extraArgs) {
        List<String> partes = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (Map.Entry<String, Set<Object>> cond : condicoes.entrySet()) {
            for (List<Object> lote : lotes(cond.getValue())) {
                partes.add(q(cond.getKey()) + " IN (" + marcadores(lote.size()) + ")");
                args.addAll(lote);
            }
        }
        String sql = "SELECT COUNT(*) FROM " + q(tabela) + " WHERE (" + String.join(" OR ", partes) + ")";
        if (extra != null) {
            sql += " AND " + extra;
            args.addAll(List.of(extraArgs));
        }
        Long n = jdbc.queryForObject(sql, Long.class, args.toArray());
        return n != null ? n : 0;
    }

    private Map<Fk, Long> contarOrfas(List<Fk> fks, Set<String> tabelasAfetadas) {
        Map<Fk, Long> orfas = new HashMap<>();
        for (Fk fk : fks) {
            if (!tabelasAfetadas.contains(fk.pai())) {
                continue;
            }
            Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + q(fk.filho()) + " c LEFT JOIN " + q(fk.pai())
                    + " p ON c." + q(fk.coluna()) + " = p." + q(fk.colunaPai())
                    + " WHERE c." + q(fk.coluna()) + " IS NOT NULL AND p." + q(fk.colunaPai()) + " IS NULL",
                    Long.class);
            if (n != null && n > 0) {
                orfas.put(fk, n);
            }
        }
        return orfas;
    }

    // ---------- utilitários SQL ----------

    private static String q(String identificador) {
        return "`" + identificador.replace("`", "``") + "`";
    }

    private static String marcadores(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static List<List<Object>> lotes(Set<Object> valores) {
        List<List<Object>> lotes = new ArrayList<>();
        List<Object> atual = new ArrayList<>(LOTE);
        for (Object v : valores) {
            atual.add(v);
            if (atual.size() == LOTE) {
                lotes.add(atual);
                atual = new ArrayList<>(LOTE);
            }
        }
        if (!atual.isEmpty()) {
            lotes.add(atual);
        }
        return lotes;
    }
}
