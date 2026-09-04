package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.service.RemuneracaoService;

@Route(value = "professores", layout = MainLayout.class)
@PageTitle("Professores | CoreoFlow")
@RolesAllowed("ADMIN")
public class ProfessorView extends VerticalLayout {

    private final ProfessorRepository professorRepository;
    private final RemuneracaoService remuneracaoService;
    private final Grid<Professor> grid = new Grid<>(Professor.class, false);

    public ProfessorView(ProfessorRepository professorRepository, RemuneracaoService remuneracaoService) {
        this.professorRepository = professorRepository;
        this.remuneracaoService = remuneracaoService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "#f5f7fa");

        Div tituloWrapper = new Div();
        tituloWrapper.getStyle().set("padding", "20px 20px 0 20px");
        H2 titulo = new H2("Gestão de Professores");
        titulo.getStyle().set("margin-top", "0");
        tituloWrapper.add(titulo);

        add(tituloWrapper, criarToolbar(), configurarGrid());
        atualizarGrid();
    }

    // ---------- TOOLBAR ----------
    private Component criarToolbar() {
        HorizontalLayout toolbar = new HorizontalLayout();
        toolbar.setWidthFull();
        toolbar.setPadding(true);
        toolbar.getStyle()
                .set("background", "white")
                .set("border-bottom", "1px solid #e0e0e0")
                .set("padding", "12px 20px");
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        TextField pesquisa = new TextField();
        pesquisa.setPlaceholder("🔍 Pesquisar professor...");
        pesquisa.setWidth("280px");
        pesquisa.setClearButtonVisible(true);
        pesquisa.addValueChangeListener(e -> {
            String val = e.getValue().toLowerCase().trim();
            grid.setItems(getProfessoresDoStudio().stream()
                    .filter(p -> p.getNome().toLowerCase().contains(val)
                            || (p.getEmail() != null && p.getEmail().toLowerCase().contains(val)))
                    .toList());
        });

        Button adicionar = ViewUtils.botaoNovo("Novo Professor", e -> abrirDialog(new Professor()));

        toolbar.add(pesquisa, adicionar);
        return toolbar;
    }

    // ---------- GRID ----------
    private Grid<Professor> configurarGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.getStyle().set("flex-grow", "1").set("margin", "12px 16px");

        grid.addComponentColumn(this::criarBotoesLinha).setHeader("Ações").setAutoWidth(true);

        grid.addComponentColumn(p -> {
            Div avatar = new Div();
            String initials = p.getNome() != null && p.getNome().length() >= 2
                    ? p.getNome().substring(0, 1).toUpperCase()
                    : "?";
            avatar.setText(initials);
            avatar.getStyle()
                    .set("background", "#457b9d")
                    .set("color", "white")
                    .set("width", "36px").set("height", "36px")
                    .set("border-radius", "50%")
                    .set("display", "flex").set("align-items", "center")
                    .set("justify-content", "center")
                    .set("font-weight", "700").set("font-size", "14px");

            Span nome = new Span(p.getNome());
            nome.getStyle().set("font-weight", "600");

            HorizontalLayout cell = new HorizontalLayout(avatar, nome);
            cell.setAlignItems(FlexComponent.Alignment.CENTER);
            return cell;
        }).setHeader("Professor").setAutoWidth(true).setSortable(true).setComparator(Professor::getNome);

        grid.addColumn(Professor::getEmail).setHeader("Email").setAutoWidth(true).setSortable(true);
        grid.addColumn(Professor::getTelefone).setHeader("Telefone").setAutoWidth(true);

        grid.addComponentColumn(p -> {
            Studio studio = TenantContext.getCurrentStudio();
            boolean herdado = p.getTipoRemuneracao() == null || p.getTipoRemuneracao().isBlank();
            Span badge = new Span(remuneracaoService.descricaoEfetiva(p, studio));
            badge.getStyle()
                    .set("background", herdado ? "#eef2f7" : "#e8f5e9")
                    .set("color", herdado ? "#546e7a" : "#2e7d32")
                    .set("padding", "3px 10px")
                    .set("border-radius", "12px")
                    .set("font-weight", "600")
                    .set("font-size", "0.85rem");
            badge.getElement().setProperty("title", herdado ? "Herdado do estúdio" : "Configuração própria");
            return badge;
        }).setHeader("Remuneração").setAutoWidth(true);

        return grid;
    }

    private HorizontalLayout criarBotoesLinha(Professor professor) {
        Button editar = new Button("Editar", new Icon(VaadinIcon.EDIT));
        editar.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        editar.addClickListener(e -> abrirDialog(professor));

        Button remover = new Button("Remover", new Icon(VaadinIcon.TRASH));
        remover.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        remover.addClickListener(e -> confirmarRemocao(professor));

        return new HorizontalLayout(editar, remover);
    }

    // ---------- DIALOG ----------
    private void abrirDialog(Professor professor) {
        boolean novo = (professor.getId() == null);

        Dialog dialog = new Dialog();
        dialog.setWidth("460px");
        dialog.setHeaderTitle(novo ? "➕ Novo Professor" : "✏️ Editar Professor");

        TextField nome = new TextField("Nome completo");
        nome.setWidthFull();
        nome.setRequired(true);
        nome.setPrefixComponent(VaadinIcon.USER.create());
        nome.setValue(professor.getNome() != null ? professor.getNome() : "");

        EmailField email = new EmailField("Email");
        email.setWidthFull();
        email.setRequired(true);
        email.setPrefixComponent(VaadinIcon.ENVELOPE.create());
        email.setValue(professor.getEmail() != null ? professor.getEmail() : "");

        TextField telefone = new TextField("Telefone");
        telefone.setWidthFull();
        telefone.setPrefixComponent(VaadinIcon.PHONE.create());
        telefone.setValue(professor.getTelefone() != null ? professor.getTelefone() : "");

        NumberField valorHora = new NumberField("€/hora — aula regular");
        valorHora.setWidthFull();
        valorHora.setMin(0);
        valorHora.setMax(200);
        valorHora.setStep(0.5);
        valorHora.setPrefixComponent(VaadinIcon.EURO.create());
        valorHora.setValue(professor.getValorHoraAula());

        // --- Remuneração (sobrepõe o estúdio) ---
        Studio studio = TenantContext.getCurrentStudio();

        H4 secRemun = new H4("Remuneração");
        secRemun.getStyle().set("margin", "12px 0 0 0");
        Span remunHint = new Span(studio != null
                ? "Deixa \"Herdar do estúdio\" para usar: " + remuneracaoService.descricaoEfetiva(null, studio)
                : "Deixa \"Herdar do estúdio\" para usar a configuração do estúdio.");
        remunHint.getStyle().set("font-size", "12px").set("color", "#888");

        final String HERDAR = "— Herdar do estúdio —";
        ComboBox<String> tipoRemun = new ComboBox<>("Tipo de remuneração");
        tipoRemun.setItems(HERDAR, "HORA", "PERCENTAGEM");
        tipoRemun.setItemLabelGenerator(t -> switch (t) {
            case "HORA" -> "Valor por hora (€/h)";
            case "PERCENTAGEM" -> "Percentagem da mensalidade (%)";
            default -> HERDAR;
        });
        tipoRemun.setWidthFull();
        tipoRemun.setValue(professor.getTipoRemuneracao() == null || professor.getTipoRemuneracao().isBlank()
                ? HERDAR : professor.getTipoRemuneracao());

        NumberField valorEnsaio = new NumberField("€/hora — ensaio");
        valorEnsaio.setValue(professor.getValorHoraEnsaio());
        valorEnsaio.setMin(0);
        NumberField valorPrivada = new NumberField("€/hora — privada / workshop");
        valorPrivada.setValue(professor.getValorHoraPrivada());
        valorPrivada.setMin(0);

        NumberField perc1x = new NumberField("% — 1x/sem");
        perc1x.setValue(professor.getPerc1x());
        NumberField perc2x = new NumberField("% — 2x/sem");
        perc2x.setValue(professor.getPerc2x());
        NumberField perc3x = new NumberField("% — 3x/sem");
        perc3x.setValue(professor.getPerc3x());
        NumberField percOutras = new NumberField("% — outras");
        percOutras.setValue(professor.getPercOutras());
        for (NumberField f : new NumberField[] { perc1x, perc2x, perc3x, percOutras }) {
            f.setMin(0);
            f.setMax(100);
        }

        FormLayout grupoHora = new FormLayout(valorHora, valorEnsaio, valorPrivada);
        grupoHora.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        FormLayout grupoPerc = new FormLayout(perc1x, perc2x, perc3x, percOutras);
        grupoPerc.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        Runnable ajustar = () -> {
            String tipoEfetivo = HERDAR.equals(tipoRemun.getValue())
                    ? (studio != null ? studio.getTipoRemuneracaoProf() : "HORA")
                    : tipoRemun.getValue();
            boolean hora = !"PERCENTAGEM".equals(tipoEfetivo);
            // €/hora regular é sempre relevante (fallback do modo percentagem); só oculta ensaio/privada e %
            valorEnsaio.setVisible(hora);
            valorPrivada.setVisible(hora);
            grupoPerc.setVisible(!hora);
        };
        tipoRemun.addValueChangeListener(e -> ajustar.run());
        ajustar.run();

        FormLayout form = new FormLayout(nome, email, telefone);
        form.setWidthFull();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button guardar = new Button("💾 Guardar", e -> {
            if (nome.isEmpty() || email.isEmpty()) {
                Notification.show("⚠️ Nome e Email são obrigatórios", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            professor.setNome(nome.getValue().trim());
            professor.setEmail(email.getValue().trim());
            professor.setTelefone(telefone.getValue().trim());
            professor.setValorHoraAula(valorHora.getValue() != null ? valorHora.getValue() : 25.0);
            professor.setTipoRemuneracao(HERDAR.equals(tipoRemun.getValue()) ? null : tipoRemun.getValue());
            professor.setValorHoraEnsaio(valorEnsaio.getValue());
            professor.setValorHoraPrivada(valorPrivada.getValue());
            professor.setPerc1x(perc1x.getValue());
            professor.setPerc2x(perc2x.getValue());
            professor.setPerc3x(perc3x.getValue());
            professor.setPercOutras(percOutras.getValue());
            if (professor.getStudio() == null) {
                professor.setStudio(pt.studioflow.config.TenantContext.getCurrentStudio());
            }

            professorRepository.save(professor);
            atualizarGrid();
            dialog.close();

            Notification n = Notification.show("✅ Professor guardado com sucesso!", 3000, Notification.Position.BOTTOM_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelar = new Button("Cancelar", e -> dialog.close());
        cancelar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.add(form, secRemun, remunHint, tipoRemun, grupoHora, grupoPerc);
        dialog.getFooter().add(cancelar, guardar);
        dialog.open();
    }

    // ---------- REMOVER COM CONFIRMAÇÃO ----------
    private void confirmarRemocao(Professor professor) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Remover Professor");
        confirm.setText("Tem a certeza que quer remover " + professor.getNome() + "? Esta ação não pode ser desfeita.");
        confirm.setCancelable(true);
        confirm.setCancelText("Cancelar");
        confirm.setConfirmText("Remover");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> {
            professorRepository.delete(professor);
            atualizarGrid();
            Notification.show("Professor removido", 2000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        });
        confirm.open();
    }

    // ---------- ATUALIZAR GRID ----------
    private java.util.List<pt.studioflow.model.Professor> getProfessoresDoStudio() {
        pt.studioflow.model.Studio studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        return studio != null ? professorRepository.findAllByStudio(studio) : professorRepository.findAll();
    }

    private void atualizarGrid() {
        grid.setItems(getProfessoresDoStudio());
    }
}
