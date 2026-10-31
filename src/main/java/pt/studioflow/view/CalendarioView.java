package pt.studioflow.view;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aula;
import pt.studioflow.model.MarcacaoSala;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AulaRepository;
import pt.studioflow.repository.MarcacaoSalaRepository;
import pt.studioflow.repository.TurmaRepository;

// FICHEIRO A APAGAR — apaga manualmente e faz git rm CalendarioView.java
@PageTitle("Calendário | CoreoFlow")
// @Route removida — view desativada
@RolesAllowed({ "ADMIN", "PROF" })
public class CalendarioView extends VerticalLayout {

    private final AulaRepository aulaRepository;
    private final MarcacaoSalaRepository marcacaoSalaRepository;
    private final TurmaRepository turmaRepository;

    private YearMonth mesAtual = YearMonth.now();
    private final Span tituloMes = new Span();
    private final Div gridCalendario = new Div();

    public CalendarioView(AulaRepository aulaRepository,
            MarcacaoSalaRepository marcacaoSalaRepository,
            TurmaRepository turmaRepository) {
        this.aulaRepository = aulaRepository;
        this.marcacaoSalaRepository = marcacaoSalaRepository;
        this.turmaRepository = turmaRepository;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        injectStyles();

        H2 titulo = new H2("Calendário");
        Div cabecalhoPagina = new Div(titulo);
        cabecalhoPagina.addClassNames("cf-page-header", "cf-page-header-padded");

        add(cabecalhoPagina, criarNavegacao(), criarCabecalhoDias(), gridCalendario);
        expand(gridCalendario);

        renderizarCalendario();
    }

    private void injectStyles() {
        // .cal-* e .chip-* estão no tema coreoflow (com variantes para modo escuro).
    }

    private Component criarNavegacao() {
        HorizontalLayout nav = new HorizontalLayout();
        nav.setAlignItems(Alignment.CENTER);
        nav.addClassNames("cf-cal-nav", "cf-wrap");
        nav.setWidthFull();

        Button ant = new Button(VaadinIcon.CHEVRON_LEFT.create(), e -> {
            mesAtual = mesAtual.minusMonths(1);
            renderizarCalendario();
        });
        ant.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button prox = new Button(VaadinIcon.CHEVRON_RIGHT.create(), e -> {
            mesAtual = mesAtual.plusMonths(1);
            renderizarCalendario();
        });
        prox.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button hoje = new Button("Hoje", e -> {
            mesAtual = YearMonth.now();
            renderizarCalendario();
        });
        hoje.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        tituloMes.addClassName("cf-cal-mes");

        Div spacer = new Div();
        spacer.getStyle().set("flex", "1");

        Div chipAula = criarChipLegenda("Aula Regular", "chip-aula");
        Div chipEnsaio = criarChipLegenda("Ensaio", "chip-ensaio");
        Div chipMarcacao = criarChipLegenda("Marcação Sala", "chip-marcacao");

        nav.add(ant, tituloMes, prox, hoje, spacer, chipAula, chipEnsaio, chipMarcacao);
        return nav;
    }

    private Div criarChipLegenda(String label, String cssClass) {
        Div chip = new Div();
        chip.addClassName("cal-chip");
        chip.addClassName(cssClass);
        chip.setText(label);
        chip.addClassName("cf-cal-legenda");
        return chip;
    }

    private Component criarCabecalhoDias() {
        Div cabecalho = new Div();
        cabecalho.addClassName("cal-grid");
        cabecalho.getStyle().set("padding-bottom", "0").set("padding-top", "8px");

        String[] diasSemana = { "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom" };
        for (String dia : diasSemana) {
            Div cell = new Div();
            cell.addClassName("cal-header-day");
            cell.setText(dia);
            cabecalho.add(cell);
        }
        return cabecalho;
    }

    private void renderizarCalendario() {
        tituloMes.setText(
            mesAtual.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "PT")).toUpperCase()
            + " " + mesAtual.getYear()
        );

        gridCalendario.removeAll();
        gridCalendario.addClassName("cal-grid");
        gridCalendario.getStyle().set("padding-top", "4px").set("align-content", "start");

        Studio studio = TenantContext.getCurrentStudio();
        List<Aula> todasAulas = studio != null
                ? aulaRepository.findByTurmaStudio(studio)
                : aulaRepository.findAll();

        LocalDate inicioMes = mesAtual.atDay(1);
        LocalDate fimMes = mesAtual.atEndOfMonth();
        List<MarcacaoSala> marcacoes = studio != null
                ? marcacaoSalaRepository.findByStudioAndDataBetween(studio, inicioMes, fimMes)
                : marcacaoSalaRepository.findByDataBetween(inicioMes, fimMes);

        // Primeiro dia a mostrar (segunda-feira da semana do dia 1)
        LocalDate primeiroDia = inicioMes;
        while (primeiroDia.getDayOfWeek() != DayOfWeek.MONDAY) {
            primeiroDia = primeiroDia.minusDays(1);
        }

        // 6 semanas = 42 células
        LocalDate dia = primeiroDia;
        for (int i = 0; i < 42; i++) {
            final LocalDate diaAtual = dia;

            Div cell = new Div();
            cell.addClassName("cal-day");

            if (!diaAtual.getMonth().equals(mesAtual.getMonth())) {
                cell.addClassName("out-of-month");
            }
            if (diaAtual.equals(LocalDate.now())) {
                cell.addClassName("today");
            }

            Span numDia = new Span(String.valueOf(diaAtual.getDayOfMonth()));
            numDia.addClassName("cal-day-number");
            cell.add(numDia);

            // Aulas regulares (por dia da semana)
            DayOfWeek dow = diaAtual.getDayOfWeek();
            List<Aula> aulasHoje = todasAulas.stream()
                .filter(a -> a.getDia() != null && a.getDia().equals(dow))
                .collect(Collectors.toList());

            for (Aula aula : aulasHoje) {
                if (aula.getTurma() == null) continue;

                Div chip = new Div();
                String tipo = aula.getTipo();
                chip.addClassName("cal-chip");
                if ("ENSAIO".equalsIgnoreCase(tipo)) {
                    chip.addClassName("chip-ensaio");
                } else {
                    chip.addClassName("chip-aula");
                }

                String hora = aula.getHoraInicio() != null
                    ? aula.getHoraInicio().toString().substring(0, 5) + " "
                    : "";
                chip.setText(hora + aula.getTurma().getDescricao());
                chip.getElement().setAttribute("title",
                    aula.getTurma().getDescricao()
                    + (aula.getHoraInicio() != null
                        ? " — " + aula.getHoraInicio() + " às " + aula.getHoraFim()
                        : ""));
                cell.add(chip);
            }

            // Marcações de sala para este dia
            List<MarcacaoSala> marcacoesHoje = marcacoes.stream()
                .filter(m -> m.getData() != null && m.getData().equals(diaAtual))
                .collect(Collectors.toList());

            for (MarcacaoSala m : marcacoesHoje) {
                Div chip = new Div();
                chip.addClassName("cal-chip");
                chip.addClassName("chip-marcacao");

                String horaM = m.getHoraInicio() != null
                    ? m.getHoraInicio().toString().substring(0, 5) + " "
                    : "";
                String desc = m.getProfessor() != null ? m.getProfessor() : "Marcação";
                chip.setText(horaM + desc);
                chip.getElement().setAttribute("title",
                    desc + (m.getHoraInicio() != null
                        ? " — " + m.getHoraInicio() + " às " + m.getHoraFim()
                        : ""));
                cell.add(chip);
            }

            gridCalendario.add(cell);
            dia = dia.plusDays(1);
        }
    }
}
