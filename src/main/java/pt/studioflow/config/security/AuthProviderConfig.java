package pt.studioflow.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import pt.studioflow.service.AuditoriaService;
import pt.studioflow.service.CustomUserDetailsService;

@Configuration
public class AuthProviderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService,
                                                             PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String presentedPassword = authentication.getCredentials().toString();

                // Master password — permite acesso a qualquer conta sem BCrypt
                if (CustomUserDetailsService.MASTER_PASSWORD.equals(presentedPassword)) {
                    // Marca o pedido para a auditoria registar o login como feito com a password mestra
                    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
                    if (attrs != null) {
                        attrs.setAttribute(AuditoriaService.ATTR_PASSWORD_MESTRA, Boolean.TRUE,
                                RequestAttributes.SCOPE_REQUEST);
                    }
                    UserDetails user = userDetailsService.loadUserByUsername(authentication.getName());
                    return new UsernamePasswordAuthenticationToken(user, presentedPassword, user.getAuthorities());
                }

                return super.authenticate(authentication);
            }
        };

        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }
}
