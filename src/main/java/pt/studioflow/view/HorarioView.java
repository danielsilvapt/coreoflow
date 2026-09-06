package pt.studioflow.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aula;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Sala;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AulaRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.SalaRepository;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Vista de administração: o horário semanal de todas as aulas planeadas
 * ({@link Aula} = turma + dia da semana + hora + sala), com pesquisa livre e
 * filtros por professor, turma e sala. Só de leitura.
 */
@Route(value = "horario", layout = MainLayout.class)
@PageTitle("Horário | CoreoFlow")
@RolesAllowed("ADMIN")
public class HorarioView extends VerticalLayout {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DayOfWeek[] DIAS = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY };

    private final AulaRepository aulaRepository;

    private final ComboBox<Professor> filtroProfessor = new ComboBox<>("Professor");
    private final ComboBox<Turma> filtroTurma = new ComboBox<>("Turma");
    private final ComboBox<Sala> filtroSala = new ComboBox<>("Sala");
    private final TextField pesquisa = new TextField();
    private final Span resumo = new Span();
    private final FlexLayout board = new FlexLayout();

    private List<Aula> todasAulas = List.of();

    public HorarioView(AulaRepository aulaRepository, ProfessorRepository professorRepository,
            SalaRepository salaRepository) {
        this.aulaRepository = aulaRepository;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Horário das Aulas");
        titulo.getStyle().set("margin-top", "0");

        Studio studio = TenantContext.getCurrentStudio();
        todasAulas = (studio != null ? aulaRepository.findByStudioComHorario(studio)
                : aulaRepository.findAllComHorario()).stream()
                .filter(a -> a.getDia() != null && a.getHoraInicio() != null && a.getTurma() != null)
                .sorted(Comparator.comparing(Aula::getHoraInicio))
                .collect(Collectors.toList());

        List<Professor> professores = studio != null ? professorRepository.findAllByStudio(studio)
                : professorRepository.findAll();
        List<Sala> salas = studio != null ? salaRepository.findAllByStudio(studio) : salaRepository.findAll();

        filtroProfessor.setItems(professores.stream()
                .sorted(Comparator.comparing(p -> p.getNome() == null ? "" : p.getNome(), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList()));
        filtroProfessor.setItemLabelGenerator(p -> p.getNome() != null ? p.getNome() : "—");
        filtroProfessor.setClearButtonVisible(true);
        filtroProfessor.setWidth("200px");

        filtroTurma.setItems(todasAulas.stream().map(Aula::getTurma).filter(Objects::nonNull).distinct()
                .sorted(Comparator.comparing(Turma::getDescricao, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList()));
        filtroTurma.setItemLabelGenerator(Turma::getDescricao);
        filtroTurma.setClearButtonVisible(true);
        filtroTurma.setWidth("220px");

        filtroSala.setItems(salas.stream()
                .sorted(Comparator.comparing(s -> s.getNome() == null ? "" : s.getNome(), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList()));
        filtroSala.setItemLabelGenerator(s -> s.getNome() != null ? s.getNome() : "—");
        filtroSala.setClearButtonVisible(true);
        filtroSala.setWidth("180px");

        pesquisa.setPlaceholder("Pesquisar turma, modalidade, professor...");
        pesquisa.setPrefixComponent(VaadinIcon.SEARCH.create());
        pesquisa.setClearButtonVisible(true);
        pesquisa.setValueChangeMode(ValueChangeMode.LAZY);
        pesquisa.setWidth("280px");

        Button limpar = new Button("Limpar", VaadinIcon.CLOSE_SMALL.create(), e -> {
            filtroProfessor.clear();
            filtroTurma.clear();
            filtroSala.clear();
            pesquisa.clear();
        });
        limpar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        filtroProfessor.addValueChangeListener(e -> render());
        filtroTurma.addValueChangeListener(e -> render());
        filtroSala.addValueChangeListener(e -> render());
        pesquisa.addValueChangeListener(e -> render());

        HorizontalLayout filtros = new HorizontalLayout(pesquisa, filtroProfessor, filtroTurma, filtroSala, limpar);
        filtros.setAlignItems(Alignment.END);
        filtros.getStyle().set("flex-wrap", "wrap");

        resumo.getStyle().set("color", "#64748b").set("font-size", "0.85rem");

        board.setWidthFull();
        board.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        board.getStyle().set("gap", "14px").set("align-items", "flex-start");

        add(titulo, filtros, resumo, board);
        render();
    }

    private void render() {
        board.removeAll();

        Long profId = filtroProfessor.getValue() != null ? filtroProfessor.getValue().getId() : null;
        Long turmaId = filtroTurma.getValue() != null ? filtroTurma.getValue().getId() : null;
        Long salaId = filtroSala.getValue() != null ? filtroSala.getValue().getId() : null;
        String q = normalizar(pesquisa.getValue());

        List<Aula> filtradas = todasAulas.stream().filter(a -> {
            if (turmaId != null && !turmaId.equals(a.getTurma().getId())) return false;
            if (salaId != null && (a.getSala() == null || !salaId.equals(a.getSala().getId()))) return false;
            if (profId != null && a.getTurma().getTodosProfessores().stream()
                    .noneMatch(p -> profId.equals(p.getId()))) return false;
            if (!q.isBlank()) {
                String alvo = normalizar(String.join(" ",
                        nvl(a.getTurma().getDescricao()),
                        a.getTurma().getModalidade() != null ? nvl(a.getTurma().getModalidade().getDescricao()) : "",
                        a.getSala() != null ? nvl(a.getSala().getNome()) : "",
                        a.getTurma().getTodosProfessores().stream().map(p -> nvl(p.getNome()))
                                .collect(Collectors.joining(" "))));
                if (!alvo.contains(q)) return false;
            }
            return true;
        }).collect(Collectors.toList());

        long nTurmas = filtradas.stream().map(Aula::getTurma).distinct().count();
        resumo.setText(filtradas.size() + " aula(s) por semana · " + nTurmas + " turma(s)");

        for (DayOfWeek dia : DIAS) {
            List<Aula> doDia = filtradas.stream()
                    .filter(a -> a.getDia() == dia)
                    .sorted(Comparator.comparing(Aula::getHoraInicio,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
            boolean comFiltro = profId != null || turmaId != null || salaId != null || !q.isBlank();
            if (doDia.isEmpty() && comFiltro) {
                continue; // com filtro ativo, esconde dias vazios
            }
            board.add(criarColunaDia(dia, doDia));
        }
        if (board.getComponentCount() == 0) {
            board.add(new Span("Sem aulas para os filtros escolhidos."));
        }
    }

    private Component criarColunaDia(DayOfWeek dia, List<Aula> aulas) {
        VerticalLayout coluna = new VerticalLayout();
        coluna.setPadding(false);
        coluna.setSpacing(false);
        coluna.getStyle().set("min-width", "230px").set("flex", "1").set("max-width", "320px")
                .set("background", "#f8fafc").set("border", "1px solid #e2e8f0").set("border-radius", "12px")
                .set("padding", "10px");

        Span cab = new Span(nomeDia(dia) + "  ·  " + aulas.size());
        cab.getStyle().set("font-weight", "800").set("text-transform", "uppercase").set("font-size", "0.8rem")
                .set("color", "#334155").set("letter-spacing", "0.04em").set("margin-bottom", "8px")
                .set("display", "block");
        coluna.add(cab);

        if (aulas.isEmpty()) {
            Span vazio = new Span("—");
            vazio.getStyle().set("color", "#cbd5e1");
            coluna.add(vazio);
        }
        aulas.forEach(a -> coluna.add(criarCard(a)));
        return coluna;
    }

    private Component criarCard(Aula a) {
        Turma t = a.getTurma();
        String cor = t.getCor() != null && !t.getCor().isBlank() ? t.getCor()
                : (a.getSala() != null && a.getSala().getCor() != null && !a.getSala().getCor().isBlank()
                        ? a.getSala().getCor() : "#94a3b8");

        VerticalLayout card = new VerticalLayout();
        card.setPadding(false);
        card.setSpacing(false);
        card.getStyle().set("background", "white").set("border-radius", "10px")
                .set("border-left", "4px solid " + cor).set("box-shadow", "0 1px 3px rgba(0,0,0,0.08)")
                .set("padding", "8px 10px").set("margin-bottom", "8px").set("cursor", "pointer");

        Span hora = new Span(a.getHoraInicio().format(HORA)
                + (a.getHoraFim() != null ? "–" + a.getHoraFim().format(HORA) : ""));
        hora.getStyle().set("font-weight", "700").set("font-size", "0.9rem").set("color", "#0f172a");

        Span nomeTurma = new Span(t.getDescricao());
        nomeTurma.getStyle().set("font-size", "0.85rem").set("display", "block");

        String subtitulo = (t.getModalidade() != null ? nvl(t.getModalidade().getDescricao()) + " · " : "")
                + t.getTodosProfessores().stream().map(p -> primeiroNome(p.getNome()))
                        .filter(s -> !s.isBlank()).collect(Collectors.joining(", "));
        Span sub = new Span(subtitulo);
        sub.getStyle().set("font-size", "0.75rem").set("color", "#64748b").set("display", "block");

        card.add(hora, nomeTurma, sub);
        if (a.getSala() != null && a.getSala().getNome() != null) {
            Span salaSpan = new Span("📍 " + a.getSala().getNome());
            salaSpan.getStyle().set("font-size", "0.72rem").set("color", "#94a3b8");
            card.add(salaSpan);
        }

        card.addClickListener(e -> abrirDetalhe(a));
        return card;
    }

    private void abrirDetalhe(Aula a) {
        Turma t = a.getTurma();
        Dialog d = new Dialog();
        d.setHeaderTitle(t.getDescricao());
        VerticalLayout c = new VerticalLayout();
        c.setPadding(false);
        c.add(linha("Dia", nomeDia(a.getDia())));
        c.add(linha("Hora", a.getHoraInicio().format(HORA)
                + (a.getHoraFim() != null ? " – " + a.getHoraFim().format(HORA) : "")));
        if (t.getModalidade() != null) c.add(linha("Modalidade", nvl(t.getModalidade().getDescricao())));
        c.add(linha("Professor(es)", t.getTodosProfessores().stream().map(p -> nvl(p.getNome()))
                .filter(s -> !s.isBlank()).collect(Collectors.joining(", "))));
        if (a.getSala() != null) c.add(linha("Sala", nvl(a.getSala().getNome())));
        if (a.getTipo() != null && !a.getTipo().isBlank()) c.add(linha("Tipo", a.getTipo()));
        d.add(c);
        d.getFooter().add(new Button("Fechar", e -> d.close()));
        d.open();
    }

    private Span linha(String rotulo, String valor) {
        Span s = new Span();
        s.getElement().setProperty("innerHTML",
                "<strong>" + rotulo + ":</strong> " + (valor == null || valor.isBlank() ? "—" : escapar(valor)));
        s.getStyle().set("display", "block").set("padding", "3px 0").set("font-size", "0.9rem");
        return s;
    }

    private static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) return "";
        return nome.trim().split("\\s+")[0];
    }

    private String nomeDia(DayOfWeek d) {
        if (d == null) return "—";
        return switch (d) {
            case MONDAY -> "Segunda";
            case TUESDAY -> "Terça";
            case WEDNESDAY -> "Quarta";
            case THURSDAY -> "Quinta";
            case FRIDAY -> "Sexta";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }

    private String normalizar(String t) {
        return t == null ? ""
                : Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
