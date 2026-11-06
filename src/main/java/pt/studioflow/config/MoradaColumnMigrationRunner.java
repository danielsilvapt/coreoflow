package pt.studioflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migração única: a coluna aluno.morada era VARCHAR(255), mas a inscrição pública
 * acrescenta "[Interesses: ...]" com todas as turmas escolhidas — com várias turmas
 * rebentava com "Data too long for column 'morada'". O ddl-auto=update não alarga
 * colunas existentes, por isso convertemos para TEXT aqui. Idempotente.
 */
@Component
@Order(40)
public class MoradaColumnMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MoradaColumnMigrationRunner.class);

    private final JdbcTemplate jdbc;

    public MoradaColumnMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> tipos = jdbc.queryForList(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'aluno' AND COLUMN_NAME = 'morada'",
                    String.class);
            if (tipos.isEmpty() || !"varchar".equalsIgnoreCase(tipos.get(0))) return;
            jdbc.execute("ALTER TABLE aluno MODIFY COLUMN morada TEXT");
            log.info(">>> Migração: aluno.morada alargada de VARCHAR para TEXT.");
        } catch (Exception e) {
            log.warn(">>> Migração aluno.morada falhou: {}", e.getMessage());
        }
    }
}
