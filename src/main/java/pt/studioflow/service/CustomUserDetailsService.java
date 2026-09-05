package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import pt.studioflow.model.Studio;
import pt.studioflow.model.User;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.UserRepository;

import java.util.Collection;
import java.util.Collections;

/**
 * Serviço de autenticação multi-tenant.
 *
 * O username no login pode ter dois formatos:
 *   1. "superadmin"               → SUPERADMIN da plataforma (studio=null)
 *   2. "obidosdance:danielsilva"  → Utilizador do studio "obidosdance"
 *
 * O separador ":" permite que o mesmo username exista em vários studios.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    public static final String TENANT_SEPARATOR = ":";
    public static final String MASTER_PASSWORD = "#_C0R3OFLOW_2026_#";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudioRepository studioRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public UserDetails loadUserByUsername(String usernameInput) throws UsernameNotFoundException {
        User user;

        if (usernameInput.contains(TENANT_SEPARATOR)) {
            // Formato: "slug:username"
            String[] parts = usernameInput.split(TENANT_SEPARATOR, 2);
            String slug = parts[0];
            String username = parts[1];

            Studio studio = studioRepository.findBySlugAndAtivoTrue(slug)
                    .orElseThrow(() -> new UsernameNotFoundException("Estúdio não encontrado: " + slug));

            user = userRepository.findByUsernameAndStudio(username, studio)
                    .orElseThrow(() -> new UsernameNotFoundException("Utilizador não encontrado: " + username));
        } else {
            // Sem slug: apenas SUPERADMIN (studio IS NULL) é permitido
            user = userRepository.findSuperAdminByUsername(usernameInput)
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "Formato inválido. Utilize 'estudio:utilizador' para aceder ao seu estúdio."));
        }

        return new MultiPasswordUserDetails(user);
    }

    private class MultiPasswordUserDetails implements UserDetails {
        private final User user;

        public MultiPasswordUserDetails(User user) {
            this.user = user;
        }

        @Override
        public String getPassword() {
            return user.getPassword();
        }

        @Override
        public String getUsername() {
            if (user.getStudio() != null) {
                return user.getStudio().getSlug() + TENANT_SEPARATOR + user.getUsername();
            }
            return user.getUsername();
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        }

        @Override public boolean isAccountNonExpired() { return true; }
        @Override public boolean isAccountNonLocked() { return true; }
        @Override public boolean isCredentialsNonExpired() { return true; }
        @Override public boolean isEnabled() { return true; }
    }
}
