package pt.studioflow.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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
 * associação professor↔utilizador é feita por <b>email</b> ({@code User.email ==
 * Professor.email}) e, em fallback, por <b>palavra exata</b> do primeiro nome
 * desde que identifique um único professor — nunca por {@code contains}, para
 * "Ana" não abrir as turmas da "Mariana" nem da "Ana Maria Costa".
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

    /**
     * O {@link Professor} correspondente ao utilizador com sessão iniciada.
     *
     * <p>1) {@code User.email == Professor.email} — se houver, é esse e só esse
     * (o fallback por nome nunca acrescenta outros professores);
     * 2) fallback por nome: o {@code User.firstName} tem de ser igual ao nome
     * completo do professor ou a uma palavra exata dele, e só conta se houver
     * <b>um único</b> professor nessas condições — "Ana" com "Ana Rodrigues" e
     * "Ana Maria Costa" no estúdio não resolve para nenhuma.
     */
    public Optional<Professor> professorDoUtilizador(Studio studio) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User u = auth == null ? null : userRepo.findByPrincipalName(auth.getName()).orElse(null);
        if (u == null) return Optional.empty();
        List<Professor> profs = studio != null ? professorRepo.findAllByStudio(studio) : professorRepo.findAll();

        String emailUser = u.getEmail() != null ? u.getEmail().trim() : "";
        if (!emailUser.isBlank()) {
            Optional<Professor> porEmail = profs.stream()
                    .filter(p -> p.getEmail() != null && p.getEmail().trim().equalsIgnoreCase(emailUser))
                    .findFirst();
            if (porEmail.isPresent()) return porEmail;
        }

        String primeiroNome = normalizar(u.getFirstName());
        if (primeiroNome.isBlank()) return Optional.empty();
        List<Professor> candidatos = profs.stream()
                .filter(p -> p.getNome() != null)
                .filter(p -> {
                    String nome = normalizar(p.getNome());
                    return nome.equals(primeiroNome) || Arrays.asList(nome.split("\\s+")).contains(primeiroNome);
                })
                .collect(Collectors.toList());
        return candidatos.size() == 1 ? Optional.of(candidatos.get(0)) : Optional.empty();
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
