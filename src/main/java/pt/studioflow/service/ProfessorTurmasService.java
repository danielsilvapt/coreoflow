package pt.studioflow.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Aula;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.model.User;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.UserRepository;

/**
 * Resolve as turmas em que o utilizador com sessão iniciada leciona, para as
 * áreas partilhadas entre ADMIN e PROF (vídeos das aulas, avaliações, …).
 *
 * <p>O ADMIN vê tudo; um PROF (sem ADMIN) fica restrito às suas turmas. A
 * associação professor↔utilizador é a FK explícita {@code professores.user_id}
 * (definida em Professores → "Utilizador (login)"). Enquanto um professor não
 * estiver ligado, aceita-se o email ({@code User.email == Professor.email}, um
 * único professor sem conta) e a ligação fica gravada. <b>Nunca pelo nome.</b>
 */
@Component
public class ProfessorTurmasService {

    private final UserRepository userRepo;
    private final TurmaRepository turmaRepo;
    private final ProfessorRepository professorRepo;

    public ProfessorTurmasService(UserRepository userRepo, TurmaRepository turmaRepo,
            ProfessorRepository professorRepo) {
        this.userRepo = userRepo;
        this.turmaRepo = turmaRepo;
        this.professorRepo = professorRepo;
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

    /** O {@link User} com sessão iniciada (ou null). */
    public User utilizadorAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : userRepo.findByPrincipalName(auth.getName()).orElse(null);
    }

    /** O {@link Professor} correspondente ao utilizador com sessão iniciada. */
    public Optional<Professor> professorDoUtilizador(Studio studio) {
        return professorDe(utilizadorAtual(), studio);
    }

    /**
     * O {@link Professor} ligado a {@code u} neste estúdio.
     *
     * <p>1) FK {@code professores.user_id}; 2) se ainda não houver ligação, o
     * professor do estúdio <b>sem conta</b> com o mesmo email — só se for único —
     * e a ligação é gravada para as vezes seguintes.
     */
    @Transactional
    public Optional<Professor> professorDe(User u, Studio studio) {
        if (u == null || u.getId() == null) return Optional.empty();

        Optional<Professor> ligado = professorRepo.findByUser(u)
                .filter(p -> mesmoEstudio(p, studio));
        if (ligado.isPresent()) return ligado;

        String emailUser = u.getEmail() != null ? u.getEmail().trim() : "";
        if (emailUser.isBlank()) return Optional.empty();
        List<Professor> profs = studio != null ? professorRepo.findAllByStudio(studio) : professorRepo.findAll();
        List<Professor> porEmail = profs.stream()
                .filter(p -> p.getUser() == null)
                .filter(p -> p.getEmail() != null && p.getEmail().trim().equalsIgnoreCase(emailUser))
                .collect(Collectors.toList());
        if (porEmail.size() != 1) return Optional.empty();
        Professor p = porEmail.get(0);
        // Só liga se o utilizador ainda não estiver ligado a outro professor (unique).
        if (professorRepo.findByUser(u).isEmpty()) {
            p.setUser(u);
            professorRepo.save(p);
        }
        return Optional.of(p);
    }

    private static boolean mesmoEstudio(Professor p, Studio studio) {
        if (studio == null || studio.getId() == null) return true;
        return p.getStudio() != null && studio.getId().equals(p.getStudio().getId());
    }

    /**
     * true se um nome de professor gravado como texto (marcações de sala,
     * registo de horas) pertence a {@code prof}: nome igual (sem acentos/caixa)
     * ou o username do utilizador (há registos antigos gravados assim, com ou
     * sem prefixo {@code slug:}). Nunca por {@code contains}.
     */
    public static boolean nomeGravadoCorresponde(String gravado, Professor prof, String username) {
        String g = normalizar(gravado);
        if (g.isBlank()) return false;
        if (prof != null && g.equals(normalizar(prof.getNome()))) return true;
        if (username != null && !username.isBlank()) {
            String user = normalizar(username.contains(":") ? username.split(":", 2)[1] : username);
            String gSemSlug = g.contains(":") ? g.split(":", 2)[1] : g;
            return gSemSlug.equals(user);
        }
        return false;
    }

    /**
     * Turmas do estúdio em que o utilizador atual leciona: professor principal,
     * co-professor, ou professor de pelo menos uma {@link Aula} da turma (dia
     * específico de uma turma partilhada) — dá acesso à turma inteira, como já
     * acontece com os co-professores.
     */
    public List<Turma> turmasDoUtilizador(Studio studio) {
        Professor prof = professorDoUtilizador(studio).orElse(null);
        if (prof == null) return List.of();
        Long id = prof.getId();
        List<Turma> todas = studio != null ? turmaRepo.findAllByStudio(studio) : turmaRepo.findAllComplete();
        return todas.stream()
                .filter(t -> t.getTodosProfessores().stream().anyMatch(p -> p != null && id.equals(p.getId()))
                        || (t.getAulas() != null && t.getAulas().stream()
                                .map(Aula::getProfessorEfetivo)
                                .anyMatch(p -> p != null && id.equals(p.getId()))))
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

    static String normalizar(String t) {
        return t == null ? ""
                : Normalizer.normalize(t, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
