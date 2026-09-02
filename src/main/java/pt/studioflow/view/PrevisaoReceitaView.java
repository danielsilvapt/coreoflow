package pt.studioflow.view;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.AulaRepository;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.RegistoHorasRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.SubsidioAlunoRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.RemuneracaoService;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "previsao-receita", layout = MainLayout.class)
@PageTitle("Previsão de Receita | CoreoFlow")
@RolesAllowed({"ADMIN", "SUPERADMIN"})
public class PrevisaoReceitaView extends VerticalLayout {

    record LinhaProjecao(String periodo, long alunosAtivos, double receitaBase,
                         double descontosSubsidios, double custoProfessores,
                         double receitaLiquida, String tendencia) {}

    private final MensalidadeRepository mensalidadeRepo;
    private final AlunoRepository alunoRepo;
    private final StudioRepository studioRepo;
    private final SubsidioAlunoRepository subsidioRepo;
    private final TurmaRepository turmaRepo;
    private final RegistoHorasRepository registoHorasRepo;
    private final AlunoTurmaRepository alunoTurmaRepo;
    private final AulaRepository aulaRepo;
    private final RemuneracaoService remuneracaoService;

    public PrevisaoReceitaView(MensalidadeRepository mensalidadeRepo,
                                AlunoRepository alunoRepo,
                                StudioRepository studioRepo,
                                SubsidioAlunoRepository subsidioRepo,
                                TurmaRepository turmaRepo,
                                RegistoHorasRepository registoHorasRepo,
                                AlunoTurmaRepository alunoTurmaRepo,
                                AulaRepository aulaRepo,
                                RemuneracaoService remuneracaoService) {
        this.mensalidadeRepo = mensalidadeRepo;
        this.alunoRepo = alunoRepo;
        this.studioRepo = studioRepo;
        this.subsidioRepo = subsidioRepo;
        this.turmaRepo = turmaRepo;
        this.registoHorasRepo = registoHorasRepo;
        this.alunoTurmaRepo = alunoTurmaRepo;
        this.aulaRepo = aulaRepo;
        this.remuneracaoService = remuneracaoService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        Studio studio = TenantContext.getCurrentStudio();
        boolean isSA = studio == null;

        H2 titulo = new H2("Previsão de Receita");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        // Para SA: selector de estúdio
        if (isSA) {
            ComboBox<Studio> studioCombo = new ComboBox<>("Estúdio");
            studioCombo.setItems(studioRepo.findAll().stream().filter(Studio::isAtivo).toList());
            studioCombo.setItemLabelGenerator(Studio::getNome);
            studioCombo.setWidth("280px");
            studioCombo.addValueChangeListener(e -> {
                removeAll();
                add(titulo);
                add(studioCombo);
                if (e.getValue() != null)
                    add(criarDashboard(e.getValue()));
            });
            add(studioCombo);
            return;
        }

        add(criarDashboard(studio));
    }

    private VerticalLayout criarDashboard(Studio studio) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);

        List<Mensalidade> todas = mensalidadeRepo.findAllByStudio(studio);
        List<Aluno> alunos = alunoRepo.findAllByStudio(studio);
        long alunosAtivos = alunos.stream().filter(a -> a.getStatus() == Aluno.AlunoStatus.ATIVO).count();

        List<pt.studioflow.model.Turma> turmas = turmaRepo.findAllByStudio(studio);
        RemuneracaoService.Dados dadosRemun = new RemuneracaoService.Dados()
                .mensalidades(todas)
                .registos(registoHorasRepo.findAllByStudio(studio))
                .aulas(aulaRepo.findByTurmaStudio(studio))
                .inscricoes(alunoTurmaRepo.findAll().stream()
                        .filter(at -> at.getTurma() != null && at.getTurma().getStudio() != null
                                && at.getTurma().getStudio().getId().equals(studio.getId()))
                        .toList());

        // Receita real dos últimos 6 meses
        double receitaHistorica = todas.stream()
                .filter(m -> m.getEstado() == EstadoMensalidade.PAGO)
                .filter(m -> LocalDate.of(m.getAno(), m.getMes(), 1)
                        .isAfter(LocalDate.now().minusMonths(6)))
                .mapToDouble(Mensalidade::getValor).sum();

        double mediaHistorica = receitaHistorica / 6.0;

        // Receita prevista nos próximos 6 meses (baseada na média)
        double descontoSubsidios = subsidioRepo.findByStudioOrderByDataRenovacaoAsc(studio).stream()
                .filter(s -> s.isAtivo())
                .mapToDouble(s -> mediaHistorica * (s.getPercentagem() / 100.0) / alunosAtivos)
                .sum();

        // Cards de resumo
        HorizontalLayout cards = new HorizontalLayout(
                cardMetrica("Alunos Ativos", String.valueOf(alunosAtivos), "#1976D2"),
                cardMetrica("Média Mensal (6m)", String.format("%.0f €", mediaHistorica), "#27AE60"),
                cardMetrica("Receita Anual Prev.", String.format("%.0f €", mediaHistorica * 12), "#E67E22"),
                cardMetrica("Descontos/Subsídios", String.format("%.0f €/mês", descontoSubsidios), "#7B1FA2")
        );
        cards.setWidthFull();
        cards.getStyle().set("flex-wrap", "wrap").set("gap", "12px").set("margin-bottom", "16px");

        // Tabela de projeção mês a mês
        List<LinhaProjecao> linhas = new ArrayList<>();
        LocalDate hoje = LocalDate.now();
        for (int i = -3; i <= 6; i++) {
            LocalDate mes = hoje.plusMonths(i);
            String periodo = mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt"))
                    + " " + mes.getYear();

            // Real: buscar do histórico
            final int anoFinal = mes.getYear();
            final Month mesFinal = mes.getMonth();
            double receitaReal = todas.stream()
                    .filter(m -> m.getAno() == anoFinal && m.getMes() == mesFinal
                            && m.getEstado() == EstadoMensalidade.PAGO)
                    .mapToDouble(Mensalidade::getValor).sum();

            boolean futuro = mes.isAfter(hoje);
            double base = futuro ? mediaHistorica : receitaReal;

            java.time.YearMonth ym = java.time.YearMonth.from(mes);
            double custoProfs = remuneracaoService.rentabilidadePorTurma(turmas, ym, dadosRemun)
                    .values().stream().mapToDouble(x -> x[1]).sum();

            double liquida = base - (futuro ? descontoSubsidios : 0) - custoProfs;
            String tendencia = futuro ? "📈 Previsão" : (receitaReal >= mediaHistorica * 0.9 ? "✅" : "⚠️");

            linhas.add(new LinhaProjecao(periodo, alunosAtivos, base, futuro ? descontoSubsidios : 0,
                    custoProfs, liquida, tendencia));
        }

        Grid<LinhaProjecao> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setHeight("400px");

        grid.addColumn(LinhaProjecao::periodo).setHeader("Período").setFlexGrow(1);
        grid.addColumn(l -> l.alunosAtivos() + " alunos").setHeader("Alunos").setAutoWidth(true);
        grid.addColumn(l -> String.format("%.2f €", l.receitaBase())).setHeader("Receita Base").setAutoWidth(true);
        grid.addColumn(l -> l.descontosSubsidios() > 0
                ? String.format("-%.2f €", l.descontosSubsidios()) : "—")
                .setHeader("Descontos").setAutoWidth(true);
        grid.addColumn(l -> l.custoProfessores() > 0
                ? String.format("-%.2f €", l.custoProfessores()) : "—")
                .setHeader("Custo Professores").setAutoWidth(true);
        grid.addComponentColumn(l -> {
            Span s = new Span(String.format("%.2f €", l.receitaLiquida()));
            s.getStyle().set("font-weight", "700")
                    .set("color", l.receitaLiquida() >= 0 ? "#27AE60" : "#E74C3C");
            return s;
        }).setHeader("Líquido após Profs").setAutoWidth(true);
        grid.addColumn(LinhaProjecao::tendencia).setHeader("Estado").setAutoWidth(true);

        grid.setItems(linhas);

        layout.add(cards, new H3("Projeção por Mês (últimos 3 + próximos 6)"), grid);
        return layout;
    }

    private VerticalLayout cardMetrica(String label, String valor, String cor) {
        Span v = new Span(valor);
        v.getStyle().set("font-size", "22px").set("font-weight", "700").set("color", cor);
        Span l = new Span(label);
        l.getStyle().set("font-size", "11px").set("color", "#888").set("text-transform", "uppercase");
        VerticalLayout card = new VerticalLayout(v, l);
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.setSpacing(false);
        card.setPadding(true);
        card.getStyle()
                .set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)").set("flex", "1")
                .set("min-width", "140px");
        return card;
    }
}
