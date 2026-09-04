package pt.studioflow.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.ListaEspera;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.ListaEsperaRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.AlunoTurmaService;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "lista-espera", layout = MainLayout.class)
@PageTitle("Lista de Espera | CoreoFlow")
@RolesAllowed("ADMIN")
public class ListaEsperaView extends VerticalLayout {

    private final ListaEsperaRepository listaEsperaRepository;
    private final TurmaRepository turmaRepository;

    private Grid<ListaEspera> grid;
    private ComboBox<Turma> filtroTurma;

    public ListaEsperaView(ListaEsperaRepository listaEsperaRepository,
                           TurmaRepository turmaRepository) {
        this.listaEsperaRepository = listaEsperaRepository;
        this.turmaRepository = turmaRepository;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Lista de Espera");
        titulo.getStyle().set("margin", "0 0 8px 0").set("padding", "20px 20px 0 20px");

        add(titulo, criarToolbar(), criarGrid());
        atualizar();
    }

    private HorizontalLayout criarToolbar() {
        filtroTurma = new ComboBox<>("Turma");
        Studio studio = TenantContext.getCurrentStudio();
        List<Turma> turmas = studio != null
                ? turmaRepository.findAllByStudio(studio)
                : turmaRepository.findAll();
        filtroTurma.setItems(turmas);
        filtroTurma.setItemLabelGenerator(t -> t.getDescricao() + " (" + t.getCodigo() + ")");
        filtroTurma.setClearButtonVisible(true);
        filtroTurma.setPlaceholder("Todas as turmas");
        filtroTurma.setWidth("260px");
        filtroTurma.addValueChangeListener(e -> atualizar());

        Button btnNovo = ViewUtils.botaoNovo("Adicionar à Espera", e -> abrirDialog(null));

        return ViewUtils.toolbar(filtroTurma, btnNovo);
    }

    private Grid<ListaEspera> criarGrid() {
        grid = new Grid<>(ListaEspera.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addComponentColumn(le -> {
            HorizontalLayout actions = new HorizontalLayout();
            actions.setSpacing(true);

            // WhatsApp — destaque se NOTIFICADO
            String url = AlunoTurmaService.whatsappUrlEspera(le,
                    le.getTurma() != null ? le.getTurma().getDescricao() : "");
            if (url != null) {
                Button wa = new Button(VaadinIcon.CHAT.create());
                wa.getStyle().set("color", "#25D366");
                if (le.getEstado() == ListaEspera.Estado.NOTIFICADO) {
                    wa.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
                    wa.setText("Notificar");
                }
                wa.addClickListener(e -> UI.getCurrent().getPage().open(url, "_blank"));
                actions.add(wa);
            }

            // Marcar como convertido
            if (le.getEstado() != ListaEspera.Estado.CONVERTIDO) {
                Button conv = new Button(VaadinIcon.CHECK.create());
                conv.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_TERTIARY);
                conv.getElement().setProperty("title", "Marcar como Convertido");
                conv.addClickListener(e -> {
                    le.setEstado(ListaEspera.Estado.CONVERTIDO);
                    listaEsperaRepository.save(le);
                    atualizar();
                });
                actions.add(conv);
            }

            // Desistiu
            Button des = new Button(VaadinIcon.CLOSE_SMALL.create());
            des.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            des.getElement().setProperty("title", "Desistiu");
            des.addClickListener(e -> {
                ConfirmDialog cd = new ConfirmDialog();
                cd.setHeader("Remover da lista?");
                cd.setText("Marcar " + le.getNomeCompleto() + " como Desistiu?");
                cd.setCancelable(true);
                cd.setConfirmText("Sim");
                cd.addConfirmListener(ev -> {
                    le.setEstado(ListaEspera.Estado.DESISTIU);
                    listaEsperaRepository.save(le);
                    atualizar();
                });
                cd.open();
            });
            actions.add(des);

            return actions;
        }).setHeader("Ações").setAutoWidth(true);

        grid.addColumn(le -> le.getDataInscricao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .setHeader("Data").setAutoWidth(true).setSortable(true);

        grid.addColumn(ListaEspera::getNomeCompleto)
                .setHeader("Nome").setAutoWidth(true).setSortable(true);

        grid.addColumn(le -> le.getTurma() != null ? le.getTurma().getDescricao() : "—")
                .setHeader("Turma").setAutoWidth(true);

        grid.addColumn(ListaEspera::getTelemovel).setHeader("Telemóvel").setAutoWidth(true);
        grid.addColumn(ListaEspera::getEmail).setHeader("Email").setAutoWidth(true);

        grid.addComponentColumn(le -> {
            Span badge = new Span(le.getEstado().name());
            String[] style = switch (le.getEstado()) {
                case AGUARDA -> new String[]{"#fff3e0", "#E67E22"};
                case NOTIFICADO -> new String[]{"#e3f2fd", "#1976D2"};
                case CONVERTIDO -> new String[]{"#e8f5e9", "#27AE60"};
                case DESISTIU -> new String[]{"#fce4ec", "#C62828"};
            };
            badge.getStyle()
                    .set("background", style[0]).set("color", style[1])
                    .set("padding", "2px 8px").set("border-radius", "10px")
                    .set("font-size", "11px").set("font-weight", "600");
            return badge;
        }).setHeader("Estado").setAutoWidth(true);

        return grid;
    }

    private void atualizar() {
        Studio studio = TenantContext.getCurrentStudio();
        Turma turma = filtroTurma != null ? filtroTurma.getValue() : null;

        List<ListaEspera> items;
        if (turma != null) {
            items = listaEsperaRepository.findByTurmaAndStudioOrderByDataInscricaoAsc(turma, studio);
        } else {
            items = studio != null
                    ? listaEsperaRepository.findByStudioOrderByDataInscricaoAsc(studio)
                    : listaEsperaRepository.findAll();
        }
        grid.setItems(items);
    }

    private void abrirDialog(ListaEspera entrada) {
        boolean novo = entrada == null;
        ListaEspera le = novo ? new ListaEspera() : entrada;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(novo ? "Adicionar à Lista de Espera" : "Editar");
        dialog.setWidth("420px");

        TextField nome = new TextField("Nome Completo");
        nome.setRequired(true);
        nome.setWidthFull();
        nome.setValue(le.getNomeCompleto() != null ? le.getNomeCompleto() : "");

        TextField telemovel = new TextField("Telemóvel");
        telemovel.setWidthFull();
        telemovel.setValue(le.getTelemovel() != null ? le.getTelemovel() : "");

        TextField email = new TextField("Email");
        email.setWidthFull();
        email.setValue(le.getEmail() != null ? le.getEmail() : "");

        Studio studio = TenantContext.getCurrentStudio();
        ComboBox<Turma> turmaCombo = new ComboBox<>("Turma");
        List<Turma> turmas = studio != null
                ? turmaRepository.findAllByStudio(studio)
                : turmaRepository.findAll();
        turmaCombo.setItems(turmas);
        turmaCombo.setItemLabelGenerator(t -> t.getDescricao() + " (" + t.getCodigo() + ")");
        turmaCombo.setRequired(true);
        turmaCombo.setWidthFull();
        if (le.getTurma() != null) turmaCombo.setValue(le.getTurma());

        FormLayout form = new FormLayout(nome, telemovel, email, turmaCombo);
        form.setWidthFull();

        Button guardar = new Button("Guardar", e -> {
            if (nome.isEmpty() || turmaCombo.getValue() == null) {
                Notification.show("Nome e Turma são obrigatórios");
                return;
            }
            le.setNomeCompleto(nome.getValue().trim());
            le.setTelemovel(telemovel.getValue().trim());
            le.setEmail(email.getValue().trim());
            le.setTurma(turmaCombo.getValue());
            le.setStudio(studio);
            if (novo) le.setEstado(ListaEspera.Estado.AGUARDA);
            listaEsperaRepository.save(le);
            atualizar();
            dialog.close();
            Notification.show("Guardado").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(form);
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }
}
