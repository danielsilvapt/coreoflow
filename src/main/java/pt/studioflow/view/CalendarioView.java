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
        titulo.getStyle().set("margin-top", "0");

        add(titulo, criarNavegacao(), criarCabecalhoDias(), gridCalendario);
        expand(gridCalendario);

        renderizarCalendario();
    }

    private void injectStyles() {
        String css =
            ".cal-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 4px; padding: 8px 16px; }" +
            ".cal-day { background: white; border-radius: 10px; border: 1px solid #e0e0e0; min-height: 90px; padding: 6px; overflow: hidden; }" +
            ".cal-day.today { border: 2px solid #1976d2; background: #e3f2fd; }" +
            ".cal-day.out-of-month { background: #f5f5f5; opacity: 0.5; }" +
            ".cal-day-number { font-weight: 700; font-size: 0.88rem; color: #333; margin-bottom: 3px; display: block; }" +
            ".cal-day.today .cal-day-number { color: #1976d2; }" +
            ".cal-chip { border-radius: 5px; padding: 2px 5px; font-size: 0.68rem; font-weight: 600; margin-bottom: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%; display: block; }" +
            ".chip-aula { background: #e8f5e9; color: #2e7d32; border: 1px solid #c8e6c9; }" +
            ".chip-marcacao { background: #fff3e0; color: #e65100; border: 1px solid #ffe0b2; }" +
            ".chip-ensaio { background: #ede7f6; color: #4527a0; border: 1px solid #d1c4e9; }" +
            ".cal-header-day { text-align: center; font-weight: 700; font-size: 0.78rem; color: #666; padding: 6px 0; text-transform: uppercase; letter-spacing: 0.5px; }";

        getElement().executeJs(
            "const s = document.createElement('style'); s.textContent = $0; document.head.appendChild(s);", css);
    }

    private Component criarNavegacao() {
        HorizontalLayout nav = new HorizontalLayout();
        nav.setAlignItems(Alignment.CENTER);
        nav.getStyle()
            .set("padding", "10px 16px")
            .set("background", "white")
            .set("border-bottom", "1px solid #eee");
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

        tituloMes.getStyle()
            .set("font-weight", "700")
            .set("font-size", "1.1rem")
            .set("min-width", "200px")
            .set("text-align", "center")
            .set("color", "#1a237e");

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
        chip.getStyle().set("display", "inline-block").set("margin-left", "6px");
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
