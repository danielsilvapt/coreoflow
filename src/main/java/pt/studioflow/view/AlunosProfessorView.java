package pt.studioflow.view;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.grid.HeaderRow;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Turma;
import pt.studioflow.model.User;
import pt.studioflow.repository.UserRepository;
import pt.studioflow.service.TurmaService;

@PageTitle("Os Meus Alunos | CoreoFlow")
@Route(value = "alunos-professor", layout = MainLayout.class)
@RolesAllowed({ "DELEG", "PROF", "ADMIN" })
public class AlunosProfessorView extends VerticalLayout {

    private final TurmaService turmaService;
    private final UserRepository userRepository;

    private Grid<AlunoDTO> grid;
    private ListDataProvider<AlunoDTO> dataProvider;
    private final Map<String, String> filtrosAtivos = new HashMap<>();

    @Autowired
    public AlunosProfessorView(TurmaService turmaService, UserRepository userRepository) {
        this.turmaService = turmaService;
        this.userRepository = userRepository;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Os Meus Alunos");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);
        configurarUI();
        carregarDados();
    }


    private void configurarUI() {
        grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);

        // 1. Definição das Colunas
        Grid.Column<AlunoDTO> colAluno = grid.addComponentColumn(dto -> {
            Span nomeDesktop = new Span(dto.getAluno().getNomeCompleto());
            nomeDesktop.addClassName("desktop-only");
            Span nomeMobile = new Span(formatarNomeCurto(dto.getAluno().getNomeCompleto()));
            nomeMobile.addClassName("mobile-only");

            VerticalLayout layout = new VerticalLayout(nomeDesktop, nomeMobile);
            layout.setPadding(false);
            layout.setSpacing(false);
            return layout;
        }).setHeader("Aluno").setSortable(true).setKey("aluno").setAutoWidth(true);

        Grid.Column<AlunoDTO> colTurmas = grid.addColumn(dto -> dto.getNomesTurmas())
                .setHeader("Turmas").setSortable(true).setKey("turmas").setAutoWidth(true);

        Grid.Column<AlunoDTO> colNascimento = grid
                .addColumn(dto -> dto.getAluno().getDataNascimento() == null ? "-"
                        : dto.getAluno().getDataNascimento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .setHeader("Nascimento").setSortable(true).setKey("nascimento").setAutoWidth(true);

        Grid.Column<AlunoDTO> colIdade = grid.addComponentColumn(dto -> {
            HorizontalLayout hl = new HorizontalLayout();
            hl.setJustifyContentMode(JustifyContentMode.CENTER);
            hl.setWidthFull();

            if (dto.getAluno().getDataNascimento() == null)
                return hl;
            int idade = java.time.Period.between(dto.getAluno().getDataNascimento(), LocalDate.now()).getYears();
            hl.add(new Span(idade + " anos"));

            if (dto.getAluno().getDataNascimento().getMonth() == LocalDate.now().getMonth()) {
                Icon gift = new Icon(VaadinIcon.GIFT);
                gift.setColor("#e67e22");
                gift.setSize("16px");
                hl.add(gift);
            }
            return hl;
        }).setHeader("Idade")
                .setTextAlign(ColumnTextAlign.CENTER)
                .setSortable(true)
                .setKey("idade")
                .setAutoWidth(true);

        Grid.Column<AlunoDTO> colTlm = grid
                .addColumn(dto -> dto.getAluno().getTelemovel() != null ? dto.getAluno().getTelemovel() : "-")
                .setHeader("Telemóvel").setSortable(true).setKey("tlm").setAutoWidth(true);

        Grid.Column<AlunoDTO> colEml = grid
                .addColumn(dto -> dto.getAluno().getEmail() != null ? dto.getAluno().getEmail() : "-")
                .setHeader("Email").setSortable(true).setKey("eml").setAutoWidth(true);

        // 2. Linha de Filtros
        HeaderRow filterRow = grid.appendHeaderRow();
        configurarCampoFiltro(colAluno, filterRow, "Nome...");
        configurarFiltroComboTurma(colTurmas, filterRow); // Filtro ComboBox
        configurarCampoFiltro(colNascimento, filterRow, "Data...");
        configurarCampoFiltro(colIdade, filterRow, "Ex: >18");
        configurarCampoFiltro(colTlm, filterRow, "Tlm...");
        configurarCampoFiltro(colEml, filterRow, "Email...");

        add(grid);
        expand(grid);
    }

    private void configurarCampoFiltro(Grid.Column<AlunoDTO> column, HeaderRow filterRow, String placeholder) {
        TextField filterField = new TextField();
        filterField.setPlaceholder(placeholder);
        filterField.setValueChangeMode(ValueChangeMode.EAGER);
        filterField.setWidthFull();
        filterField.addThemeVariants(TextFieldVariant.LUMO_SMALL);

        filterField.addValueChangeListener(event -> {
            filtrosAtivos.put(column.getKey(), event.getValue().trim().toLowerCase());
            aplicarFiltros();
        });

        filterRow.getCell(column).setComponent(filterField);
    }

    private void configurarFiltroComboTurma(Grid.Column<AlunoDTO> column, HeaderRow filterRow) {
        ComboBox<String> comboTurma = new ComboBox<>();
        comboTurma.setPlaceholder("Filtrar...");
        comboTurma.setClearButtonVisible(true);
        comboTurma.setWidthFull();
        comboTurma.addThemeVariants(ComboBoxVariant.LUMO_SMALL);

        // Popula a combo com as turmas baseadas nos dados atuais
        comboTurma.addFocusListener(e -> {
            Set<String> turmasUnicas = dataProvider.getItems().stream()
                    .flatMap(dto -> Arrays.stream(dto.getNomesTurmas().split(", ")))
                    .collect(Collectors.toCollection(TreeSet::new));
            comboTurma.setItems(turmasUnicas);
        });

        comboTurma.addValueChangeListener(event -> {
            filtrosAtivos.put(column.getKey(), event.getValue() == null ? "" : event.getValue().toLowerCase());
            aplicarFiltros();
        });

        filterRow.getCell(column).setComponent(comboTurma);
    }

    private void aplicarFiltros() {
        if (dataProvider == null)
            return;
        dataProvider.setFilter(dto -> {
            for (Map.Entry<String, String> filtro : filtrosAtivos.entrySet()) {
                String termo = filtro.getValue();
                if (termo == null || termo.isEmpty())
                    continue;

                boolean corresponde = switch (filtro.getKey()) {
                    case "aluno" -> dto.getAluno().getNomeCompleto().toLowerCase().contains(termo);
                    case "turmas" -> dto.getNomesTurmas().toLowerCase().contains(termo);
                    case "nascimento" -> dto.getAluno().getDataNascimento() != null &&
                            dto.getAluno().getDataNascimento().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                    .contains(termo);
                    case "tlm" ->
                        dto.getAluno().getTelemovel() != null && dto.getAluno().getTelemovel().contains(termo);
                    case "idade" -> {
                        if (dto.getAluno().getDataNascimento() == null)
                            yield false;
                        int idadeAluno = java.time.Period.between(dto.getAluno().getDataNascimento(), LocalDate.now())
                                .getYears();
                        String termoLimpo = termo.replace(" ", "");
                        try {
                            if (termoLimpo.startsWith(">="))
                                yield idadeAluno >= Integer.parseInt(termoLimpo.substring(2));
                            if (termoLimpo.startsWith("<="))
                                yield idadeAluno <= Integer.parseInt(termoLimpo.substring(2));
                            if (termoLimpo.startsWith(">"))
                                yield idadeAluno > Integer.parseInt(termoLimpo.substring(1));
                            if (termoLimpo.startsWith("<"))
                                yield idadeAluno < Integer.parseInt(termoLimpo.substring(1));
                            yield String.valueOf(idadeAluno).contains(termoLimpo);
                        } catch (Exception e) {
                            yield true;
                        }
                    }
                    default -> true;
                };
                if (!corresponde)
                    return false;
            }
            return true;
        });
    }

    private void carregarDados() {
        String profNomeNormalizado = normalizar(getFirstNameFromDatabase());

        List<Turma> turmasDoProf = turmaService.findAllComplete().stream()
                .filter(t -> t.getProfessor() != null)
                .filter(t -> normalizar(t.getProfessor().getNome().split(" ")[0]).equals(profNomeNormalizado))
                .collect(Collectors.toList());

        Map<Aluno, Set<String>> alunoTurmasMap = new HashMap<>();
        for (Turma t : turmasDoProf) {
            t.getAlunosTurma().forEach(at -> {
                if (at.getAluno() != null) {
                    alunoTurmasMap.computeIfAbsent(at.getAluno(), k -> new TreeSet<>()).add(t.getDescricao());
                }
            });
        }

        List<AlunoDTO> dtos = alunoTurmasMap.entrySet().stream()
                .map(entry -> new AlunoDTO(entry.getKey(), String.join(", ", entry.getValue())))
                .collect(Collectors.toList());

        dataProvider = new ListDataProvider<>(dtos);

        // Ordenação por defeito pela coluna "Turmas"
        dataProvider.setSortOrder(AlunoDTO::getNomesTurmas, SortDirection.ASCENDING);

        grid.setDataProvider(dataProvider);
    }

    public static class AlunoDTO {
        private final Aluno aluno;
        private final String nomesTurmas;

        public AlunoDTO(Aluno aluno, String nomesTurmas) {
            this.aluno = aluno;
            this.nomesTurmas = nomesTurmas;
        }

        public Aluno getAluno() {
            return aluno;
        }

        public String getNomesTurmas() {
            return nomesTurmas;
        }
    }

    private String formatarNomeCurto(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank())
            return "";
        String[] partes = nomeCompleto.trim().split("\\s+");
        return partes.length <= 1 ? nomeCompleto : partes[0] + " " + partes[partes.length - 1];
    }

    private String getFirstNameFromDatabase() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "" : userRepository.findByPrincipalName(auth.getName()).map(User::getFirstName).orElse("");
    }

    private String normalizar(String texto) {
        if (texto == null)
            return "";
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}