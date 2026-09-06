package pt.studioflow.config;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Força a locale pt-PT em todas as UIs. Sem isto, os {@code DatePicker} e a
 * formatação de datas herdavam a locale por omissão da JVM do servidor
 * (en-US) e apareciam em MM/DD/YYYY em vez de DD/MM/YYYY.
 */
@Component
public class LocaleInitListener implements VaadinServiceInitListener {

    private static final Locale PT_PT = new Locale("pt", "PT");

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit -> {
            UI ui = uiInit.getUI();
            ui.setLocale(PT_PT);
            ui.getSession().setLocale(PT_PT);
        });
    }
}
