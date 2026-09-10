package pt.studioflow.config;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * O loader "CF" com brilho (definido em MainLayout) só aparece a partir do
 * "first delay" do Vaadin, que por omissão é 450ms. Páginas rápidas (a
 * maioria dos menus CRUD) nunca chegam lá, por isso o loader parecia
 * exclusivo do Treinador IA (chamadas à IA e ao modelo 3D, mais lentas).
 * Baixar o delay torna-o transversal a qualquer navegação/pedido ao servidor.
 */
@Component
public class LoadingIndicatorInitListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit -> {
            UI ui = uiInit.getUI();
            ui.getLoadingIndicatorConfiguration().setFirstDelay(150);
            ui.getLoadingIndicatorConfiguration().setSecondDelay(1500);
            ui.getLoadingIndicatorConfiguration().setThirdDelay(4000);
        });
    }
}
