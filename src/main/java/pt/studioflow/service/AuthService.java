package pt.studioflow.service;

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UserDetails;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.ContaPortal;
import pt.studioflow.model.Studio;
import pt.studioflow.model.User;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.ContaPortalRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.UserRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private static final String CHAVE_ALUNO_SELECIONADO = "coreoflow.portal.alunoSelecionadoId";

    private final AuthenticationContext authenticationContext;
    private final AlunoRepository alunoRepository;
    private final UserRepository userRepository;
    private final StudioRepository studioRepository;
    private final ContaPortalRepository contaPortalRepository;

    public AuthService(AuthenticationContext authenticationContext,
                       AlunoRepository alunoRepository,
                       UserRepository userRepository,
                       StudioRepository studioRepository,
                       ContaPortalRepository contaPortalRepository) {
        this.authenticationContext = authenticationContext;
        this.alunoRepository = alunoRepository;
        this.userRepository = userRepository;
        this.studioRepository = studioRepository;
        this.contaPortalRepository = contaPortalRepository;
    }

    /**
     * Inicializa o TenantContext na sessão com base no utilizador autenticado.
     * Deve ser chamado após o login (ex: no MainLayout).
     */
    public void initTenantContext() {
        Optional<UserDetails> userDetails = authenticationContext.getAuthenticatedUser(UserDetails.class);
        if (userDetails.isEmpty()) return;

        String usernameInput = userDetails.get().getUsername();

        if (usernameInput.contains(CustomUserDetailsService.TENANT_SEPARATOR)) {
            String slug = usernameInput.split(CustomUserDetailsService.TENANT_SEPARATOR, 2)[0];
            studioRepository.findBySlugAndAtivoTrue(slug).ifPresent(TenantContext::setCurrentStudio);
        }
        // SUPERADMIN não tem studio - TenantContext fica null
    }

    /**
     * Retorna o Studio atual da sessão.
     */
    public Studio getCurrentStudio() {
        return TenantContext.getCurrentStudio();
    }

    /**
     * Retorna o utilizador User logado.
     */
    public Optional<User> getCurrentUser() {
        Optional<UserDetails> userDetails = authenticationContext.getAuthenticatedUser(UserDetails.class);
        if (userDetails.isEmpty()) return Optional.empty();

        String usernameInput = userDetails.get().getUsername();
        Studio studio = TenantContext.getCurrentStudio();

        if (studio != null && usernameInput.contains(CustomUserDetailsService.TENANT_SEPARATOR)) {
            String username = usernameInput.split(CustomUserDetailsService.TENANT_SEPARATOR, 2)[1];
            return userRepository.findByUsernameAndStudio(username, studio);
        }
        return userRepository.findByUsername(usernameInput);
    }

    /** Email do principal autenticado (tira o prefixo "slug:" quando existe). */
    private String emailDoPrincipal() {
        Optional<UserDetails> userDetails = authenticationContext.getAuthenticatedUser(UserDetails.class);
        if (userDetails.isEmpty()) return null;
        String username = userDetails.get().getUsername();
        return username.contains(CustomUserDetailsService.TENANT_SEPARATOR)
                ? username.split(CustomUserDetailsService.TENANT_SEPARATOR, 2)[1]
                : username;
    }

    /**
     * Conta do portal (aluno/encarregado) autenticada, se o login foi feito
     * por email.
     */
    public Optional<ContaPortal> getContaPortalLogado() {
        String email = emailDoPrincipal();
        return email == null ? Optional.empty() : contaPortalRepository.findByEmailIgnoreCase(email);
    }

    /**
     * Todos os alunos do estúdio associados ao email autenticado (um email de
     * pai pode ter vários filhos), ordenados por nome.
     */
    public List<Aluno> getAlunosDoPortal() {
        String email = emailDoPrincipal();
        Studio studio = TenantContext.getCurrentStudio();
        if (email == null || studio == null) return List.of();
        return alunoRepository.findByEmailAndStudioWithTurmas(email, studio).stream()
                .sorted(Comparator.comparing(a -> a.getNomeCompleto() == null ? "" : a.getNomeCompleto(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Guarda na sessão qual dos filhos está a ser visto. */
    public void setAlunoSelecionado(Long alunoId) {
        VaadinSession sessao = VaadinSession.getCurrent();
        if (sessao != null) sessao.setAttribute(CHAVE_ALUNO_SELECIONADO, alunoId);
    }

    /**
     * Aluno atualmente selecionado no portal (por omissão, o primeiro filho).
     * É este o "aluno logado" para todas as views do portal.
     */
    public Aluno getAlunoSelecionado() {
        List<Aluno> alunos = getAlunosDoPortal();
        if (alunos.isEmpty()) return null;
        VaadinSession sessao = VaadinSession.getCurrent();
        Object selecionado = sessao != null ? sessao.getAttribute(CHAVE_ALUNO_SELECIONADO) : null;
        if (selecionado instanceof Long id) {
            return alunos.stream().filter(a -> id.equals(a.getId())).findFirst().orElse(alunos.get(0));
        }
        return alunos.get(0);
    }

    /**
     * Retorna o Aluno logado no portal. Mantido pelo nome antigo para as views
     * existentes; delega no aluno selecionado (multi-filho).
     */
    public Aluno getAlunoLogado() {
        return getAlunoSelecionado();
    }

    public boolean isAuthenticated() {
        return authenticationContext.isAuthenticated();
    }

    public boolean isSuperAdmin() {
        Optional<UserDetails> userDetails = authenticationContext.getAuthenticatedUser(UserDetails.class);
        if (userDetails.isEmpty()) return false;
        return userDetails.get().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
    }

    public void logout() {
        TenantContext.clear();
        authenticationContext.logout();
    }
}
