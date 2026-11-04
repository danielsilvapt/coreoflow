package pt.studioflow.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/**
 * "Lembrar-me" automático só para o perfil PRESENÇA (iPad fixo na sala com o
 * kiosk de checkin): a sessão em memória cai a cada deploy ou quando o tablet
 * fica muito tempo bloqueado, e quem está na sala não sabe a palavra-passe.
 *
 * <p>O cookie é assinado com utilizador + validade + hash da palavra-passe +
 * chave, por isso mudar a palavra-passe da conta PRESENÇA invalida todos os
 * cookies emitidos. Os outros perfis não recebem cookie.</p>
 */
public class KioskRememberMeServices extends TokenBasedRememberMeServices {

    static final String COOKIE = "cf-kiosk";
    static final int VALIDADE_SEGUNDOS = 90 * 24 * 60 * 60; // 90 dias

    public KioskRememberMeServices(String key, UserDetailsService userDetailsService) {
        super(key, userDetailsService);
        setCookieName(COOKIE);
        setTokenValiditySeconds(VALIDADE_SEGUNDOS);
        setAlwaysRemember(true);
    }

    @Override
    public void onLoginSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication successfulAuthentication) {
        boolean presenca = successfulAuthentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_PRESENCA".equals(a.getAuthority()));
        if (presenca) {
            super.onLoginSuccess(request, response, successfulAuthentication);
        }
    }
}
