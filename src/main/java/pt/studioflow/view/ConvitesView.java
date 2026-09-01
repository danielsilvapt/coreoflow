package pt.studioflow.view;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Autowired;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;
import pt.studioflow.service.PdfService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@PageTitle("Convites | CoreoFlow")
@Route(value = "convites", layout = MainLayout.class)
@RolesAllowed({ "ADMIN" })
public class ConvitesView extends VerticalLayout {

    private final ConviteRepository conviteRepository;
    private final TurmaRepository turmaRepository;
    private final InscricaoEventoRepository inscricaoRepository;
    private final PdfService pdfService;

    private final Grid<Convite> grid = new Grid<>(Convite.class, false);
    private Grid.Column<Convite> colunaData;
    private final ListDataProvider<Convite> dataProvider = new ListDataProvider<>(new java.util.ArrayList<>());
    private TextField filterText = new TextField();

    private List<Turma> todasTurmas;
    private Map<Long, Integer> contagemAlunos;

    @Autowired
    public ConvitesView(ConviteRepository conviteRepository, TurmaRepository turmaRepository,
            InscricaoEventoRepository inscricaoRepository, PdfService pdfService) {
        this.conviteRepository = conviteRepository;
        this.turmaRepository = turmaRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.pdfService = pdfService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        pt.studioflow.model.Studio _studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        this.todasTurmas = _studio != null ? turmaRepository.findAllByStudio(_studio) : turmaRepository.findAll();
        this.contagemAlunos = turmaRepository.getContagemAlunosPorTurma().stream()
                .collect(Collectors.toMap(obj -> (Long) obj[0], obj -> (Integer) obj[1]));

        configurarUI();
    }

    private void configurarUI() {
        H2 titulo = new H2("Gestão de Eventos");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        Button btnNovo = ViewUtils.botaoNovo("Novo Evento",
                e -> new ConviteDialog(conviteRepository, this::atualizarDados).open());

        filterText.setPlaceholder("Filtrar por evento...");
        filterText.setClearButtonVisible(true);
        filterText.setPrefixComponent(VaadinIcon.SEARCH.create());
        filterText.setValueChangeMode(ValueChangeMode.LAZY);
        filterText.addValueChangeListener(e -> aplicarFiltro());

        configurarGrid();
        grid.setItems(dataProvider);

        add(ViewUtils.toolbar(filterText, btnNovo), grid);
        atualizarDados();
    }


    private void aplicarFiltro() {
        if (dataProvider != null) {
            dataProvider.setFilter(convite -> {
                if (filterText.getValue() == null || filterText.getValue().isEmpty())
                    return true;
                return convite.getEvento().toLowerCase().contains(filterText.getValue().toLowerCase());
            });
        }
    }

    private void configurarGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COLUMN_BORDERS);

        // PDF
        grid.addComponentColumn(c -> {
            StreamResource res = new StreamResource("Ficha_" + c.getId() + ".pdf", (stream, session) -> {
                List<Turma> turmasConfirmadas = todasTurmas.stream()
                        .filter(t -> c.getParticipacoes().get(t.getId()) == StatusParticipacao.CONFIRMADO)
                        .collect(Collectors.toList());
                List<Aluno> alunosConfirmados = inscricaoRepository.findByConvite(c).stream()
                        .filter(InscricaoEvento::isConfirmado).map(InscricaoEvento::getAluno)
                        .collect(Collectors.toList());
                pdfService.escreverFichaEvento(c, turmasConfirmadas, contagemAlunos, alunosConfirmados, stream);
            });
            Anchor anchor = new Anchor(res, "");
            anchor.getElement().setAttribute("download", true);
            Button btnPdf = new Button(VaadinIcon.FILE.create());
            btnPdf.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            anchor.add(btnPdf);
            return anchor;
        }).setHeader("PDF").setWidth("70px").setFlexGrow(0);

        // EVENTO COM BADGE VERDE SE PASSADO
        grid.addComponentColumn(c -> {
            Span span = new Span(c.getEvento());
            if (c.getData() != null && c.getData().isBefore(LocalDate.now())) {
                span.getElement().getThemeList().add("badge success"); // ESTA É A INDICAÇÃO VERDE
            }
            return span;
        }).setHeader("Evento").setSortable(true).setComparator(Convite::getEvento).setFrozen(true).setAutoWidth(true);

        // DATA COM BADGE VERDE SE PASSADO
        colunaData = grid.addComponentColumn(c -> {
            String dataFormatada = c.getData() != null ? c.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "";
            Span span = new Span(dataFormatada);
            if (c.getData() != null && c.getData().isBefore(LocalDate.now())) {
                span.getElement().getThemeList().add("badge success"); // ESTA É A INDICAÇÃO VERDE
            }
            return span;
        }).setHeader("Data").setSortable(true).setComparator(Convite::getData).setAutoWidth(true);

        grid.addColumn(Convite::getHora).setHeader("Hora").setSortable(true).setAutoWidth(true);

        grid.addColumn(c -> {
            return todasTurmas.stream()
                    .filter(t -> c.getParticipacoes().get(t.getId()) == StatusParticipacao.CONFIRMADO)
                    .mapToInt(t -> contagemAlunos.getOrDefault(t.getId(), 0)).sum();
        }).setHeader("Total Alunos").setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(c -> {
            Button btnObs = new Button(VaadinIcon.CHAT.create());
            btnObs.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            if (c.getObservacoes() != null && !c.getObservacoes().isEmpty()) {
                btnObs.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            }
            btnObs.addClickListener(e -> abrirDialogObservacoes(c));
            return btnObs;
        }).setHeader("Obs").setWidth("80px").setFlexGrow(0);

        // TURMAS COM COMBOBOX COLORIDO
        for (Turma t : todasTurmas) {
            grid.addComponentColumn(convite -> {
                ComboBox<StatusParticipacao> combo = new ComboBox<>();
                combo.setItems(StatusParticipacao.values());
                combo.setItemLabelGenerator(StatusParticipacao::getLabel);
                combo.setWidth("140px");
                combo.addThemeVariants(ComboBoxVariant.LUMO_SMALL);

                StatusParticipacao status = convite.getParticipacoes().getOrDefault(t.getId(),
                        StatusParticipacao.NAO_VAI);
                combo.setValue(status);

                // Aplicar cor inicial
                aplicarCorStatus(combo, status);

                combo.addValueChangeListener(e -> {
                    if (e.getValue() != null) {
                        convite.getParticipacoes().put(t.getId(), e.getValue());
                        conviteRepository.save(convite);
                        aplicarCorStatus(combo, e.getValue());
                        grid.getDataProvider().refreshItem(convite);
                    }
                });
                return combo;
            })
                    .setHeader(t.getDescricao())
                    .setSortable(true)
                    .setComparator(
                            c -> c.getParticipacoes().getOrDefault(t.getId(), StatusParticipacao.NAO_VAI).ordinal())
                    .setAutoWidth(true);
        }
    }

    private void aplicarCorStatus(ComboBox<StatusParticipacao> combo, StatusParticipacao status) {
        String corFundo = switch (status) {
            case CONFIRMADO -> "#d4edda"; // VERDE para confirmados
            case PENDENTE -> "#fff3cd"; // AMARELO
            case NAO_VAI -> "#f8d7da"; // VERMELHO
            case AGUARDA_COREOGRAFIA -> "#cce5ff"; // AZUL
        };

        // Injetar o estilo diretamente no elemento de input do Vaadin
        combo.getElement().executeJs(
                "this.inputElement.style.backgroundColor = $0;" +
                        "this.inputElement.style.fontWeight = 'bold';",
                corFundo);
    }

    private void abrirDialogObservacoes(Convite c) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Notas: " + c.getEvento());
        dialog.setWidth("500px");
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        Div resumo = new Div();
        resumo.setWidthFull();
        resumo.getStyle().set("background", "#f6f8fa").set("padding", "12px").set("border-radius", "8px");

        String conf = todasTurmas.stream()
                .filter(t -> c.getParticipacoes().get(t.getId()) == StatusParticipacao.CONFIRMADO)
                .map(Turma::getDescricao).collect(Collectors.joining(", "));
        String pend = todasTurmas.stream()
                .filter(t -> c.getParticipacoes().get(t.getId()) == StatusParticipacao.PENDENTE)
                .map(Turma::getDescricao).collect(Collectors.joining(", "));

        resumo.add(new Html("<div><b>✅ Confirmadas:</b> " + (conf.isEmpty() ? "---" : conf) +
                "<br><b>⏳ Pendentes:</b> " + (pend.isEmpty() ? "---" : pend) + "</div>"));

        TextArea txtObs = new TextArea("Observações Gerais");
        txtObs.setValue(c.getObservacoes() != null ? c.getObservacoes() : "");
        txtObs.setWidthFull();
        txtObs.setHeight("250px");

        layout.add(resumo, txtObs);
        Button save = new Button("Guardar", e -> {
            c.setObservacoes(txtObs.getValue());
            conviteRepository.save(c);
            grid.getDataProvider().refreshItem(c);
            dialog.close();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(layout);
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), save);
        dialog.open();
    }

    private void atualizarDados() {
        pt.studioflow.model.Studio _s = pt.studioflow.config.TenantContext.getCurrentStudio();
        List<Convite> convites = _s != null ? conviteRepository.findAllByStudio(_s) : conviteRepository.findAll();
        dataProvider.getItems().clear();
        dataProvider.getItems().addAll(convites);
        dataProvider.refreshAll();
    }
}
