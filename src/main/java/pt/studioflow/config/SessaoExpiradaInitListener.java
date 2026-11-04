package pt.studioflow.config;

import com.vaadin.flow.server.CustomizedSystemMessages;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * Quando a sessão expira (ou a app reinicia num deploy) o browser recarrega a
 * página sozinho em vez de mostrar o aviso "Session expired". Para o kiosk de
 * checkin (perfil PRESENÇA) isso, junto com o cookie "lembrar-me", faz com que
 * volte a entrar e regresse a /checkin sem ninguém tocar no ecrã; para os
 * restantes perfis leva ao login.
 */
@Component
public class SessaoExpiradaInitListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().setSystemMessagesProvider(info -> {
            CustomizedSystemMessages mensagens = new CustomizedSystemMessages();
            mensagens.setSessionExpiredNotificationEnabled(false);
            return mensagens;
        });
    }
}
