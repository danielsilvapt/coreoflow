package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.PresencaRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.TurmaRepository;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Route(value = "admin/saude", layout = MainLayout.class)
@PageTitle("Painel de Saúde | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class PainelSaudeView extends VerticalLayout {

    private final StudioRepository studioRepository;
    private final AlunoRepository alunoRepository;
    private final MensalidadeRepository mensalidadeRepository;
    private final TurmaRepository turmaRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final PresencaRepository presencaRepository;
    private final JdbcTemplate jdbc;

    public PainelSaudeView(StudioRepository studioRepository,
                           AlunoRepository alunoRepository,
                           MensalidadeRepository mensalidadeRepository,
                           TurmaRepository turmaRepository,
                           AlunoTurmaRepository alunoTurmaRepository,
                           PresencaRepository presencaRepository,
                           JdbcTemplate jdbc) {
        this.studioRepository = studioRepository;
        this.alunoRepository = alunoRepository;
        this.mensalidadeRepository = mensalidadeRepository;
        this.turmaRepository = turmaRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.presencaRepository = presencaRepository;
        this.jdbc = jdbc;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        render();
    }

    private void render() {
        removeAll();

        H2 titulo = new H2("Painel de Saúde da Plataforma");
        titulo.getStyle().set("margin-top", "0");
        Button atualizar = new Button("Atualizar", VaadinIcon.REFRESH.create(), e -> render());
        atualizar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout topo = new HorizontalLayout(titulo, atualizar);
        topo.setAlignItems(FlexComponent.Alignment.CENTER);
        topo.setWidthFull();
        topo.expand(titulo);
        add(topo);

        add(criarCardServidor());
        add(criarCardBaseDados());

        add(new H3("Por estúdio"));
        List<Studio> studios = studioRepository.findAll().stream().filter(Studio::isAtivo).toList();
        for (Studio studio : studios) {
            add(criarCardEstudio(studio));
        }
        if (studios.isEmpty()) {
            Span vazio = new Span("Nenhum estúdio ativo.");
            vazio.getStyle().set("color", "#888");
            add(vazio);
        }
    }

    // =========================================================
    //  ESTADO DO SERVIDOR
    // =========================================================
    private VerticalLayout criarCardServidor() {
        Runtime rt = Runtime.getRuntime();
        long maxHeap = rt.maxMemory();
        long usedHeap = rt.totalMemory() - rt.freeMemory();
        double heapPct = maxHeap > 0 ? (usedHeap * 100.0 / maxHeap) : 0;

        java.io.File disco = new java.io.File(".");
        long discoTotal = disco.getTotalSpace();
        long discoUsavel = disco.getUsableSpace();
        double discoPct = discoTotal > 0 ? ((discoTotal - discoUsavel) * 100.0 / discoTotal) : 0;

        double procCpu = -1, sysCpu = -1;
        long ramTotal = 0, ramLivre = 0;
        try {
            com.sun.management.OperatingSystemMXBean os =
                    (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            procCpu = os.getProcessCpuLoad() * 100;
            sysCpu = os.getCpuLoad() * 100;
            ramTotal = os.getTotalMemorySize();
            ramLivre = os.getFreeMemorySize();
        } catch (Throwable ignore) {
            // métricas do SO podem não estar disponíveis em todos os JDK
        }
        double ramPct = ramTotal > 0 ? ((ramTotal - ramLivre) * 100.0 / ramTotal) : 0;

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        int cores = rt.availableProcessors();

        VerticalLayout card = cardBase("#3498DB");
        card.add(cabecalhoCard("🖥️  Estado do Servidor",
                "Ligado há " + formatarDuracao(uptimeMs) + "  ·  " + cores + " CPUs  ·  " + threads + " threads"));

        VerticalLayout barras = new VerticalLayout(
                barraMetrica("Memória da aplicação (heap JVM)",
                        formatarBytes(usedHeap) + " / " + formatarBytes(maxHeap), heapPct),
                barraMetrica("Memória do servidor (RAM)",
                        ramTotal > 0 ? formatarBytes(ramTotal - ramLivre) + " / " + formatarBytes(ramTotal) : "n/d",
                        ramPct),
                barraMetrica("Disco",
                        formatarBytes(discoTotal - discoUsavel) + " / " + formatarBytes(discoTotal), discoPct),
                barraMetrica("CPU do processo", procCpu >= 0 ? String.format("%.0f%%", procCpu) : "n/d",
                        Math.max(0, procCpu)),
                barraMetrica("CPU do sistema", sysCpu >= 0 ? String.format("%.0f%%", sysCpu) : "n/d",
                        Math.max(0, sysCpu)));
        barras.setPadding(false);
        barras.setSpacing(false);
        barras.getStyle().set("gap", "12px").set("margin-top", "10px");
        card.add(barras);
        return card;
    }

    // =========================================================
    //  BASE DE DADOS
    // =========================================================
    private VerticalLayout criarCardBaseDados() {
        VerticalLayout card = cardBase("#9B59B6");

        Double totalMb = null;
        List<TabelaBD> tabelas = List.of();
        try {
            totalMb = jdbc.queryForObject(
                    "SELECT ROUND(SUM(data_length + index_length) / 1024 / 1024, 1) "
                            + "FROM information_schema.tables WHERE table_schema = DATABASE()",
                    Double.class);
            tabelas = jdbc.query(
                    "SELECT table_name, "
                            + "ROUND((data_length + index_length) / 1024 / 1024, 2) AS mb, "
                            + "COALESCE(table_rows, 0) AS linhas "
                            + "FROM information_schema.tables WHERE table_schema = DATABASE() "
                            + "ORDER BY (data_length + index_length) DESC LIMIT 10",
                    (rs, i) -> new TabelaBD(rs.getString("table_name"), rs.getDouble("mb"), rs.getLong("linhas")));
        } catch (Exception e) {
            card.add(cabecalhoCard("🗄️  Base de Dados", "Não foi possível ler o tamanho: " + e.getMessage()));
            return card;
        }

        long nAlunos = alunoRepository.count();
        long nStudios = studioRepository.count();
        long nMensalidades = mensalidadeRepository.count();
        long nTurmas = turmaRepository.count();
        long nInscricoes = alunoTurmaRepository.count();
        long nPresencas = presencaRepository.count();

        card.add(cabecalhoCard("🗄️  Base de Dados",
                (totalMb != null ? totalMb + " MB no total" : "tamanho n/d")
                        + "  ·  " + nStudios + " estúdios · " + nAlunos + " alunos · " + nMensalidades
                        + " mensalidades · " + nTurmas + " turmas · " + nInscricoes + " inscrições · "
                        + nPresencas + " presenças"));

        Grid<TabelaBD> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_NO_BORDER);
        grid.setItems(tabelas);
        grid.setAllRowsVisible(true);
        grid.addColumn(TabelaBD::nome).setHeader("Tabela").setFlexGrow(1);
        grid.addColumn(t -> String.format("%.2f MB", t.mb())).setHeader("Tamanho").setAutoWidth(true);
        grid.addColumn(t -> String.format("%,d", t.linhas())).setHeader("Linhas (aprox.)").setAutoWidth(true);
        grid.getStyle().set("margin-top", "10px");
        card.add(grid);
        return card;
    }

    private record TabelaBD(String nome, double mb, long linhas) {}

    // =========================================================
    //  ESTÚDIO (mantido)
    // =========================================================
    private VerticalLayout criarCardEstudio(Studio studio) {
        long alunosPendentes = alunoRepository.findAllByStudio(studio).stream()
                .filter(a -> a.getStatus() == Aluno.AlunoStatus.PENDENTE
                        || a.getStatus() == Aluno.AlunoStatus.EXPERIMENTAL)
                .count();
        long mensalidadesPorEmitir = mensalidadeRepository.findAllByStudio(studio).stream()
                .filter(m -> m.getEstado() == EstadoMensalidade.POR_EMITIR).count();
        long mensalidadesEmDivida = mensalidadeRepository.findAllByStudio(studio).stream()
                .filter(m -> m.getEstado() == EstadoMensalidade.EM_DIVIDA).count();
        long turmasSemProf = turmaRepository.findAllByStudio(studio).stream()
                .filter(t -> t.getProfessor() == null && t.isAtivo()).count();

        boolean critico = mensalidadesEmDivida > 5 || turmasSemProf > 0;
        boolean atencao = alunosPendentes > 0 || mensalidadesPorEmitir > 3;
        String semaforo = critico ? "#E74C3C" : atencao ? "#F39C12" : "#27AE60";
        String semaforoLabel = critico ? "Crítico" : atencao ? "Atenção" : "OK";

        VerticalLayout card = cardBase(semaforo);

        Span nomeStudio = new Span(studio.getNome());
        nomeStudio.getStyle().set("font-weight", "700").set("font-size", "16px");
        Span slug = new Span("@" + studio.getSlug());
        slug.getStyle().set("color", "#888").set("font-size", "12px").set("margin-left", "8px");
        Span badge = new Span(semaforoLabel);
        badge.getStyle().set("background", semaforo).set("color", "white")
                .set("padding", "2px 10px").set("border-radius", "12px")
                .set("font-size", "11px").set("font-weight", "700").set("margin-left", "auto");
        HorizontalLayout header = new HorizontalLayout(nomeStudio, slug, badge);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.getStyle().set("margin-bottom", "10px");

        HorizontalLayout metricas = new HorizontalLayout(
                criarMetrica("Pendentes", alunosPendentes, VaadinIcon.USER_CLOCK, "#E67E22", alunosPendentes > 0),
                criarMetrica("Por Emitir", mensalidadesPorEmitir, VaadinIcon.INVOICE, "#3498DB", mensalidadesPorEmitir > 0),
                criarMetrica("Em Dívida", mensalidadesEmDivida, VaadinIcon.WARNING, "#E74C3C", mensalidadesEmDivida > 0),
                criarMetrica("Turmas s/ Prof", turmasSemProf, VaadinIcon.ACADEMY_CAP, "#9B59B6", turmasSemProf > 0));
        metricas.setSpacing(true);
        metricas.getStyle().set("flex-wrap", "wrap");

        card.add(header, metricas);
        return card;
    }

    // =========================================================
    //  HELPERS
    // =========================================================
    private VerticalLayout cardBase(String cor) {
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.setWidthFull();
        card.getStyle()
                .set("background", "white")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.07)")
                .set("border-left", "4px solid " + cor)
                .set("margin-bottom", "8px");
        return card;
    }

    private VerticalLayout cabecalhoCard(String titulo, String subtitulo) {
        Span t = new Span(titulo);
        t.getStyle().set("font-weight", "700").set("font-size", "16px");
        Span s = new Span(subtitulo);
        s.getStyle().set("color", "#888").set("font-size", "12px");
        VerticalLayout v = new VerticalLayout(t, s);
        v.setPadding(false);
        v.setSpacing(false);
        return v;
    }

    private HorizontalLayout barraMetrica(String label, String valor, double pct) {
        pct = Math.max(0, Math.min(100, pct));
        String cor = pct >= 90 ? "#E74C3C" : pct >= 75 ? "#F39C12" : "#27AE60";

        Span l = new Span(label);
        l.getStyle().set("font-size", "13px").set("min-width", "230px");
        Span v = new Span(valor + "   (" + String.format("%.0f%%", pct) + ")");
        v.getStyle().set("font-size", "12px").set("color", "#666").set("min-width", "150px")
                .set("text-align", "right");

        ProgressBar bar = new ProgressBar();
        bar.setValue(pct / 100.0);
        bar.setWidth("100%");
        bar.getStyle().set("--lumo-primary-color", cor);

        HorizontalLayout row = new HorizontalLayout(l, bar, v);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.expand(bar);
        row.getStyle().set("flex-wrap", "wrap");
        return row;
    }

    private VerticalLayout criarMetrica(String label, long valor, VaadinIcon icone, String cor, boolean alerta) {
        Icon icon = icone.create();
        icon.setSize("16px");
        icon.setColor(alerta ? cor : "#bbb");
        Span valorSpan = new Span(String.valueOf(valor));
        valorSpan.getStyle().set("font-size", "22px").set("font-weight", "700")
                .set("color", alerta ? cor : "#ccc");
        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("font-size", "11px").set("color", "#888");
        VerticalLayout box = new VerticalLayout(icon, valorSpan, labelSpan);
        box.setAlignItems(FlexComponent.Alignment.CENTER);
        box.setSpacing(false);
        box.setPadding(true);
        box.getStyle()
                .set("min-width", "110px")
                .set("background", alerta ? "#fff8f0" : "#fafafa")
                .set("border-radius", "10px")
                .set("border", "1px solid " + (alerta ? cor + "44" : "#eee"));
        return box;
    }

    private static String formatarBytes(long b) {
        if (b <= 0) return "0 B";
        String[] u = {"B", "KB", "MB", "GB", "TB"};
        int i = (int) (Math.log(b) / Math.log(1024));
        i = Math.min(i, u.length - 1);
        return String.format("%.1f %s", b / Math.pow(1024, i), u[i]);
    }

    private static String formatarDuracao(long ms) {
        Duration d = Duration.ofMillis(ms);
        long dias = d.toDays();
        long h = d.toHoursPart();
        long m = d.toMinutesPart();
        if (dias > 0) return dias + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}
