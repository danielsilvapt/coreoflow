package pt.studioflow.view;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import pt.studioflow.service.TocOnlineApiService;

import java.util.List;

/**
 * Destino do redirect OAuth2 do TOCOnline depois do admin do estúdio autorizar
 * o CoreoFlow (ver {@link TocOnlineApiService#gerarUrlAutorizacao}). Página
 * simples de confirmação - o admin fecha esta aba e volta ao StudioAdminView.
 */
@Route("toconline/callback")
@AnonymousAllowed
public class TocOnlineCallbackView extends VerticalLayout implements BeforeEnterObserver {

    private final TocOnlineApiService tocOnlineApiService;

    public TocOnlineCallbackView(TocOnlineApiService tocOnlineApiService) {
        this.tocOnlineApiService = tocOnlineApiService;
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var params = event.getLocation().getQueryParameters().getParameters();
        String code = params.getOrDefault("code", List.of()).stream().findFirst().orElse(null);
        String state = params.getOrDefault("state", List.of()).stream().findFirst().orElse(null);
        String erro = params.getOrDefault("error", List.of()).stream().findFirst().orElse(null);

        if (erro != null) {
            mostrar("Ligação cancelada", "O TOCOnline devolveu um erro: " + erro);
            return;
        }
        if (code == null || state == null) {
            mostrar("Pedido inválido", "Faltam parâmetros no redirecionamento do TOCOnline.");
            return;
        }

        String resultado = tocOnlineApiService.concluirLigacao(code, state);
        boolean sucesso = resultado.contains("sucesso");
        mostrar(sucesso ? "Ligado!" : "Não foi possível ligar", resultado);
    }

    private void mostrar(String titulo, String mensagem) {
        removeAll();
        add(new H2(titulo), new Span(mensagem), new Span("Podes fechar esta janela e voltar ao CoreoFlow."));
    }
}
