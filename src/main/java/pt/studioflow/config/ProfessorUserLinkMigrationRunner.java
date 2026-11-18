package pt.studioflow.config;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Professor;
import pt.studioflow.model.User;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.UserRepository;

/**
 * Migração única para a FK explícita {@code professores.user_id}.
 *
 * <p>Antes, o professor do utilizador era adivinhado em runtime por email e,
 * em fallback, pelo primeiro nome. Agora a ligação é gravada. Para ninguém
 * perder o acesso às suas turmas no deploy, liga-se uma vez o que já
 * funcionava:
 * <ol>
 *   <li>email igual (mesmo estúdio), se for inequívoco;</li>
 *   <li>utilizadores PROF ainda soltos: primeiro nome = palavra exata do nome
 *       de <b>um único</b> professor solto do estúdio (a regra antiga).</li>
 * </ol>
 * Só corre enquanto não houver nenhuma ligação na BD — depois disso o nome
 * nunca mais é usado; novas ligações fazem-se em Professores.
 */
@Component
@Order(50)
public class ProfessorUserLinkMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProfessorUserLinkMigrationRunner.class);

    private final ProfessorRepository professorRepo;
    private final UserRepository userRepo;

    public ProfessorUserLinkMigrationRunner(ProfessorRepository professorRepo, UserRepository userRepo) {
        this.professorRepo = professorRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            if (professorRepo.countByUserIsNotNull() > 0) return;

            List<Professor> profs = professorRepo.findAll();
            List<User> users = userRepo.findAll().stream()
                    .filter(u -> u.getStudio() != null)
                    .filter(u -> !"SUPERADMIN".equals(u.getRole()) && !"ALUNO".equals(u.getRole()))
                    .collect(Collectors.toList());
            Set<Long> usados = new HashSet<>();
            int porEmail = 0, porNome = 0;

            // 1) email
            for (Professor p : profs) {
                if (p.getEmail() == null || p.getEmail().isBlank() || p.getStudio() == null) continue;
                String email = p.getEmail().trim();
                Long sid = p.getStudio().getId();
                List<User> cands = users.stream()
                        .filter(u -> !usados.contains(u.getId()))
                        .filter(u -> Objects.equals(u.getStudio().getId(), sid))
                        .filter(u -> u.getEmail() != null && u.getEmail().trim().equalsIgnoreCase(email))
                        .collect(Collectors.toList());
                if (cands.size() != 1) continue;
                // e o email não pode ser partilhado por dois professores do estúdio
                long profsComEmail = profs.stream()
                        .filter(o -> o.getStudio() != null && Objects.equals(o.getStudio().getId(), sid))
                        .filter(o -> o.getEmail() != null && o.getEmail().trim().equalsIgnoreCase(email))
                        .count();
                if (profsComEmail != 1) continue;
                ligar(p, cands.get(0), usados);
                porEmail++;
            }

            // 2) regra antiga do primeiro nome — só PROF, só inequívoco
            for (User u : users) {
                if (!"PROF".equals(u.getRole()) || usados.contains(u.getId())) continue;
                String primeiro = normalizar(u.getFirstName());
                if (primeiro.isBlank()) continue;
                Long sid = u.getStudio().getId();
                List<Professor> cands = new ArrayList<>();
                for (Professor p : profs) {
                    if (p.getUser() != null || p.getNome() == null || p.getStudio() == null) continue;
                    if (!Objects.equals(p.getStudio().getId(), sid)) continue;
                    String nome = normalizar(p.getNome());
                    if (nome.equals(primeiro) || Arrays.asList(nome.split("\\s+")).contains(primeiro)) cands.add(p);
                }
                if (cands.size() != 1) continue;
                ligar(cands.get(0), u, usados);
                porNome++;
            }

            log.info(">>> Migração professores.user_id: {} ligação(ões) por email, {} pelo nome (regra antiga).",
                    porEmail, porNome);
        } catch (Exception e) {
            log.warn(">>> Migração professores.user_id falhou: {}", e.getMessage());
        }
    }

    private void ligar(Professor p, User u, Set<Long> usados) {
        p.setUser(u);
        professorRepo.save(p);
        usados.add(u.getId());
        log.info(">>> Professor '{}' (id {}) ligado ao utilizador '{}' (id {}).",
                p.getNome(), p.getId(), u.getUsername(), u.getId());
    }

    private static String normalizar(String t) {
        return t == null ? ""
                : Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
