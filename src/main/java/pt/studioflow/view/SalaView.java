package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Sala;
import pt.studioflow.repository.SalaRepository;

@Route(value = "salas", layout = MainLayout.class)
@PageTitle("Salas | CoreoFlow")
@RolesAllowed("ADMIN")
public class SalaView extends VerticalLayout {

    private final SalaRepository salaRepository;
    private final Grid<Sala> grid = new Grid<>(Sala.class, false);

    public SalaView(SalaRepository salaRepository) {
        this.salaRepository = salaRepository;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Gestão de Salas");
        titulo.getStyle().set("margin-top", "0");
        add(titulo, ViewUtils.toolbar(ViewUtils.botaoNovo("Nova Sala", e -> abrirDialog(new Sala()))), configurarGrid());

        atualizarGrid();
    }


    // ---------- BOTÃO ADICIONAR ----------
    private Button criarBotaoAdicionar() {
        Button adicionar = new Button("Adicionar Sala", new Icon(VaadinIcon.PLUS));
        adicionar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        adicionar.addClickListener(e -> abrirDialog(new Sala()));
        return adicionar;
    }

    // ---------- GRID ----------
    private Grid<Sala> configurarGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(Sala::getNome)
                .setHeader("Nome")
                .setAutoWidth(true);

        grid.addColumn(Sala::getCor)
                .setHeader("Cor")
                .setAutoWidth(true);

        grid.addComponentColumn(this::criarBotoesLinha)
                .setHeader("Ações")
                .setAutoWidth(true);

        return grid;
    }

    // ---------- BOTÕES POR LINHA ----------
    private HorizontalLayout criarBotoesLinha(Sala sala) {
        Button editar = new Button(new Icon(VaadinIcon.EDIT));
        editar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        editar.addClickListener(e -> abrirDialog(sala));

        Button remover = new Button(new Icon(VaadinIcon.TRASH));
        remover.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        remover.addClickListener(e -> removerSala(sala));

        return new HorizontalLayout(editar, remover);
    }

    // ---------- DIALOG ----------
    private void abrirDialog(Sala sala) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(
                sala.getId() == null ? "Adicionar Sala" : "Editar Sala"
        );

        TextField nome = new TextField("Nome");
        nome.setWidthFull();
        nome.setRequired(true);
        nome.setValue(sala.getNome() != null ? sala.getNome() : "");

        TextField cor = new TextField("Cor");
        cor.setWidthFull();
        cor.setPlaceholder("ex: #FF5733 ou azul");
        cor.setValue(sala.getCor() != null ? sala.getCor() : "");

        FormLayout form = new FormLayout(nome, cor);
        form.setWidthFull();

        Button guardar = new Button("Guardar", e -> {
            if (nome.isEmpty()) {
                Notification.show("O nome da sala é obrigatório");
                return;
            }

            sala.setNome(nome.getValue());
            sala.setCor(cor.getValue());
            if (sala.getStudio() == null) {
                sala.setStudio(pt.studioflow.config.TenantContext.getCurrentStudio());
            }

            salaRepository.save(sala);
            atualizarGrid();
            dialog.close();

            Notification.show("Sala guardada com sucesso");
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelar = new Button("Cancelar", e -> dialog.close());

        HorizontalLayout botoes = new HorizontalLayout(cancelar, guardar);

        dialog.add(form);
        dialog.getFooter().add(botoes);

        dialog.open();
    }

    // ---------- REMOVER ----------
    private void removerSala(Sala sala) {
        salaRepository.delete(sala);
        atualizarGrid();
        Notification.show("Sala removida");
    }

    // ---------- ATUALIZAR GRID ----------
    private void atualizarGrid() {
        pt.studioflow.model.Studio s = pt.studioflow.config.TenantContext.getCurrentStudio();
        grid.setItems(s != null ? salaRepository.findAllByStudio(s) : salaRepository.findAll());
    }
}
