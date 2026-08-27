package pt.studioflow.service;

import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.UserDetails;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Studio;
import pt.studioflow.model.User;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.UserRepository;

import java.util.Optional;

@Service
public class AuthService {

    private final AuthenticationContext authenticationContext;
    private final AlunoRepository alunoRepository;
    private final UserRepository userRepository;
    private final StudioRepository studioRepository;

    public AuthService(AuthenticationContext authenticationContext,
                       AlunoRepository alunoRepository,
                       UserRepository userRepository,
                       StudioRepository studioRepository) {
        this.authenticationContext = authenticationContext;
        this.alunoRepository = alunoRepository;
        this.userRepository = userRepository;
        this.studioRepository = studioRepository;
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

    /**
     * Retorna o Aluno logado (para utilizadores com role ALUNO).
     */
    public Aluno getAlunoLogado() {
        Optional<UserDetails> userDetails = authenticationContext.getAuthenticatedUser(UserDetails.class);
        if (userDetails.isEmpty()) return null;

        // O email do aluno está associado ao email do User
        Optional<User> user = getCurrentUser();
        if (user.isEmpty()) return null;

        Studio studio = TenantContext.getCurrentStudio();
        if (studio == null) return null;

        return alunoRepository.findByEmailAndStudioWithTurmas(user.get().getEmail(), studio)
                .stream().findFirst().orElse(null);
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
