package pt.studioflow.config.security;

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import pt.studioflow.service.CustomUserDetailsService;

@Configuration
@EnableMethodSecurity(jsr250Enabled = true)
public class SecurityConfig extends VaadinWebSecurity {

    private final CustomUserDetailsService userDetailsService;
    private final DaoAuthenticationProvider authenticationProvider;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          DaoAuthenticationProvider authenticationProvider) {
        this.userDetailsService = userDetailsService;
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authenticationProvider(authenticationProvider);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/images/**", "/logos/**", "/icons/**", "/sw.js", "/manifest.webmanifest").permitAll());

        http.authorizeHttpRequests(auth ->
                auth.requestMatchers(new AntPathRequestMatcher("/inscricao")).permitAll());

        http.csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/api/convocatoria/**")));

        http.authorizeHttpRequests(auth ->
                auth.requestMatchers(new AntPathRequestMatcher("/api/convocatoria/**")).permitAll());

        super.configure(http);

        setLoginView(http, "login");

        // Garante que após login inválido (continue= com path errado) redireciona para /
        SavedRequestAwareAuthenticationSuccessHandler successHandler =
                new SavedRequestAwareAuthenticationSuccessHandler() {
                    @Override
                    protected String determineTargetUrl(
                            jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response) {
                        String target = super.determineTargetUrl(request, response);
                        // Se o URL alvo parecer um caminho de ficheiro ou recurso interno, vai para /
                        if (target != null && (target.contains("src/main") || target.contains("resources/static"))) {
                            return "/";
                        }
                        return target;
                    }
                };
        successHandler.setDefaultTargetUrl("/");
        http.formLogin(form -> form.successHandler(successHandler));


        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID"));

        http.csrf(csrf -> csrf.ignoringRequestMatchers("/logout"));
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new org.springframework.security.authentication.ProviderManager(authenticationProvider);
    }

}
