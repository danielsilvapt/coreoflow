package pt.studioflow.config.security;

import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;

@Component
public class VaadinRouteSecurity implements BeforeEnterListener {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {

        System.out.println("BEFORE ENTER -------------------------");

        Class<?> view = event.getNavigationTarget();
        RolesAllowed rolesAllowed = findRolesAllowed(view);

        if (rolesAllowed != null) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            boolean allowed = auth.getAuthorities().stream()
                    .anyMatch(a -> java.util.Arrays.asList(rolesAllowed.value())
                            .contains(a.getAuthority()));

            if (!allowed) {
                // Redireciona para login ou podes criar página de acesso negado
                event.rerouteTo("login");
            }
        }
    }

    private RolesAllowed findRolesAllowed(Class<?> clazz) {
        while (clazz != null) {
            RolesAllowed rolesAllowed = clazz.getAnnotation(RolesAllowed.class);
            if (rolesAllowed != null) {
                return rolesAllowed;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
