package pt.studioflow.config.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Põe o utilizador autenticado no MDC (chave "user") para aparecer em todas as
 * linhas de log geradas durante o pedido — incluindo as da framework (Vaadin,
 * Hibernate, etc.), não só as nossas. authentication.getName() já vem no
 * formato "estudio:username" (ver CustomUserDetailsService), por isso identifica
 * estúdio + utilizador numa só string.
 *
 * @Order(LOWEST_PRECEDENCE) garante que corre depois do filtro do Spring Security,
 * para o SecurityContext já estar preenchido quando lemos a autenticação.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MdcUserFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            MDC.put(MDC_KEY, resolveUser());
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "anonymous";
        }
        return auth.getName();
    }
}
