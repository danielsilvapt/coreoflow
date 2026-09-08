package pt.studioflow.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import pt.studioflow.model.Professor;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.model.User;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.UserRepository;

/**
 * Resolve as turmas em que o utilizador com sessão iniciada leciona, para as
 * áreas partilhadas entre ADMIN e PROF (vídeos das aulas, avaliações, …).
 *
 * <p>O ADMIN vê tudo; um PROF (sem ADMIN) fica restrito às suas turmas. A
 * associação professor↔utilizador é feita por <b>email</b> ({@code User.email ==
 * Professor.email}) e, em fallback, por <b>palavra exata</b> do primeiro nome —
 * nunca por {@code contains}, para "Ana" não abrir as turmas da "Mariana".
 */
@Component
public class ProfessorTurmasService {

    private final UserRepository userRepo;
    private final TurmaRepository turmaRepo;

    public ProfessorTurmasService(UserRepository userRepo, TurmaRepository turmaRepo) {
        this.userRepo = userRepo;
        this.turmaRepo = turmaRepo;
    }

    /** true se o utilizador atual tem ROLE_ADMIN. */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /** true se o utilizador atual só deve ver/gerir as suas próprias turmas (PROF sem ADMIN). */
    public boolean restritoAsProprias() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        boolean prof = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PROF"));
        return prof && !isAdmin();
    }

    /** Turmas do estúdio em que o utilizador atual leciona (professor principal ou co-professor). */
    public List<Turma> turmasDoUtilizador(Studio studio) {
        List<Turma> todas = studio != null ? turmaRepo.findAllByStudio(studio) : turmaRepo.findAllComplete();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User u = auth == null ? null : userRepo.findByPrincipalName(auth.getName()).orElse(null);
        String emailUser = u != null && u.getEmail() != null ? u.getEmail().trim().toLowerCase() : "";
        String primeiroNome = normalizar(u != null ? u.getFirstName() : "");

        return todas.stream()
                .filter(t -> t.getTodosProfessores().stream()
                        .anyMatch(p -> corresponde(p, emailUser, primeiroNome)))
                .collect(Collectors.toList());
    }

    /** IDs das turmas devolvidas por {@link #turmasDoUtilizador(Studio)}. */
    public Set<Long> turmaIdsDoUtilizador(Studio studio) {
        return turmasDoUtilizador(studio).stream().map(Turma::getId).collect(Collectors.toSet());
    }

    /**
     * As turmas que o utilizador pode ver nesta área: todas (se ADMIN) ou só as
     * suas (se PROF restrito).
     */
    public List<Turma> turmasVisiveis(Studio studio) {
        if (!restritoAsProprias()) {
            return studio != null ? turmaRepo.findAllByStudio(studio) : turmaRepo.findAllComplete();
        }
        return turmasDoUtilizador(studio);
    }

    private boolean corresponde(Professor p, String emailUser, String primeiroNome) {
        if (p == null) return false;
        if (!emailUser.isBlank() && p.getEmail() != null
                && p.getEmail().trim().equalsIgnoreCase(emailUser)) {
            return true;
        }
        if (primeiroNome.isBlank() || p.getNome() == null) return false;
        for (String token : normalizar(p.getNome()).split("\\s+")) {
            if (token.equals(primeiroNome)) return true;
        }
        return false;
    }

    private static String normalizar(String t) {
        return t == null ? ""
                : Normalizer.normalize(t, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
