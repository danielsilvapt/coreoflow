package pt.studioflow.config;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * O loader "Pirueta" (bailarina, definido no tema coreoflow/styles.css) só aparece a partir do
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
            ui.getPage().executeJs(ROTACAO_ESTILOS);
        });
    }

    /**
     * Alterna o estilo de dança do loader (ver data-cf-loader em styles.css): começa num
     * estilo ao acaso e passa ao seguinte cada vez que o loader se esconde, para a troca
     * nunca se ver a meio de uma animação.
     */
    private static final String ROTACAO_ESTILOS = """
            (function () {
              if (window.__cfLoaderRotacao) return;
              window.__cfLoaderRotacao = true;
              var estilos = ['ballet', 'hiphop', 'salao', 'yoga', 'flamenco', 'contemporaneo'];
              var i = Math.floor(Math.random() * estilos.length);
              var root = document.documentElement;
              root.setAttribute('data-cf-loader', estilos[i]);
              var visivel = false;
              function ligar(el) {
                new MutationObserver(function () {
                  var agora = /\\b(first|second|third)\\b/.test(el.className);
                  if (visivel && !agora) {
                    i = (i + 1) % estilos.length;
                    root.setAttribute('data-cf-loader', estilos[i]);
                  }
                  visivel = agora;
                }).observe(el, { attributes: true, attributeFilter: ['class'] });
              }
              (function procurar(tentativas) {
                var el = document.querySelector('.v-loading-indicator');
                if (el) ligar(el);
                else if (tentativas > 0) setTimeout(function () { procurar(tentativas - 1); }, 250);
              })(40);
            })();
            """;
}
