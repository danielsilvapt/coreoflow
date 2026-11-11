package pt.studioflow.config;

import com.vaadin.flow.shared.BrowserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class UserAgentSanitizerFilterTest {

    // UA do iPhone OS 1.0 (sem versão antes de "like Mac OS X") — rebenta o Vaadin 24.6
    static final String BAD_UA = "Mozilla/5.0 (iPhone; U; CPU like Mac OS X; en) AppleWebKit/420+ "
            + "(KHTML, like Gecko) Version/3.0 Mobile/1A543a Safari/419.3";
    static final String GOOD_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1";

    @Test
    void reproduzBugDoVaadin() {
        StringIndexOutOfBoundsException ex = assertThrows(StringIndexOutOfBoundsException.class,
                () -> new BrowserDetails(BAD_UA));
        assertTrue(ex.getMessage().contains("begin 40, end 27, length 122"), ex.getMessage());
    }

    @Test
    void substituiUaInvalido() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.addHeader("User-Agent", BAD_UA);
        MockFilterChain chain = new MockFilterChain();
        new UserAgentSanitizerFilter().doFilter(req, new MockHttpServletResponse(), chain);
        String ua = ((jakarta.servlet.http.HttpServletRequest) chain.getRequest()).getHeader("user-agent");
        assertEquals(UserAgentSanitizerFilter.FALLBACK_UA, ua);
        assertDoesNotThrow(() -> new BrowserDetails(ua));
    }

    @Test
    void mantemUaValido() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.addHeader("User-Agent", GOOD_UA);
        MockFilterChain chain = new MockFilterChain();
        new UserAgentSanitizerFilter().doFilter(req, new MockHttpServletResponse(), chain);
        assertSame(req, chain.getRequest());
    }
}
