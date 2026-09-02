package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Lead;
import pt.studioflow.model.Lead.EstadoLead;
import pt.studioflow.model.Lead.TipoLead;
import pt.studioflow.repository.LeadRepository;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Pipeline comercial da CoreoFlow: escolas, associações e outras
 * organizações com quem estamos em processo de venda (demos, propostas,
 * negociação) — antes de se tornarem um Studio cliente da plataforma.
 */
@Route(value = "admin/leads", layout = MainLayout.class)
@PageTitle("Leads & Prospecção | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class LeadsView extends VerticalLayout {

    private static final DateTimeFormatter DATA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LeadRepository leadRepository;
    private final HorizontalLayout cardsRow = new HorizontalLayout();
    private final ComboBox<EstadoLead> filtroEstado = new ComboBox<>("Filtrar por estado");
    private Grid<Lead> grid;

    public LeadsView(LeadRepository leadRepository) {
        this.leadRepository = leadRepository;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Leads & Prospecção");
        titulo.getStyle().set("margin", "0 0 8px 0").set("padding", "20px 20px 0 20px");

        cardsRow.setWidthFull();
        cardsRow.setSpacing(true);
        cardsRow.getStyle().set("padding", "0 20px 12px 20px");

        filtroEstado.setItems(EstadoLead.values());
        filtroEstado.setItemLabelGenerator(EstadoLead::getLabel);
        filtroEstado.setClearButtonVisible(true);
        filtroEstado.setPlaceholder("Todos os estados");
        filtroEstado.addValueChangeListener(e -> atualizar());

        add(titulo, cardsRow,
                ViewUtils.toolbar(filtroEstado, ViewUtils.botaoNovo("Novo Lead", e -> abrirDialogLead(null))),
                criarGrid());
        atualizar();
    }

    private Grid<Lead> criarGrid() {
        grid = new Grid<>(Lead.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);

        grid.addComponentColumn(l -> {
            HorizontalLayout acoes = new HorizontalLayout();
            acoes.setSpacing(false);

            Button editar = new Button(VaadinIcon.EDIT.create(), e -> abrirDialogLead(l));
            editar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            if (l.getTelefone() != null && !l.getTelefone().isBlank()) {
                Button whatsapp = new Button(VaadinIcon.CHAT.create(), e -> abrirWhatsApp(l));
                whatsapp.getStyle().set("color", "#25D366");
                whatsapp.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                acoes.add(whatsapp);
            }

            Button apagar = new Button(VaadinIcon.TRASH.create(), e -> confirmarApagar(l));
            apagar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            acoes.add(editar, apagar);
            return acoes;
        }).setHeader("Ações").setAutoWidth(true);

        grid.addColumn(Lead::getNome).setHeader("Nome").setAutoWidth(true).setSortable(true);
        grid.addColumn(l -> l.getTipo() != null ? l.getTipo().getLabel() : "").setHeader("Tipo").setAutoWidth(true);
        grid.addColumn(Lead::getNomeContacto).setHeader("Contacto").setAutoWidth(true);

        grid.addComponentColumn(l -> {
            VerticalLayout box = new VerticalLayout();
            box.setPadding(false);
            box.setSpacing(false);
            if (l.getTelefone() != null && !l.getTelefone().isBlank()) box.add(new Span(l.getTelefone()));
            if (l.getEmail() != null && !l.getEmail().isBlank()) box.add(new Span(l.getEmail()));
            return box;
        }).setHeader("Telefone / Email").setAutoWidth(true);

        grid.addComponentColumn(l -> {
            Span badge = new Span(l.getEstado() != null ? l.getEstado().getLabel() : "");
            String cor = l.getEstado() != null ? l.getEstado().getCor() : "#888";
            badge.getStyle()
                    .set("background", cor)
                    .set("color", "white")
                    .set("padding", "2px 10px")
                    .set("border-radius", "12px")
                    .set("font-size", "12px")
                    .set("font-weight", "600");
            return badge;
        }).setHeader("Estado").setAutoWidth(true).setSortable(true)
                .setComparator(Comparator.comparing(l -> l.getEstado() != null ? l.getEstado().name() : ""));

        grid.addColumn(l -> l.getDataDemo() != null ? l.getDataDemo().format(DATA_FMT) : "—")
                .setHeader("Data Demo").setAutoWidth(true).setSortable(true);

        grid.addColumn(l -> l.getProximoPasso() != null ? l.getProximoPasso() : "")
                .setHeader("Próximo Passo").setAutoWidth(true);

        return grid;
    }

    private void abrirDialogLead(Lead existente) {
        boolean novo = existente == null;
        Lead lead = novo ? new Lead() : existente;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(novo ? "Novo Lead" : "Editar " + lead.getNome());
        dialog.setWidth("640px");

        TextField nome = new TextField("Nome da escola/organização");
        nome.setWidthFull();
        nome.setValue(lead.getNome() != null ? lead.getNome() : "");

        ComboBox<TipoLead> tipo = new ComboBox<>("Tipo");
        tipo.setItems(TipoLead.values());
        tipo.setItemLabelGenerator(TipoLead::getLabel);
        tipo.setValue(lead.getTipo() != null ? lead.getTipo() : TipoLead.ESCOLA_DANCA);
        tipo.setWidthFull();

        TextField nomeContacto = new TextField("Pessoa de contacto");
        nomeContacto.setWidthFull();
        nomeContacto.setValue(lead.getNomeContacto() != null ? lead.getNomeContacto() : "");

        TextField telefone = new TextField("Telefone");
        telefone.setWidthFull();
        telefone.setValue(lead.getTelefone() != null ? lead.getTelefone() : "");

        EmailField email = new EmailField("Email");
        email.setWidthFull();
        email.setValue(lead.getEmail() != null ? lead.getEmail() : "");

        ComboBox<EstadoLead> estado = new ComboBox<>("Estado");
        estado.setItems(EstadoLead.values());
        estado.setItemLabelGenerator(EstadoLead::getLabel);
        estado.setValue(lead.getEstado() != null ? lead.getEstado() : EstadoLead.NOVO);
        estado.setWidthFull();

        DatePicker dataDemo = new DatePicker("Data da demo");
        dataDemo.setWidthFull();
        dataDemo.setValue(lead.getDataDemo());

        TextField origem = new TextField("Origem");
        origem.setPlaceholder("Indicação, Instagram, prospecção direta...");
        origem.setWidthFull();
        origem.setValue(lead.getOrigem() != null ? lead.getOrigem() : "");

        TextField proximoPasso = new TextField("Próximo passo");
        proximoPasso.setPlaceholder("Ex: Confirmar demo por WhatsApp");
        proximoPasso.setWidthFull();
        proximoPasso.setValue(lead.getProximoPasso() != null ? lead.getProximoPasso() : "");

        DatePicker dataProximoPasso = new DatePicker("Data do próximo passo");
        dataProximoPasso.setWidthFull();
        dataProximoPasso.setValue(lead.getDataProximoPasso());

        NumberField valorMensal = new NumberField("Valor mensal estimado (€)");
        valorMensal.setMin(0);
        valorMensal.setWidthFull();
        valorMensal.setValue(lead.getValorMensalEstimado() != null ? lead.getValorMensalEstimado() : 0.0);

        TextArea notas = new TextArea("Notas");
        notas.setWidthFull();
        notas.setMinHeight("100px");
        notas.setValue(lead.getNotas() != null ? lead.getNotas() : "");

        FormLayout form = new FormLayout(nome, tipo, nomeContacto, telefone, email, estado,
                dataDemo, origem, proximoPasso, dataProximoPasso, valorMensal);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("420px", 2));

        Button guardar = new Button("Guardar", e -> {
            if (nome.isEmpty()) { Notification.show("Nome obrigatório"); return; }
            lead.setNome(nome.getValue().trim());
            lead.setTipo(tipo.getValue());
            lead.setNomeContacto(nomeContacto.getValue().trim());
            lead.setTelefone(telefone.getValue().trim());
            lead.setEmail(email.getValue().trim());
            lead.setEstado(estado.getValue());
            lead.setDataDemo(dataDemo.getValue());
            lead.setOrigem(origem.getValue().trim());
            lead.setProximoPasso(proximoPasso.getValue().trim());
            lead.setDataProximoPasso(dataProximoPasso.getValue());
            lead.setValorMensalEstimado(valorMensal.getValue() != null ? valorMensal.getValue() : 0.0);
            lead.setNotas(notas.getValue().trim());
            leadRepository.save(lead);
            atualizar();
            dialog.close();
            Notification.show("Lead guardado").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new VerticalLayout(form, notas));
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }

    private void confirmarApagar(Lead lead) {
        ConfirmDialog confirm = new ConfirmDialog();
        confirm.setHeader("Apagar lead");
        confirm.setText("Tens a certeza que queres apagar \"" + lead.getNome() + "\"? Esta ação não pode ser desfeita.");
        confirm.setCancelable(true);
        confirm.setConfirmText("Apagar");
        confirm.setConfirmButtonTheme("error primary");
        confirm.addConfirmListener(e -> {
            leadRepository.delete(lead);
            atualizar();
            Notification.show("Lead apagado").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        confirm.open();
    }

    private void abrirWhatsApp(Lead lead) {
        String telemovel = lead.getTelefone();
        if (telemovel == null || telemovel.isEmpty()) return;
        String primeiroNome = (lead.getNomeContacto() != null && !lead.getNomeContacto().isBlank())
                ? lead.getNomeContacto().split(" ")[0] : "";
        String mensagem = "Olá" + (primeiroNome.isBlank() ? "" : " " + primeiroNome)
                + "! Daqui é da CoreoFlow. Gostaríamos de combinar uma demo da plataforma para a " + lead.getNome() + ".";
        String encoded = java.net.URLEncoder.encode(mensagem, java.nio.charset.StandardCharsets.UTF_8);
        String url = "https://wa.me/351" + telemovel.replaceAll("[^0-9]", "") + "?text=" + encoded;
        com.vaadin.flow.component.UI.getCurrent().getPage().open(url, "_blank");
    }

    private void atualizarCards(List<Lead> leads) {
        cardsRow.removeAll();
        long total = leads.size();
        long demosMarcadas = leads.stream().filter(l -> l.getEstado() == EstadoLead.DEMO_MARCADA).count();
        long emNegociacao = leads.stream()
                .filter(l -> l.getEstado() == EstadoLead.PROPOSTA_ENVIADA || l.getEstado() == EstadoLead.NEGOCIACAO)
                .count();
        long ganhos = leads.stream().filter(l -> l.getEstado() == EstadoLead.GANHO).count();

        cardsRow.add(
                criarCard("Total Leads", String.valueOf(total), VaadinIcon.USERS, "#4A90E2"),
                criarCard("Demos Marcadas", String.valueOf(demosMarcadas), VaadinIcon.CLOCK, "#7B61FF"),
                criarCard("Em Negociação", String.valueOf(emNegociacao), VaadinIcon.CHAT, "#F9A825"),
                criarCard("Ganhos", String.valueOf(ganhos), VaadinIcon.CHECK_CIRCLE, "#27AE60")
        );
    }

    private Component criarCard(String label, String valor, VaadinIcon icone, String cor) {
        Icon icon = icone.create();
        icon.setSize("26px");
        icon.setColor(cor);

        Span valorSpan = new Span(valor);
        valorSpan.getStyle().set("font-size", "28px").set("font-weight", "700").set("color", cor).set("line-height", "1");

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("font-size", "12px").set("color", "#666")
                .set("text-transform", "uppercase").set("letter-spacing", "0.8px");

        VerticalLayout card = new VerticalLayout(icon, valorSpan, labelSpan);
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.setSpacing(false);
        card.setPadding(true);
        card.getStyle()
                .set("background", "white")
                .set("border-radius", "16px")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.08)")
                .set("border", "1px solid #f0f0f0")
                .set("min-width", "140px")
                .set("flex", "1");
        return card;
    }

    private void atualizar() {
        List<Lead> leads = leadRepository.findAllByOrderByCreatedAtDesc();
        atualizarCards(leads);
        EstadoLead filtro = filtroEstado.getValue();
        grid.setItems(filtro == null ? leads : leads.stream().filter(l -> l.getEstado() == filtro).toList());
    }
}
