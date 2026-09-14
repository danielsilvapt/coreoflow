package pt.studioflow.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import pt.studioflow.model.Studio;
import pt.studioflow.repository.StudioRepository;

/**
 * Consulta o {@link SessionRegistry} do Spring Security para saber, em tempo
 * real, quem está autenticado na aplicação e em que estúdio — usado pelo
 * dashboard do superadmin.
 */
@Service
public class SessaoAtivaService {

    private final SessionRegistry sessionRegistry;
    private final StudioRepository studioRepository;

    public SessaoAtivaService(SessionRegistry sessionRegistry, StudioRepository studioRepository) {
        this.sessionRegistry = sessionRegistry;
        this.studioRepository = studioRepository;
    }

    public record SessaoInfo(String studioSlug, String studioNome, String username, String papel,
            LocalDateTime ultimaAtividade) {
    }

    /** Uma linha por sessão HTTP autenticada e não expirada. */
    public List<SessaoInfo> listarSessoesAtivas() {
        Map<String, Studio> studiosPorSlug = studioRepository.findAll().stream()
                .collect(Collectors.toMap(Studio::getSlug, s -> s, (a, b) -> a));

        List<SessaoInfo> resultado = new ArrayList<>();
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (!(principal instanceof UserDetails ud)) {
                continue;
            }
            List<SessionInformation> sessoes = sessionRegistry.getAllSessions(principal, false);
            for (SessionInformation si : sessoes) {
                String usernameCompleto = ud.getUsername();
                String slug = null;
                String username = usernameCompleto;
                if (usernameCompleto.contains(CustomUserDetailsService.TENANT_SEPARATOR)) {
                    String[] partes = usernameCompleto.split(CustomUserDetailsService.TENANT_SEPARATOR, 2);
                    slug = partes[0];
                    username = partes[1];
                }
                String papel = ud.getAuthorities().stream().findFirst()
                        .map(GrantedAuthority::getAuthority).orElse("");
                Studio studio = slug != null ? studiosPorSlug.get(slug) : null;
                String studioNome = studio != null ? studio.getNome() : (slug != null ? slug : "Plataforma (superadmin)");
                resultado.add(new SessaoInfo(slug, studioNome, username, papel, paraLocalDateTime(si.getLastRequest())));
            }
        }
        return resultado;
    }

    /** Nº de utilizadores ligados agora, por nome de estúdio (inclui "Plataforma (superadmin)"). */
    public Map<String, Long> contarPorEstudio() {
        return listarSessoesAtivas().stream()
                .collect(Collectors.groupingBy(SessaoInfo::studioNome, Collectors.counting()));
    }

    public long total() {
        return listarSessoesAtivas().size();
    }

    private static LocalDateTime paraLocalDateTime(Date data) {
        return data == null ? null : data.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
