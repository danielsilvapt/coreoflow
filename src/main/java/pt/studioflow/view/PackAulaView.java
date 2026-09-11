package pt.studioflow.view;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.PackAula;
import pt.studioflow.model.Studio;
import pt.studioflow.service.PackAulaService;

/**
 * CRUD (ADMIN, por estúdio) do catálogo de packs de aulas avulso que o
 * estúdio vende — cada estúdio define os seus próprios tamanhos/preços,
 * não há packs fixos na plataforma.
 */
@PageTitle("Packs de Aula Avulsa | CoreoFlow")
@Route(value = "packs-aula", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class PackAulaView extends VerticalLayout {

    private final PackAulaService packAulaService;
    private final Grid<PackAula> grid = new Grid<>(PackAula.class, false);
    private final List<PackAula> lista = new ArrayList<>();

    public PackAulaView(PackAulaService packAulaService) {
        this.packAulaService = packAulaService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Packs de Aula Avulsa");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        Button addButton = ViewUtils.botaoNovo("Novo Pack", e -> abrirFormulario(null));
        add(ViewUtils.toolbar(addButton));

        configurarGrid();
        add(grid);
        expand(grid);

        atualizarLista();
    }

    private void configurarGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        grid.setDataProvider(new ListDataProvider<>(lista));

        grid.addColumn(PackAula::getNome).setHeader("Nome");
        grid.addColumn(PackAula::getNumAulas).setHeader("Nº Aulas");
        grid.addColumn(p -> p.getPreco() + "€").setHeader("Preço");
        grid.addColumn(p -> p.getValidadeDias() != null ? p.getValidadeDias() + " dias" : "Sem expiração")
                .setHeader("Validade");
        grid.addColumn(p -> p.isRestritoAModalidade() ? "Só a modalidade escolhida" : "Qualquer turma/modalidade")
                .setHeader("Âmbito");
        grid.addComponentColumn(p -> {
            Icon icon = (p.isAtivo() ? VaadinIcon.CHECK : VaadinIcon.CLOSE).create();
            icon.getStyle().set("color", p.isAtivo() ? "green" : "red");
            return icon;
        }).setHeader("Ativo");

        grid.addComponentColumn(pack -> {
            Button editar = new Button(new Icon(VaadinIcon.EDIT), e -> abrirFormulario(pack));
            Button remover = new Button(new Icon(VaadinIcon.TRASH), e -> confirmarRemocao(pack));
            HorizontalLayout acoes = new HorizontalLayout(editar, remover);
            acoes.setSpacing(true);
            return acoes;
        }).setHeader("Ações").setAutoWidth(true).setFlexGrow(0);
    }

    private void confirmarRemocao(PackAula pack) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Tem a certeza?");
        dialog.add("Eliminar o pack \"" + pack.getNome() + "\"? Compras já feitas com este pack mantêm-se.");
        Button confirmar = new Button("Eliminar", e -> {
            packAulaService.deleteById(pack.getId());
            dialog.close();
            atualizarLista();
            Notification.show("Pack eliminado.").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        Button cancelar = new Button("Cancelar", e -> dialog.close());
        dialog.getFooter().add(cancelar, confirmar);
        dialog.open();
    }

    private void abrirFormulario(PackAula existente) {
        Studio studio = TenantContext.getCurrentStudio();
        if (studio == null) {
            Notification.show("Sem estúdio ativo na sessão.").addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        PackAula pack = existente != null ? existente : new PackAula();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existente != null ? "Editar Pack" : "Novo Pack");

        TextField nome = new TextField("Nome");
        nome.setValue(pack.getNome() != null ? pack.getNome() : "");
        nome.setRequired(true);
        nome.setWidthFull();

        NumberField numAulas = new NumberField("Número de aulas (1 = aula avulsa única)");
        numAulas.setValue(pack.getNumAulas() != null ? pack.getNumAulas().doubleValue() : 1.0);
        numAulas.setMin(1);
        numAulas.setStep(1);
        numAulas.setWidthFull();

        NumberField preco = new NumberField("Preço (€)");
        preco.setValue(pack.getPreco() != null ? pack.getPreco().doubleValue() : 0.0);
        preco.setMin(0);
        preco.setWidthFull();

        NumberField validadeDias = new NumberField("Validade em dias (vazio = sem expiração)");
        if (pack.getValidadeDias() != null) validadeDias.setValue(pack.getValidadeDias().doubleValue());
        validadeDias.setMin(0);
        validadeDias.setWidthFull();

        Checkbox restrito = new Checkbox("Restrito à modalidade escolhida na compra");
        restrito.setValue(pack.isRestritoAModalidade());

        Checkbox ativo = new Checkbox("Ativo (visível na compra pública)");
        ativo.setValue(existente == null || pack.isAtivo());

        VerticalLayout form = new VerticalLayout(nome, numAulas, preco, validadeDias, restrito, ativo);
        form.setPadding(false);
        dialog.add(form);

        Button guardar = new Button("Guardar", e -> {
            if (nome.getValue().isBlank() || numAulas.getValue() == null || preco.getValue() == null) {
                Notification.show("Preenche nome, nº de aulas e preço.").addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            pack.setStudio(studio);
            pack.setNome(nome.getValue().trim());
            pack.setNumAulas(numAulas.getValue().intValue());
            pack.setPreco(BigDecimal.valueOf(preco.getValue()));
            pack.setValidadeDias(validadeDias.getValue() != null ? validadeDias.getValue().intValue() : null);
            pack.setRestritoAModalidade(restrito.getValue());
            pack.setAtivo(ativo.getValue());
            packAulaService.save(pack);
            dialog.close();
            atualizarLista();
            Notification.show("Pack guardado.").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        Button cancelar = new Button("Cancelar", e -> dialog.close());
        dialog.getFooter().add(cancelar, guardar);
        dialog.open();
    }

    private void atualizarLista() {
        Studio studio = TenantContext.getCurrentStudio();
        lista.clear();
        if (studio != null) {
            lista.addAll(packAulaService.listarPorStudio(studio));
        }
        ((ListDataProvider<PackAula>) grid.getDataProvider()).refreshAll();
    }
}
