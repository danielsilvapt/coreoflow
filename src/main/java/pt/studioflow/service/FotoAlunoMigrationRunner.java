package pt.studioflow.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

/**
 * Migração única (corre a cada arranque, idempotente): fotos de alunos que
 * ainda estão na coluna legada "foto" (LONGBLOB) são enviadas para o R2 e
 * passam a ser referenciadas por {@link Aluno#getFotoChave()}. Se o R2 ainda
 * não estiver configurado, falha em silêncio por aluno e tenta de novo no
 * próximo arranque.
 */
@Component
public class FotoAlunoMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final FotoAlunoMigrador migrador;

    public FotoAlunoMigrationRunner(JdbcTemplate jdbcTemplate, FotoAlunoMigrador migrador) {
        this.jdbcTemplate = jdbcTemplate;
        this.migrador = migrador;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Long> pendentes;
        try {
            pendentes = jdbcTemplate.queryForList(
                    "SELECT id FROM aluno WHERE foto IS NOT NULL AND (foto_chave IS NULL OR foto_chave = '')",
                    Long.class);
        } catch (Exception e) {
            // Coluna "foto" já não existe (BD nova, ou já foi limpa manualmente) — nada a migrar
            return;
        }
        if (pendentes.isEmpty()) return;

        System.out.println("Migração de fotos: " + pendentes.size() + " aluno(s) com foto ainda por migrar para o R2.");
        int migrados = 0;
        for (Long id : pendentes) {
            try {
                if (migrador.migrarAluno(id)) migrados++;
            } catch (Exception e) {
                System.err.println("Migração de fotos: falhou para o aluno " + id + " — " + e.getMessage()
                        + " (tenta novamente no próximo arranque)");
            }
        }
        System.out.println("Migração de fotos: " + migrados + " de " + pendentes.size() + " concluída(s).");
    }
}

/**
 * Migra a foto de um aluno numa transação própria (sessão Hibernate aberta),
 * para que {@code aluno.getStudio().getSlug()} — lazy — funcione, e para que
 * cada aluno seja atómico (uma falha não afeta os outros).
 */
@Service
class FotoAlunoMigrador {

    private final JdbcTemplate jdbcTemplate;
    private final AlunoRepository alunoRepository;
    private final R2StorageService storageService;

    FotoAlunoMigrador(JdbcTemplate jdbcTemplate, AlunoRepository alunoRepository, R2StorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.alunoRepository = alunoRepository;
        this.storageService = storageService;
    }

    /** @return true se a foto foi migrada, false se não havia nada a fazer. */
    @Transactional
    public boolean migrarAluno(Long id) {
        byte[] bytes = jdbcTemplate.queryForObject("SELECT foto FROM aluno WHERE id = ?", byte[].class, id);
        if (bytes == null || bytes.length == 0) return false;

        Aluno aluno = alunoRepository.findById(id).orElse(null);
        if (aluno == null) return false;

        Studio studio = aluno.getStudio();
        String slug = studio != null && studio.getSlug() != null ? studio.getSlug() : "sem-estudio";
        String chave = "alunos/" + slug + "/" + UUID.randomUUID() + ".jpg";
        storageService.upload(chave, "image/jpeg", new ByteArrayInputStream(bytes), bytes.length);

        aluno.setFotoChave(chave);
        alunoRepository.save(aluno);
        return true;
    }
}
