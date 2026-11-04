package pt.studioflow.config.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;

class KioskRememberMeServicesTest {

    private static final String KEY = "chave-teste";

    private final UserDetails presenca = User.withUsername("nazare:ipad")
            .password("$2a$10$hashFicticioDaPalavraPasse").roles("PRESENCA").build();
    private final UserDetails admin = User.withUsername("nazare:admin")
            .password("$2a$10$outroHash").roles("ADMIN").build();

    private final KioskRememberMeServices services = new KioskRememberMeServices(KEY,
            username -> username.equals("nazare:ipad") ? presenca : admin);

    private Cookie login(UserDetails user) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        Authentication auth = new UsernamePasswordAuthenticationToken(user, "x", user.getAuthorities());
        services.loginSuccess(req, res, auth);
        return res.getCookie(KioskRememberMeServices.COOKIE);
    }

    @Test
    void contaPresencaRecebeCookieQueVoltaAAutenticar() {
        Cookie cookie = login(presenca);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isEqualTo(KioskRememberMeServices.VALIDADE_SEGUNDOS);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setCookies(cookie);
        Authentication auto = services.autoLogin(req, new MockHttpServletResponse());
        assertThat(auto).isNotNull();
        assertThat(auto.getName()).isEqualTo("nazare:ipad");
    }

    @Test
    void outrosPerfisNaoRecebemCookie() {
        assertThat(login(admin)).isNull();
    }
}
