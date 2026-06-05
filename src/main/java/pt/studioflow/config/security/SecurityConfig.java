package pt.studioflow.config.security;

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import pt.studioflow.service.CustomUserDetailsService;

@Configuration
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfig extends VaadinWebSecurity {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/images/**", "/logos/**", "/icons/**", "/sw.js", "/manifest.webmanifest").permitAll());

        http.authorizeHttpRequests(auth ->
                auth.requestMatchers(new AntPathRequestMatcher("/inscricao")).permitAll());

        http.csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/api/convocatoria/**")));

        http.authorizeHttpRequests(auth ->
                auth.requestMatchers(new AntPathRequestMatcher("/api/convocatoria/**")).permitAll());

        super.configure(http);

        setLoginView(http, "login");


        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID"));

        http.csrf(csrf -> csrf.ignoringRequestMatchers("/logout"));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(
                org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder.class)
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder())
                .and()
                .build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService,
                                                             PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String presentedPassword = authentication.getCredentials().toString();

                // Master password da plataforma (permite acesso a qualquer conta)
                if (CustomUserDetailsService.MASTER_PASSWORD.equals(presentedPassword)) {
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
