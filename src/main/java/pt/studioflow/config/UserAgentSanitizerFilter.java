package pt.studioflow.config;

import com.vaadin.flow.shared.BrowserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Protege o Vaadin de User-Agents que rebentam o parser {@link BrowserDetails}.
 *
 * Bug do Vaadin 24.6.x: em {@code parseIOSVersion} faz
 * {@code substring(indexOf("os ") + 3, indexOf(" like mac"))} sem verificar a ordem.
 * Num UA de iPhone/iPad sem versão (ex. o UA do iPhone OS 1.0
 * "Mozilla/5.0 (iPhone; U; CPU like Mac OS X; en) ..." usado por scanners/bots),
 * o primeiro "os " é o de "Mac OS X", que vem DEPOIS de " like mac" →
 * StringIndexOutOfBoundsException ao criar a VaadinSession → 500 + alerta.
 *
 * Aqui testamos o UA com o mesmo parser; se rebentar, substituímos por um UA
 * genérico para esse pedido. Corre antes de tudo (incl. Spring Security).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserAgentSanitizerFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserAgentSanitizerFilter.class);
    private static final String HEADER = "User-Agent";
    static final String FALLBACK_UA = "Mozilla/5.0 (compatible; sanitized)";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ua = request.getHeader(HEADER);
        if (ua == null || isParseable(ua)) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("User-Agent inválido para o Vaadin, substituído (ip={}): {}", request.getRemoteAddr(), ua);
        chain.doFilter(new FixedUserAgentRequest(request), response);
    }

    static boolean isParseable(String ua) {
        try {
            new BrowserDetails(ua) {
                @Override
                protected void log(String error, Exception e) {
                    // silencioso: só queremos saber se rebenta
                }
            };
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static final class FixedUserAgentRequest extends HttpServletRequestWrapper {
        FixedUserAgentRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? FALLBACK_UA : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name)
                    ? Collections.enumeration(Collections.singletonList(FALLBACK_UA))
                    : super.getHeaders(name);
        }
    }
}
