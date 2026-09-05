package pt.studioflow.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
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
    private final AlunoRepository alunoRepository;
    private final R2StorageService storageService;

    public FotoAlunoMigrationRunner(JdbcTemplate jdbcTemplate, AlunoRepository alunoRepository,
            R2StorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.alunoRepository = alunoRepository;
        this.storageService = storageService;
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
                byte[] bytes = jdbcTemplate.queryForObject("SELECT foto FROM aluno WHERE id = ?", byte[].class, id);
                if (bytes == null || bytes.length == 0) continue;

                Aluno aluno = alunoRepository.findById(id).orElse(null);
                if (aluno == null) continue;

                Studio studio = aluno.getStudio();
                String chave = "alunos/" + (studio != null ? studio.getSlug() : "sem-estudio")
                        + "/" + UUID.randomUUID() + ".jpg";
                storageService.upload(chave, "image/jpeg", new ByteArrayInputStream(bytes), bytes.length);

                aluno.setFotoChave(chave);
                alunoRepository.save(aluno);
                migrados++;
            } catch (Exception e) {
                System.err.println("Migração de fotos: falhou para o aluno " + id + " — " + e.getMessage()
                        + " (tenta novamente no próximo arranque)");
            }
        }
        System.out.println("Migração de fotos: " + migrados + " de " + pendentes.size() + " concluída(s).");
    }
}
