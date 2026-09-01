package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Referencia;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.ReferenciaRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "referencias", layout = MainLayout.class)
@PageTitle("Referências | CoreoFlow")
@RolesAllowed("ADMIN")
public class ReferenciasView extends VerticalLayout {

    private final ReferenciaRepository referenciaRepo;
    private final AlunoRepository alunoRepo;
    private Grid<Referencia> grid;

    public ReferenciasView(ReferenciaRepository referenciaRepo, AlunoRepository alunoRepo) {
        this.referenciaRepo = referenciaRepo;
        this.alunoRepo = alunoRepo;
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Referências");
        titulo.getStyle().set("margin", "0 0 8px 0").set("padding", "20px 20px 0 20px");

        add(titulo, ViewUtils.toolbar(ViewUtils.botaoNovo("Registar Referência", e -> abrirDialog(null))),
            criarGrid());
        atualizar();
    }

    private Grid<Referencia> criarGrid() {
        grid = new Grid<>(Referencia.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(r -> r.getDataReferencia().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .setHeader("Data").setAutoWidth(true).setSortable(true);
        grid.addColumn(r -> r.getReferenciador().getNomeCompleto())
                .setHeader("Quem referenciou").setFlexGrow(1).setSortable(true);
        grid.addColumn(r -> r.getReferenciado().getNomeCompleto())
                .setHeader("Novo aluno").setFlexGrow(1);
        grid.addComponentColumn(r -> {
            String[] cfg = switch (r.getEstado()) {
                case PENDENTE     -> new String[]{"#fff3e0","#E67E22","Pendente"};
                case CONFIRMADA   -> new String[]{"#e3f2fd","#1976D2","Confirmada"};
                case RECOMPENSADA -> new String[]{"#e8f5e9","#27AE60","Recompensada"};
            };
            Span b = new Span(cfg[2]);
            b.getStyle().set("background",cfg[0]).set("color",cfg[1])
                    .set("padding","2px 8px").set("border-radius","10px")
                    .set("font-size","11px").set("font-weight","600");
            return b;
        }).setHeader("Estado").setAutoWidth(true);
        grid.addColumn(r -> r.getDescontoEuros() > 0
                ? String.format("%.2f €", r.getDescontoEuros()) : "—")
                .setHeader("Desconto").setAutoWidth(true);
        grid.addComponentColumn(r -> {
            HorizontalLayout actions = new HorizontalLayout();
            if (r.getEstado() == Referencia.Estado.PENDENTE) {
                Button confirmar = new Button("Confirmar", e -> {
                    r.setEstado(Referencia.Estado.CONFIRMADA);
                    r.setDataConfirmacao(LocalDate.now());
                    referenciaRepo.save(r);
                    atualizar();
                    Notification.show("Referência confirmada!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
                confirmar.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
                actions.add(confirmar);
            }
            if (r.getEstado() == Referencia.Estado.CONFIRMADA) {
                Button recompensar = new Button("Recompensar", e -> {
                    r.setEstado(Referencia.Estado.RECOMPENSADA);
                    referenciaRepo.save(r);
                    atualizar();
                    Notification.show("Desconto de " + String.format("%.2f €", r.getDescontoEuros())
                            + " aplicado!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
                recompensar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                actions.add(recompensar);
            }
            Button del = new Button(VaadinIcon.TRASH.create(), e -> { referenciaRepo.delete(r); atualizar(); });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            actions.add(del);
            return actions;
        }).setHeader("Ações").setAutoWidth(true);

        return grid;
    }

    private void atualizar() {
        Studio s = TenantContext.getCurrentStudio();
        grid.setItems(s != null ? referenciaRepo.findByStudioOrderByDataReferenciaDesc(s)
                                 : referenciaRepo.findAll());
    }

    private void abrirDialog(Referencia ref) {
        Studio studio = TenantContext.getCurrentStudio();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Registar Referência");
        dialog.setWidth("440px");

        List<Aluno> alunos = studio != null ? alunoRepo.findAllByStudio(studio) : alunoRepo.findAll();

        ComboBox<Aluno> referenciador = new ComboBox<>("Aluno que referenciou");
        referenciador.setItems(alunos);
        referenciador.setItemLabelGenerator(Aluno::getNomeCompleto);
        referenciador.setRequired(true); referenciador.setWidthFull();

        ComboBox<Aluno> referenciado = new ComboBox<>("Novo aluno trazido");
        referenciado.setItems(alunos);
        referenciado.setItemLabelGenerator(Aluno::getNomeCompleto);
        referenciado.setRequired(true); referenciado.setWidthFull();

        NumberField desconto = new NumberField("Desconto a oferecer (€)");
        desconto.setMin(0); desconto.setValue(5.0); desconto.setWidthFull();
        desconto.setHelperText("Aplicado na mensalidade quando recompensado");

        TextArea obs = new TextArea("Observações");
        obs.setWidthFull();

        Button guardar = new Button("Guardar", e -> {
            if (referenciador.getValue() == null || referenciado.getValue() == null) {
                Notification.show("Ambos os alunos são obrigatórios"); return;
            }
            Referencia r = new Referencia();
            r.setReferenciador(referenciador.getValue());
            r.setReferenciado(referenciado.getValue());
            r.setDescontoEuros(desconto.getValue() != null ? desconto.getValue() : 0);
            r.setObservacoes(obs.getValue().trim());
            r.setStudio(studio);
            referenciaRepo.save(r);
            atualizar();
            dialog.close();
            Notification.show("Referência registada").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new FormLayout(referenciador, referenciado, desconto, obs));
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }
}
