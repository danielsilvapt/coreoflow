package pt.studioflow.view;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import pt.studioflow.model.RegistoEmail;
import pt.studioflow.model.RegistoEmail.Estado;
import pt.studioflow.model.RegistoEmailDestinatario;
import pt.studioflow.model.RegistoEmailDestinatario.EstadoEntrega;
import pt.studioflow.repository.RegistoEmailRepository;
import pt.studioflow.service.HistoricoComunicacaoService;
import pt.studioflow.util.DataUtil;

/**
 * Separador "Histórico" do Centro de Comunicação: lista os emails enviados,
 * por quem, para quem, se o servidor de email aceitou o envio e — com o webhook
 * do Brevo — se chegou a cada aluno. Clicar numa linha abre o detalhe.
 */
public class HistoricoComunicacaoPanel extends VerticalLayout {

    private final RegistoEmailRepository repo;
    private final HistoricoComunicacaoService historico;
    private final Long studioId;
    /** Se não for null, só mostra os envios deste utilizador (professores). */
    private final String apenasUtilizador;

    private final ComboBox<String> periodo = new ComboBox<>("Período");
    private final ComboBox<Estado> estado = new ComboBox<>("Envio");
    private final Checkbox soFalhas = new Checkbox("Só com falhas de entrega");
    private final TextField pesquisa = new TextField("Pesquisar");
    private final Grid<RegistoEmail> grid = new Grid<>();

    public HistoricoComunicacaoPanel(RegistoEmailRepository repo, HistoricoComunicacaoService historico,
            Long studioId, String apenasUtilizador) {
        this.repo = repo;
        this.historico = historico;
        this.studioId = studioId;
        this.apenasUtilizador = apenasUtilizador;

        setSizeFull();
        setPadding(false);

        periodo.setItems("Últimos 7 dias", "Últimos 30 dias", "Últimos 90 dias", "Últimos 12 meses", "Tudo");
        periodo.setValue("Últimos 30 dias");
        periodo.setWidth("170px");
        estado.setItems(Estado.values());
        estado.setItemLabelGenerator(Estado::getLabel);
        estado.setPlaceholder("Todos");
        estado.setClearButtonVisible(true);
        estado.setWidth("150px");
        pesquisa.setPlaceholder("assunto, aluno, email, turma…");
        pesquisa.setClearButtonVisible(true);
        pesquisa.setValueChangeMode(ValueChangeMode.LAZY);
        pesquisa.setWidth("260px");

        periodo.addValueChangeListener(e -> recarregar());
        estado.addValueChangeListener(e -> recarregar());
        soFalhas.addValueChangeListener(e -> recarregar());
        pesquisa.addValueChangeListener(e -> recarregar());

        Button atualizar = new Button("Atualizar", VaadinIcon.REFRESH.create(), e -> recarregar());
        atualizar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout filtros = new HorizontalLayout(periodo, estado, pesquisa, soFalhas, atualizar);
        filtros.setAlignItems(FlexComponent.Alignment.BASELINE);
        filtros.getStyle().set("flex-wrap", "wrap");
        add(filtros);

        configurarGrid();
        add(grid);
        expand(grid);
    }

    private void configurarGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        grid.setEmptyStateText("Ainda não há comunicações enviadas neste período.");

        grid.addColumn(r -> r.getData() != null ? DataUtil.formatar(r.getData()) : "")
                .setHeader("Data").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(RegistoEmail::getRemetenteApresentacao)
                .setHeader("Enviado por").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(RegistoEmail::getTurma).setHeader("Turma").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(RegistoEmail::getAssunto).setHeader("Assunto").setFlexGrow(1);
        grid.addColumn(r -> r.getNumDestinatarios() + (r.getNumDestinatarios() == 1 ? " aluno" : " alunos"))
                .setHeader("Para").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(r -> badge(r.getEstado() != null ? r.getEstado().getLabel() : "",
                r.getEstado() != null ? r.getEstado().getCor() : "#999"))
                .setHeader("Envio").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::resumoEntrega).setHeader("Entrega").setAutoWidth(true).setFlexGrow(0);

        grid.addItemClickListener(e -> abrirDetalhe(e.getItem()));
    }

    /** "12 entregues · 5 abertos · 1 falhou", ou o aviso de alunos sem email. */
    private Component resumoEntrega(RegistoEmail r) {
        Span s = new Span();
        s.getStyle().set("font-size", "12px");
        if (r.getEstado() != Estado.ENVIADO) {
            s.setText("—");
            return s;
        }
        if (r.getNumEntregues() == 0 && r.getNumFalhados() == 0) {
            s.setText("a aguardar confirmação");
            s.getStyle().set("color", "var(--cf-text-tertiary)");
        } else {
            Span ok = new Span(r.getNumEntregues() + "/" + r.getNumDestinatarios() + " entregues");
            ok.getStyle().set("color", "#27AE60").set("font-weight", "600");
            s.add(ok);
            if (r.getNumAbertos() > 0) {
                s.add(new Span(" · " + r.getNumAbertos() + " abertos"));
            }
            if (r.getNumFalhados() > 0) {
                Span f = new Span(" · " + r.getNumFalhados() + (r.getNumFalhados() == 1 ? " falhou" : " falharam"));
                f.getStyle().set("color", "var(--lumo-error-text-color)").set("font-weight", "600");
                s.add(f);
            }
        }
        int semEmail = r.getListaSemEmail().size();
        if (semEmail > 0) {
            Span aviso = new Span(" · " + semEmail + " sem email");
            aviso.getStyle().set("color", "var(--lumo-error-text-color)");
            s.add(aviso);
        }
        return s;
    }

    public void recarregar() {
        if (studioId == null) {
            grid.setItems(List.of());
            return;
        }
        Estado e = estado.getValue();
        boolean falhas = soFalhas.getValue();
        String texto = pesquisa.getValue() != null && !pesquisa.getValue().isBlank() ? pesquisa.getValue().trim() : null;
        LocalDateTime desde = desde();
        grid.setItems(q -> repo.pesquisar(studioId, e, apenasUtilizador, falhas, texto, desde,
                PageRequest.of(q.getPage(), q.getPageSize())).stream());
    }

    private LocalDateTime desde() {
        return switch (periodo.getValue() == null ? "" : periodo.getValue()) {
            case "Últimos 7 dias" -> LocalDateTime.now().minusDays(7);
            case "Últimos 30 dias" -> LocalDateTime.now().minusDays(30);
            case "Últimos 90 dias" -> LocalDateTime.now().minusDays(90);
            case "Últimos 12 meses" -> LocalDateTime.now().minusMonths(12);
            default -> null;
        };
    }

    private void abrirDetalhe(RegistoEmail r) {
        Dialog d = new Dialog();
        d.setHeaderTitle(r.getAssunto() != null && !r.getAssunto().isBlank() ? r.getAssunto() : "(sem assunto)");
        d.setWidth("760px");

        VerticalLayout c = new VerticalLayout();
        c.setPadding(false);

        HorizontalLayout topo = new HorizontalLayout(
                badge(r.getEstado() != null ? r.getEstado().getLabel() : "", r.getEstado() != null ? r.getEstado().getCor() : "#999"),
                meta((r.getData() != null ? DataUtil.formatar(r.getData()) : "") + "  ·  por "
                        + r.getRemetenteApresentacao()
                        + (r.getTurma() != null ? "  ·  " + r.getTurma() : "")));
        topo.setAlignItems(FlexComponent.Alignment.CENTER);
        c.add(topo);

        if (r.getEstado() == Estado.FALHOU) {
            c.add(caixa("O servidor de email não aceitou o envio — nenhum aluno recebeu esta mensagem."
                    + (r.getErro() != null ? "\n\nErro: " + r.getErro() : ""), "var(--lumo-error-color-10pct)"));
        } else if (r.getEstado() == Estado.BLOQUEADO) {
            c.add(caixa("Não enviado: o envio de emails está desativado para este estúdio.",
                    "var(--lumo-warning-color-10pct, #fff4e0)"));
        }

        c.add(titulo("Destinatários (" + r.getNumDestinatarios() + ")"));
        c.add(gridDestinatarios(historico.destinatarios(r.getId()), r.getEstado() == Estado.ENVIADO));

        if (!r.getListaSemEmail().isEmpty()) {
            c.add(titulo("Sem email registado — não receberam (" + r.getListaSemEmail().size() + ")"));
            c.add(caixa(String.join("\n", r.getListaSemEmail()), "var(--cf-surface-subtle)"));
        }
        if (r.getProfessorCopia() != null) {
            c.add(meta("Professor em cópia: " + r.getProfessorCopia()));
        }
        if (r.getAnexos() != null && !r.getAnexos().isBlank()) {
            c.add(titulo("Anexos"));
            c.add(meta(r.getAnexos()));
        }
        c.add(titulo("Mensagem"));
        c.add(caixa(r.getMensagem() != null ? r.getMensagem() : "", "var(--cf-surface-subtle)"));

        d.add(c);
        d.getFooter().add(new Button("Fechar", e -> d.close()));
        d.open();
    }

    private Grid<RegistoEmailDestinatario> gridDestinatarios(List<RegistoEmailDestinatario> itens, boolean enviado) {
        Grid<RegistoEmailDestinatario> g = new Grid<>();
        g.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        g.addColumn(RegistoEmailDestinatario::getNome).setHeader("Aluno").setFlexGrow(1);
        g.addColumn(RegistoEmailDestinatario::getEmail).setHeader("Email").setFlexGrow(1);
        g.addComponentColumn(x -> {
            if (!enviado) {
                return new Span("—");
            }
            EstadoEntrega e = x.getEstado() != null ? x.getEstado() : EstadoEntrega.PENDENTE;
            Span b = (Span) badge(e.getLabel(), e.getCor());
            String dica = (x.getDetalhe() != null ? x.getDetalhe() : "")
                    + (x.getAtualizadoEm() != null ? (x.getDetalhe() != null ? " · " : "")
                            + DataUtil.formatar(x.getAtualizadoEm()) : "");
            if (!dica.isBlank()) {
                b.getElement().setAttribute("title", dica);
            }
            return b;
        }).setHeader("Entrega").setAutoWidth(true).setFlexGrow(0);
        g.addColumn(x -> x.getDetalhe() != null ? x.getDetalhe() : "").setHeader("Motivo").setFlexGrow(1);
        g.setItems(itens);
        g.setWidthFull();
        g.setAllRowsVisible(itens.size() <= 8);
        if (itens.size() > 8) {
            g.setHeight("300px");
        }
        return g;
    }

    private static Span titulo(String t) {
        Span s = new Span(t);
        s.getStyle().set("font-weight", "700").set("font-size", "13px").set("margin-top", "8px");
        return s;
    }

    private static Span meta(String t) {
        Span s = new Span(t);
        s.getStyle().set("font-size", "12px").set("color", "var(--cf-text-tertiary)");
        return s;
    }

    private static Div caixa(String texto, String fundo) {
        Div d = new Div();
        d.setText(texto);
        d.getStyle().set("white-space", "pre-wrap").set("font-size", "13px").set("background", fundo)
                .set("padding", "10px").set("border-radius", "6px").set("width", "100%")
                .set("box-sizing", "border-box").set("max-height", "260px").set("overflow", "auto");
        return d;
    }

    private static Component badge(String label, String cor) {
        Span s = new Span(label);
        s.getStyle().set("background", cor).set("color", "white")
                .set("padding", "1px 8px").set("border-radius", "10px")
                .set("font-size", "11px").set("font-weight", "700").set("white-space", "nowrap");
        return s;
    }
}
