package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.PlanoSubscricao;
import pt.studioflow.model.Studio;
import pt.studioflow.model.StudioModulo;
import pt.studioflow.repository.PlanoSubscricaoRepository;
import pt.studioflow.repository.StudioRepository;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "admin/planos", layout = MainLayout.class)
@PageTitle("Planos de Subscrição | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class PlanosSubscricaoView extends VerticalLayout {

    private final PlanoSubscricaoRepository planoRepo;
    private final StudioRepository studioRepo;
    private Grid<PlanoSubscricao> grid;

    public PlanosSubscricaoView(PlanoSubscricaoRepository planoRepo, StudioRepository studioRepo) {
        this.planoRepo = planoRepo;
        this.studioRepo = studioRepo;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        add(ViewUtils.toolbar(ViewUtils.botaoNovo("Novo Plano", e -> abrirDialogPlano(null))),
            criarGrid());
        atualizar();
    }

    private Grid<PlanoSubscricao> criarGrid() {
        grid = new Grid<>(PlanoSubscricao.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addComponentColumn(p -> {
            Button editar = new Button(VaadinIcon.EDIT.create(), e -> abrirDialogPlano(p));
            editar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button atribuir = new Button("Atribuir Studio", e -> abrirAtribuirStudio(p));
            atribuir.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return new HorizontalLayout(editar, atribuir);
        }).setHeader("Ações").setAutoWidth(true);

        grid.addComponentColumn(p -> {
            Span b = new Span(p.getNome());
            b.getStyle().set("background", p.getCor() != null ? p.getCor() : "#4A90E2")
                    .set("color", "white").set("padding", "3px 10px")
                    .set("border-radius", "12px").set("font-weight", "700");
            return b;
        }).setHeader("Plano").setAutoWidth(true);

        grid.addColumn(p -> String.format("%.2f €/mês", p.getPrecoMensal()))
                .setHeader("Preço").setAutoWidth(true).setSortable(true);
        grid.addColumn(p -> p.getLimiteAlunos() < 0 ? "Ilimitado" : p.getLimiteAlunos() + " alunos")
                .setHeader("Limite").setAutoWidth(true);

        grid.addComponentColumn(p -> {
            long count = studioRepo.findAll().stream()
                    .filter(s -> s.getPlano() != null && s.getPlano().getId().equals(p.getId())).count();
            Span s = new Span(count + " estúdios");
            s.getStyle().set("font-weight", count > 0 ? "700" : "400")
                    .set("color", count > 0 ? "#1976D2" : "#888");
            return s;
        }).setHeader("Utilizadores").setAutoWidth(true);

        return grid;
    }

    private void abrirDialogPlano(PlanoSubscricao p) {
        boolean novo = p == null;
        PlanoSubscricao plano = novo ? new PlanoSubscricao() : p;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(novo ? "Novo Plano" : "Editar " + plano.getNome());
        dialog.setWidth("560px");

        TextField nome = new TextField("Nome do Plano");
        nome.setWidthFull();
        nome.setValue(plano.getNome() != null ? plano.getNome() : "");

        TextField cor = new TextField("Cor (hex)");
        cor.setWidthFull();
        cor.setValue(plano.getCor() != null ? plano.getCor() : "#4A90E2");

        NumberField preco = new NumberField("Preço mensal (€)");
        preco.setMin(0); preco.setWidthFull();
        preco.setValue(plano.getPrecoMensal());

        IntegerField limite = new IntegerField("Limite de alunos (-1 = ilimitado)");
        limite.setWidthFull();
        limite.setValue(plano.getLimiteAlunos() == 0 ? -1 : plano.getLimiteAlunos());

        TextArea descricao = new TextArea("Descrição");
        descricao.setWidthFull();
        descricao.setValue(plano.getDescricao() != null ? plano.getDescricao() : "");

        // Módulos incluídos
        CheckboxGroup<StudioModulo> modulos = new CheckboxGroup<>();
        modulos.setLabel("Módulos incluídos (vazio = todos)");
        modulos.setItems(StudioModulo.values());
        modulos.setItemLabelGenerator(StudioModulo::getLabel);
        modulos.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        modulos.setWidthFull();
        if (plano.getModulosIncluidos() != null && !plano.getModulosIncluidos().isBlank()) {
            Set<StudioModulo> sel = Arrays.stream(plano.getModulosIncluidos().split(","))
                    .map(String::trim)
                    .filter(m -> { try { StudioModulo.valueOf(m); return true; } catch (Exception ex) { return false; } })
                    .map(StudioModulo::valueOf)
                    .collect(Collectors.toSet());
            modulos.setValue(sel);
        } else {
            modulos.setValue(new HashSet<>(Arrays.asList(StudioModulo.values())));
        }

        Button guardar = new Button("Guardar", e -> {
            if (nome.isEmpty()) { Notification.show("Nome obrigatório"); return; }
            plano.setNome(nome.getValue().trim());
            plano.setCor(cor.getValue().trim());
            plano.setPrecoMensal(preco.getValue() != null ? preco.getValue() : 0);
            plano.setLimiteAlunos(limite.getValue() != null ? limite.getValue() : -1);
            plano.setDescricao(descricao.getValue().trim());
            plano.setModulosIncluidos(modulos.getValue().stream()
                    .map(StudioModulo::name).collect(Collectors.joining(",")));
            planoRepo.save(plano);
            atualizar();
            dialog.close();
            Notification.show("Plano guardado").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new VerticalLayout(new FormLayout(nome, cor, preco, limite, descricao), modulos));
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }

    private void abrirAtribuirStudio(PlanoSubscricao plano) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Atribuir Plano: " + plano.getNome());
        dialog.setWidth("400px");

        ComboBox<Studio> studioCombo = new ComboBox<>("Estúdio");
        studioCombo.setItems(studioRepo.findAll().stream().filter(Studio::isAtivo).toList());
        studioCombo.setItemLabelGenerator(Studio::getNome);
        studioCombo.setWidthFull();

        Button guardar = new Button("Atribuir", e -> {
            if (studioCombo.getValue() == null) { Notification.show("Seleciona um estúdio"); return; }
            Studio s = studioCombo.getValue();
            s.setPlano(plano);
            studioRepo.save(s);
            atualizar();
            dialog.close();
            Notification.show("Plano " + plano.getNome() + " atribuído a " + s.getNome())
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(studioCombo);
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }

    private void atualizar() {
        grid.setItems(planoRepo.findAll());
    }
}
