package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.context.SecurityContextHolder;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;

import com.vaadin.flow.component.grid.GridVariant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Route(value = "dashboard", layout = MainLayout.class)
@UIScope
@RolesAllowed({ "ADMIN", "PROF", "DELEG", "ALUNO" })
public class DashboardView extends Div {

        private final AlunoRepository alunoRepository;
        private final TurmaRepository turmaRepository;
        private final AlunoTurmaRepository alunoTurmaRepository;
        private final MensalidadeRepository mensalidadeRepository;
        private final PresencaRepository presencaRepository;
        private final AulaRepository aulaRepository;
        private final UserRepository userRepository;
        private final ProfessorRepository professorRepository;
        private final RegistoHorasRepository registoHorasRepository;
        private final MarcacaoSalaRepository marcacaoSalaRepository;
        private final pt.studioflow.service.DashboardService dashboardService;
        private final pt.studioflow.service.RemuneracaoService remuneracaoService;

        public DashboardView(
                        AlunoRepository alunoRepository,
                        TurmaRepository turmaRepository,
                        AlunoTurmaRepository alunoTurmaRepository,
                        MensalidadeRepository mensalidadeRepository,
                        PresencaRepository presencaRepository,
                        AulaRepository aulaRepository,
                        UserRepository userRepository,
                        ProfessorRepository professorRepository,
                        RegistoHorasRepository registoHorasRepository,
                        MarcacaoSalaRepository marcacaoSalaRepository,
                        pt.studioflow.service.DashboardService dashboardService,
                        pt.studioflow.service.RemuneracaoService remuneracaoService) {

                this.alunoRepository = alunoRepository;
                this.turmaRepository = turmaRepository;
                this.alunoTurmaRepository = alunoTurmaRepository;
                this.mensalidadeRepository = mensalidadeRepository;
                this.presencaRepository = presencaRepository;
                this.aulaRepository = aulaRepository;
                this.userRepository = userRepository;
                this.professorRepository = professorRepository;
                this.registoHorasRepository = registoHorasRepository;
                this.marcacaoSalaRepository = marcacaoSalaRepository;
                this.dashboardService = dashboardService;
                this.remuneracaoService = remuneracaoService;

                boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                boolean isProf = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_PROF"));
                boolean isAluno = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ALUNO"));

                setSizeFull();
                getStyle().set("padding", "25px").set("background-color", "#f0f2f5");
                getStyle().set("display", "flex").set("flex-direction", "column");

                FlexLayout gridLayout = new FlexLayout();
                gridLayout.setWidthFull();
                gridLayout.getStyle().set("display", "grid");
                gridLayout.getStyle().set("grid-template-columns", "repeat(auto-fill, minmax(350px, 1fr))");
                gridLayout.getStyle().set("grid-auto-rows", "auto");
                gridLayout.getStyle().set("gap", "30px");

                YearMonth mesAtual = YearMonth.now();
                LocalDate hoje = LocalDate.now();

                Studio studio = TenantContext.getCurrentStudio();

                List<Turma> turmas = studio != null
                        ? turmaRepository.findAllByStudio(studio)
                        : turmaRepository.findAllComplete();
                List<Aula> todasAulas = studio != null
                        ? aulaRepository.findByTurmaStudio(studio)
                        : aulaRepository.findAll();
                List<Mensalidade> todasMensalidades = studio != null
                        ? mensalidadeRepository.findAllByStudio(studio)
                        : mensalidadeRepository.findAll();
                List<Aluno> todosAlunos = studio != null
                        ? alunoRepository.findAllByStudio(studio)
                        : alunoRepository.findAll();
                List<Presenca> todasPresencas = studio != null
                        ? presencaRepository.findAllByAlunoStudio(studio)
                        : presencaRepository.findAll();
                List<MarcacaoSala> todasAsPrivadasDeHoje = studio != null
                        ? marcacaoSalaRepository.findAllWithAlunosByDataAndStudio(studio, hoje)
                        : marcacaoSalaRepository.findAllWithAlunosByData(hoje);

                String login = SecurityContextHolder.getContext().getAuthentication().getName();
                String firstNameBD = userRepository.findByPrincipalName(login).map(User::getFirstName).orElse("");
                String busca = normalizar(firstNameBD);

                // --- 1. ÁREA PESSOAL & PROFESSOR ---
                if (isProf || isAluno) {
                        VerticalLayout vPerso = new VerticalLayout();
                        vPerso.setPadding(false);
                        vPerso.setSpacing(true);
                        vPerso.add(new H3("Olá, " + firstNameBD + "!"));

                        Set<String> agenda = new TreeSet<>(Comparator.comparing(s -> s.split("\\|")[0]));

                        if (isProf) {
                                todasAulas.stream()
                                                .filter(a -> a.getDia() == hoje.getDayOfWeek())
                                                .filter(a -> a.getTurma() != null && a.getTurma().getProfessor() != null
                                                                && normalizar(a.getTurma().getProfessor().getNome())
                                                                                .contains(busca))
                                                .forEach(a -> agenda.add(a.getHoraInicio() + "|"
                                                                + a.getTurma().getDescricao() + "|#2e7d32"));

                                todasAsPrivadasDeHoje.stream()
                                                .filter(ms -> ms.getProfessor() != null
                                                                && normalizar(ms.getProfessor()).contains(busca))
                                                .forEach(ms -> {
                                                        String nomesAlunos = extrairNomesCurtos(ms.getAlunos());
                                                        agenda.add(ms.getHoraInicio() + "|Aula "
                                                                        + ms.getTipo().toLowerCase() + " ("
                                                                        + nomesAlunos + ")|#6d1b7b");
                                                });
                        }

                        if (agenda.isEmpty()) {
                                vPerso.add(new Span("Não tens aulas hoje."));
                        } else {
                                agenda.forEach(item -> {
                                        String[] d = item.split("\\|");
                                        vPerso.add(criarLinhaAgenda(d[0], d[1], d[2]));
                                });
                        }

                        Div cardPessoal = criarCard("Agenda do dia", VaadinIcon.CALENDAR_USER, vPerso);
                        cardPessoal.getStyle().set("grid-column", "1 / -1").set("background-color", "#e3f2fd");
                        gridLayout.add(cardPessoal);

                        if (isProf) {
                                // Pedidos Sala
                                List<MarcacaoSala> pendentes = (studio != null
                                                ? marcacaoSalaRepository.findByStudioAndStatus(studio, "PENDENTE")
                                                : marcacaoSalaRepository.findAll()).stream()
                                                .filter(m -> normalizar(m.getProfessor()).contains(busca))
                                                .collect(Collectors.toList());
                                VerticalLayout vP = new VerticalLayout();
                                vP.setPadding(false);
                                if (pendentes.isEmpty())
                                        vP.add(new Span("Sem pedidos pendentes."));
                                else
                                        pendentes.forEach(p -> vP.add(new Span(
                                                        p.getData() + " " + p.getHoraInicio() + " - " + p.getTipo())));
                                gridLayout.add(criarCard("Pedidos de Sala", VaadinIcon.CLOCK, vP));

                                // --- ATIVIDADE DO MÊS COM TOTAL EM DESTAQUE ---
                                VerticalLayout vA = new VerticalLayout();
                                vA.setPadding(false);

                                List<pt.studioflow.model.RegistoHoras> registosMes = studio != null
                                                ? registoHorasRepository.findAllByStudio(studio)
                                                : registoHorasRepository.findAll();
                                double hReg = registosMes.stream()
                                                .filter(r -> normalizar(r.getProfessor()).contains(busca)
                                                                && r.getMesNumero() == mesAtual.getMonthValue()
                                                                && !"ENSAIO".equalsIgnoreCase(r.getTipoAtividade()))
                                                .mapToDouble(r -> Duration.between(r.getInicio(), r.getFim())
                                                                .toMinutes() / 60.0)
                                                .sum();

                                double hEns = registosMes.stream()
                                                .filter(r -> normalizar(r.getProfessor()).contains(busca)
                                                                && r.getMesNumero() == mesAtual.getMonthValue()
                                                                && "ENSAIO".equalsIgnoreCase(r.getTipoAtividade()))
                                                .mapToDouble(r -> Duration.between(r.getInicio(), r.getFim())
                                                                .toMinutes() / 60.0)
                                                .sum();

                                List<MarcacaoSala> marcacoesMes = studio != null
                                                ? marcacaoSalaRepository.findAllByStudio(studio)
                                                : marcacaoSalaRepository.findAll();
                                double hPriv = marcacoesMes.stream()
                                                .filter(m -> normalizar(m.getProfessor()).contains(busca)
                                                                && m.getData().getMonthValue() == mesAtual
                                                                                .getMonthValue()
                                                                && "PRIVADA".equalsIgnoreCase(m.getTipo()))
                                                .mapToDouble(m -> Duration.between(m.getHoraInicio(), m.getHoraFim())
                                                                .toMinutes() / 60.0)
                                                .sum();

                                vA.add(criarLinhaTextoDestaque("Regulares", String.format("%.1f h", hReg), "#2e7d32"));
                                vA.add(criarLinhaTextoDestaque("Ensaios", String.format("%.1f h", hEns), "#1976d2"));
                                vA.add(criarLinhaTextoDestaque("Privadas", String.format("%.1f h", hPriv), "#6d1b7b"));

                                // Valor total acumulado com maior destaque
                                double totalGeral = hReg + hEns + hPriv;
                                Span totalSpan = new Span(String.format("%.1f", totalGeral));
                                totalSpan.getStyle()
                                                .set("font-size", "36px")
                                                .set("font-weight", "800")
                                                .set("color", "#2D3436")
                                                .set("margin-top", "15px")
                                                .set("display", "block");

                                Span labelTotal = new Span("HORAS TOTAIS NO MÊS");
                                labelTotal.getStyle().set("font-size", "10px").set("color", "#95a5a6")
                                                .set("font-weight", "bold");

                                VerticalLayout vTotal = new VerticalLayout(totalSpan, labelTotal);
                                vTotal.setAlignItems(FlexComponent.Alignment.CENTER);
                                vTotal.setPadding(false);
                                vTotal.setSpacing(false);
                                vTotal.getStyle().set("border-top", "1px solid #f0f0f0").set("margin-top", "10px");

                                vA.add(vTotal);

                                // --- Valor estimado a receber (conforme modo de remuneração efetivo) ---
                                Professor profLogado = (studio != null
                                                ? professorRepository.findAllByStudio(studio)
                                                : professorRepository.findAll()).stream()
                                                .filter(p -> normalizar(p.getNome()).contains(busca))
                                                .findFirst().orElse(null);
                                if (profLogado != null) {
                                        List<Turma> turmasProf = turmas.stream()
                                                        .filter(t -> t.getProfessor() != null
                                                                        && t.getProfessor().getId().equals(profLogado.getId()))
                                                        .collect(Collectors.toList());
                                        pt.studioflow.service.RemuneracaoService.Dados dRem = dadosRemuneracao(
                                                        studio, turmas, todasMensalidades, todasAulas);
                                        double aReceber = remuneracaoService
                                                        .pagamentosPorProfessor(java.util.List.of(profLogado), turmasProf,
                                                                        studio, mesAtual, dRem)
                                                        .stream()
                                                        .mapToDouble(pt.studioflow.service.RemuneracaoService.LinhaPagamento::total)
                                                        .sum();
                                        Span valorSpan = new Span(String.format("%.2f €", aReceber));
                                        valorSpan.getStyle().set("font-size", "26px").set("font-weight", "800")
                                                        .set("color", "#2e7d32").set("display", "block")
                                                        .set("margin-top", "10px");
                                        Span labelValor = new Span("VALOR ESTIMADO A RECEBER");
                                        labelValor.getStyle().set("font-size", "10px").set("color", "#95a5a6")
                                                        .set("font-weight", "bold");
                                        VerticalLayout vValor = new VerticalLayout(valorSpan, labelValor);
                                        vValor.setAlignItems(FlexComponent.Alignment.CENTER);
                                        vValor.setPadding(false);
                                        vValor.setSpacing(false);
                                        vA.add(vValor);
                                }

                                gridLayout.add(criarCard("Atividade de "
                                                + mesAtual.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt")),
                                                VaadinIcon.CHART, vA));

                                // BI do Professor
                                VerticalLayout vBI = new VerticalLayout();
                                vBI.setPadding(false);
                                int tAlunos = turmas.stream().filter(t -> t.getProfessor() != null
                                                && normalizar(t.getProfessor().getNome()).contains(busca))
                                                .mapToInt(t -> alunoTurmaRepository.findByTurma(t).size()).sum();
                                vBI.add(new Span("Alunos Ativos: " + tAlunos));
                                ProgressBar pbBI = new ProgressBar();
                                pbBI.setValue(Math.max(0.0, Math.min(1.0, (double) tAlunos / 50.0)));
                                vBI.add(new Span("Meta 50 alunos"), pbBI);
                                if (tAlunos >= 20) {
                                        Span s = new Span("Elegível para Bónus!");
                                        s.getStyle().set("color", "#2e7d32").set("font-weight", "bold");
                                        vBI.add(s);
                                }
                                Div cBI = criarCard("Metas do Professor", VaadinIcon.LIGHTBULB, vBI);
                                cBI.getStyle().set("background-color", "#f3e5f5");
                                gridLayout.add(cBI);
                        }
                }

                // --- CARDS KPI ADMIN (12 CARDS) ---
                if (isAdmin) {
                        Div cAtivos = criarCard("Alunos Ativos", VaadinIcon.USERS,
                                        valorGrande(String.valueOf(todosAlunos.stream().filter(Aluno::isAtivo).count()),
                                                        "#1976d2"));
                        cAtivos.addClickListener(e -> abrirModalAlocacaoPorTurma(turmas));
                        gridLayout.add(cAtivos);

                        List<Aluno> anivs = todosAlunos.stream().filter(a -> a.getDataNascimento() != null
                                        && a.getDataNascimento().getMonth() == hoje.getMonth()
                                        && a.getDataNascimento().getDayOfMonth() == hoje.getDayOfMonth()).toList();
                        gridLayout.add(criarCardAniversarios(anivs));

                        LocalDate inicioMes = mesAtual.atDay(1);
                        LocalDate inicioAnoLetivo = LocalDate.of(
                                        hoje.getMonthValue() >= 9 ? hoje.getYear() : hoje.getYear() - 1, 9, 1);
                        // Contagem de "Novos Alunos" arranca em 1 de agosto (época de inscrições),
                        // um mês antes do início oficial do Ano Letivo usado nas Renovações.
                        LocalDate inicioContagemNovosAlunos = LocalDate.of(
                                        hoje.getMonthValue() >= 8 ? hoje.getYear() : hoje.getYear() - 1, 8, 1);

                        // "Novo aluno" = teve inscrição/renovação registada no período E não tinha
                        // presenças antes do início do período (senão é apenas uma renovação de
                        // um aluno já existente, não um aluno novo).
                        Set<Long> alunosComPresencaAntesDoMes = todasPresencas.stream()
                                        .filter(p -> p.getAluno() != null && p.getData() != null
                                                        && p.getData().isBefore(inicioMes))
                                        .map(p -> p.getAluno().getId()).collect(Collectors.toSet());
                        Set<Long> alunosComPresencaAntesDaContagemAnoLetivo = todasPresencas.stream()
                                        .filter(p -> p.getAluno() != null && p.getData() != null
                                                        && p.getData().isBefore(inicioContagemNovosAlunos))
                                        .map(p -> p.getAluno().getId()).collect(Collectors.toSet());

                        List<Aluno> novosMes = todosAlunos.stream().filter(a -> a.getDataInscricaoRenovacao() != null
                                        && YearMonth.from(a.getDataInscricaoRenovacao()).equals(mesAtual)
                                        && !alunosComPresencaAntesDoMes.contains(a.getId())).toList();
                        Div cNovosMes = criarCard(
                                        "Novos Alunos (" + mesAtual.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt")) + ")",
                                        VaadinIcon.STAR, valorGrande(String.valueOf(novosMes.size()), "#FFD700"));
                        cNovosMes.addClickListener(e -> abrirModalNovosAlunos(novosMes,
                                        "Novos Alunos — " + mesAtual.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt"))));
                        gridLayout.add(cNovosMes);

                        List<Aluno> novosAnoLetivo = todosAlunos.stream().filter(a -> a.getDataInscricaoRenovacao() != null
                                        && !a.getDataInscricaoRenovacao().isBefore(inicioContagemNovosAlunos)
                                        && !alunosComPresencaAntesDaContagemAnoLetivo.contains(a.getId())).toList();
                        Div cNovosAno = criarCard("Novos Alunos (Ano Letivo)", VaadinIcon.CALENDAR,
                                        valorGrande(String.valueOf(novosAnoLetivo.size()), "#FFA000"));
                        cNovosAno.addClickListener(e -> abrirModalNovosAlunos(novosAnoLetivo, "Novos Alunos — Ano Letivo"));
                        gridLayout.add(cNovosAno);

                        if (studio != null) {
                                gridLayout.add(criarCardRenovacoes(todosAlunos, inicioAnoLetivo));
                        }

                        LocalDate lR = hoje.minusDays(15);
                        List<Aluno> risco = todosAlunos.stream().filter(Aluno::isAtivo).filter(a -> {
                                Optional<LocalDate> up = todasPresencas.stream()
                                                .filter(p -> p.getAluno().getId().equals(a.getId()))
                                                .map(Presenca::getData).max(LocalDate::compareTo);
                                return up.map(d -> d.isBefore(lR)).orElse(true);
                        }).toList();
                        Div cRisco = criarCard("Alunos em Risco", VaadinIcon.WARNING,
                                        valorGrande(String.valueOf(risco.size()), "#e65100"));
                        cRisco.addClickListener(e -> abrirModalAlunosRisco(risco));
                        gridLayout.add(cRisco);

                        List<Aluno> segs = todosAlunos.stream().filter(Aluno::isAtivo)
                                        .filter(a -> a.getDataExpiracaoSeguro() != null
                                                        && a.getDataExpiracaoSeguro().isBefore(hoje))
                                        .toList();
                        gridLayout.add(criarCardSeguro(segs));

                        long tP = todasPresencas.stream().filter(
                                        p -> p.getData() != null && YearMonth.from(p.getData()).equals(mesAtual))
                                        .count();
                        double pA = calcularPercentagemAssiduidade(turmas, todasAulas, tP, mesAtual);
                        Div cA = criarCard("Assiduidade Global", VaadinIcon.CHECK_SQUARE_O,
                                        criarAssiduidadeContent(pA, tP));
                        cA.addClickListener(e -> abrirModalAssiduidadePorTurma(turmas, todasAulas, todasPresencas, mesAtual));
                        gridLayout.add(cA);

                        double vPrev = todasMensalidades.stream()
                                        .filter(m -> m.getAno() == mesAtual.getYear()
                                                        && m.getMes().getValue() == mesAtual.getMonthValue())
                                        .mapToDouble(Mensalidade::getValor).sum();
                        Div cP = criarCard("Previsão Mensal", VaadinIcon.MONEY,
                                        valorGrande(String.format("%.2f €", vPrev), "#607d8b"));
                        cP.addClickListener(e -> abrirModalPrevisaoPorTurma(turmas, todasMensalidades, mesAtual));
                        gridLayout.add(cP);

                        double vPa = todasMensalidades.stream()
                                        .filter(m -> m.getEstado() == EstadoMensalidade.PAGO
                                                        && m.getAno() == mesAtual.getYear()
                                                        && m.getMes().getValue() == mesAtual.getMonthValue())
                                        .mapToDouble(Mensalidade::getValor).sum();
                        gridLayout.add(criarCard("Total Pago", VaadinIcon.MONEY,
                                        valorGrande(String.format("%.2f €", vPa), "#2e7d32")));

                        double vDM = todasMensalidades.stream()
                                        .filter(m -> m.getEstado() == EstadoMensalidade.FATURADO
                                                        && m.getAno() == mesAtual.getYear()
                                                        && m.getMes().getValue() == mesAtual.getMonthValue())
                                        .mapToDouble(Mensalidade::getValor).sum();
                        gridLayout.add(criarCard("Dívida do Mês", VaadinIcon.CLOCK,
                                        valorGrande(String.format("%.2f €", vDM), "#d32f2f")));

                        double vDT = todasMensalidades.stream().filter(m -> m.getEstado() == EstadoMensalidade.FATURADO)
                                        .mapToDouble(Mensalidade::getValor).sum();
                        Div cDT = criarCard("Dívida Total", VaadinIcon.COINS,
                                        valorGrande(String.format("%.2f €", vDT), "#c62828"));
                        cDT.addClickListener(e -> abrirModalDividaHistorica(todasMensalidades));
                        gridLayout.add(cDT);

                        Div cMod = criarCard("Modalidades", VaadinIcon.BAR_CHART, criarGraficoModalidades(turmas, 4));
                        cMod.addClickListener(e -> abrirModalSimples("Ranking", criarGraficoModalidades(turmas, 100)));
                        gridLayout.add(cMod);

                        Map<String, Double> lucros = calcularRentabilidadeMap(turmas, todasMensalidades, todasAulas,
                                        mesAtual);
                        double totalL = lucros.values().stream().mapToDouble(v -> v).sum();
                        Div cRent = criarCard("Rentabilidade", VaadinIcon.CHART_LINE, valorGrande(
                                        String.format("%.2f €", totalL), totalL >= 0 ? "#2e7d32" : "#d32f2f"));
                        cRent.addClickListener(e -> abrirModalRentabilidadeDetalhada(lucros, turmas, todasMensalidades,
                                        mesAtual));
                        gridLayout.add(cRent);
                }

                add(gridLayout);
        }

        // --- MODAIS E AUXILIARES (PROTEÇÕES DE LIMITE INCLUÍDAS) ---

        private void abrirModalAlocacaoPorTurma(List<Turma> turmas) {
                Dialog d = new Dialog();
                d.setHeaderTitle("Alocação por Turma");
                d.setWidth("900px");
                d.setHeight("85vh");
                List<TurmaOcupacaoDTO> dados = turmas.stream().map(
                                t -> new TurmaOcupacaoDTO(t.getDescricao(), alunoTurmaRepository.findByTurma(t).size()))
                                .sorted(Comparator.comparingInt(TurmaOcupacaoDTO::getQuantidade).reversed()).toList();
                HorizontalLayout chart = new HorizontalLayout();
                chart.setWidthFull();
                chart.setHeight("200px");
                chart.setAlignItems(FlexComponent.Alignment.BASELINE);
                int max = dados.stream().mapToInt(TurmaOcupacaoDTO::getQuantidade).max().orElse(1);
                dados.forEach(dto -> {
                        VerticalLayout col = new VerticalLayout();
                        col.setPadding(false);
                        col.setAlignItems(FlexComponent.Alignment.CENTER);
                        Div bar = new Div();
                        bar.getStyle().set("background-color", "#1976d2").set("width", "30px");
                        bar.setHeight(((double) dto.getQuantidade() / max * 140) + "px");
                        col.add(new Span(String.valueOf(dto.getQuantidade())), bar);
                        chart.add(col);
                });
                Grid<TurmaOcupacaoDTO> g = new Grid<>();
                g.setItems(dados);
                g.addColumn(TurmaOcupacaoDTO::getNome).setHeader("Turma");
                g.addColumn(TurmaOcupacaoDTO::getQuantidade).setHeader("Alunos");
                d.add(new VerticalLayout(chart, g) {
                        {
                                setSizeFull();
                                expand(g);
                        }
                });
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private void abrirModalSeguros(List<Aluno> alunos) {
                Dialog d = new Dialog();
                d.setHeaderTitle("Seguros Expirados");
                d.setWidth("1000px");
                d.setHeight("80vh");
                Grid<Aluno> g = new Grid<>(Aluno.class, false);
                g.setItems(alunos);
                g.addColumn(Aluno::getNomeCompleto).setHeader("Nome").setSortable(true);
                g.addColumn(Aluno::getTelemovel).setHeader("Telemóvel");
                g.addColumn(Aluno::getDataExpiracaoSeguro).setHeader("Data Expiração").setSortable(true);
                g.setSizeFull();
                d.add(g);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private void abrirModalAlunosRisco(List<Aluno> alunos) {
                Dialog d = new Dialog();
                d.setHeaderTitle("Alunos em Risco");
                d.setWidth("900px");
                d.setHeight("80vh");
                Grid<Aluno> g = new Grid<>(Aluno.class, false);
                g.setItems(alunos);
                g.addColumn(Aluno::getNomeCompleto).setHeader("Nome").setSortable(true);
                g.addColumn(a -> presencaRepository.findByAlunoId(a.getId()).stream()
                                .map(Presenca::getData)
                                .max(LocalDate::compareTo).map(Object::toString).orElse("Nunca"))
                                .setHeader("Última Presença");
                g.setSizeFull();
                d.add(g);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private void abrirModalPrevisaoPorTurma(List<Turma> turmas, List<Mensalidade> todasM, YearMonth mesRef) {
                Dialog d = new Dialog();
                d.setHeaderTitle("Previsão: " + mesRef.getMonth());
                d.setWidth("800px");
                Map<String, Double> prev = new HashMap<>();
                for (Turma t : turmas) {
                        double s = todasM.stream().filter(m -> m.getAno() == mesRef.getYear()
                                        && m.getMes().getValue() == mesRef.getMonthValue() && m.getTurma() != null
                                        && m.getTurma().getId().equals(t.getId())).mapToDouble(Mensalidade::getValor)
                                        .sum();
                        if (s > 0)
                                prev.put(t.getDescricao(), s);
                }
                Grid<Map.Entry<String, Double>> g = new Grid<>();
                g.setItems(prev.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                                .toList());
                g.addColumn(Map.Entry::getKey).setHeader("Turma");
                g.addColumn(e -> String.format("%.2f €", e.getValue())).setHeader("Previsto");
                d.add(g);
                d.open();
        }

        private Map<String, Double> calcularRentabilidadeMap(List<Turma> turmas, List<Mensalidade> mens,
                        List<Aula> aulas, YearMonth mes) {
                Studio studio = TenantContext.getCurrentStudio();
                pt.studioflow.service.RemuneracaoService.Dados d = dadosRemuneracao(studio, turmas, mens, aulas);
                Map<Long, double[]> rent = remuneracaoService.rentabilidadePorTurma(turmas, studio, mes, d);
                Map<String, Double> res = new HashMap<>();
                for (Turma t : turmas) {
                        double[] x = rent.get(t.getId());
                        if (x != null && (x[0] != 0 || x[1] != 0))
                                res.put(t.getDescricao(), x[2]);
                }
                return res;
        }

        private pt.studioflow.service.RemuneracaoService.Dados dadosRemuneracao(Studio studio,
                        List<Turma> turmas, List<Mensalidade> mens, List<Aula> aulas) {
                java.util.Set<Long> turmaIds = turmas.stream().map(Turma::getId).collect(Collectors.toSet());
                return new pt.studioflow.service.RemuneracaoService.Dados()
                                .mensalidades(mens)
                                .aulas(aulas)
                                .registos(studio != null ? registoHorasRepository.findAllByStudio(studio)
                                                : registoHorasRepository.findAll())
                                .inscricoes(alunoTurmaRepository.findAll().stream()
                                                .filter(at -> at.getTurma() != null
                                                                && turmaIds.contains(at.getTurma().getId()))
                                                .collect(Collectors.toList()));
        }

        private void abrirModalRentabilidadeDetalhada(Map<String, Double> lucros, List<Turma> turmas,
                        List<Mensalidade> todasM, YearMonth mes) {
                Dialog d = new Dialog();
                d.setHeaderTitle("Lucro Real por Turma");
                d.setWidth("950px");
                Grid<String> g = new Grid<>();
                g.setItems(lucros.keySet().stream().sorted((a, b) -> lucros.get(b).compareTo(lucros.get(a))).toList());
                g.addColumn(n -> n).setHeader("Turma");
                g.addColumn(n -> String.format("%.2f€",
                                todasM.stream().filter(m -> m.getTurma() != null
                                                && m.getTurma().getDescricao().equals(n) && m.getAno() == mes.getYear()
                                                && m.getMes().getValue() == mes.getMonthValue())
                                                .mapToDouble(Mensalidade::getValor).sum()))
                                .setHeader("Receita");
                g.addColumn(n -> String.format("%.2f€", lucros.get(n))).setHeader("Lucro");
                d.add(g);
                d.open();
        }

        private VerticalLayout criarAssiduidadeContent(double p, long t) {
                VerticalLayout v = new VerticalLayout();
                v.setAlignItems(FlexComponent.Alignment.CENTER);
                Span s = new Span(String.format("%.1f%%", p));
                s.getStyle().set("font-size", "28px").set("font-weight", "bold");
                ProgressBar b = new ProgressBar();
                b.setValue(Math.max(0.0, Math.min(1.0, p / 100.0)));
                v.add(s, b, new Span(t + " presenças"));
                return v;
        }

        private String normalizar(String t) {
                if (t == null)
                        return "";
                String s = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD);
                return s.replaceAll("[^\\p{ASCII}]", "").toLowerCase().trim();
        }

        private Div criarCard(String t, VaadinIcon i, com.vaadin.flow.component.Component c) {
                Div card = new Div();
                card.getStyle().set("padding", "15px").set("border-radius", "15px").set("background-color", "white")
                                .set("box-shadow", "0 4px 12px rgba(0,0,0,0.1)").set("display", "flex")
                                .set("flex-direction", "column");
                HorizontalLayout h = new HorizontalLayout(i.create(), new Span(t));
                h.getStyle().set("border-bottom", "1px solid #f0f0f0").set("padding-bottom", "10px");
                Div s = new Div(c);
                s.getStyle().set("overflow-y", "auto").set("flex-grow", "1");
                card.add(h, s);
                return card;
        }

        private Div criarCardSeguro(List<Aluno> e) {
                HorizontalLayout hl = new HorizontalLayout();
                if (!e.isEmpty()) {
                        Icon i = VaadinIcon.WARNING.create();
                        i.setColor("red");
                        hl.add(i);
                }
                hl.add(valorGrande(String.valueOf(e.size()), e.isEmpty() ? "green" : "red"));
                Div c = criarCard("Seguros Expirados", VaadinIcon.EXCLAMATION_CIRCLE_O, hl);
                c.addClickListener(ev -> abrirModalSeguros(e));
                return c;
        }

        private Span valorGrande(String v, String c) {
                Span s = new Span(v);
                s.getStyle().set("font-size", "28px").set("font-weight", "bold").set("color", c).set("display", "block")
                                .set("text-align", "center").set("margin-top", "15px");
                return s;
        }

        private Span criarLinhaTextoDestaque(String d, String v, String c) {
                HorizontalLayout hl = new HorizontalLayout(new Span(d), new Span(v));
                hl.setWidthFull();
                hl.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                hl.getChildren().toArray(Span[]::new)[1].getStyle().set("color", c).set("font-weight", "bold");
                return new Span(hl);
        }

        private Div criarCardAniversarios(List<Aluno> h) {
                boolean t = !h.isEmpty();
                VerticalLayout l = new VerticalLayout();
                l.setAlignItems(FlexComponent.Alignment.CENTER);
                Span n = new Span(String.valueOf(h.size()));
                n.getStyle().set("font-size", "42px").set("font-weight", "bold").set("color",
                                t ? "#e91e63" : "#9e9e9e");
                l.add(n, new Span(t ? "Aniversários Hoje!" : "Sem festas hoje"));
                Div c = criarCard("Aniversários", t ? VaadinIcon.GIFT : VaadinIcon.CALENDAR_CLOCK, l);
                c.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate("crm")));
                return c;
        }

        private com.vaadin.flow.component.Component criarGraficoModalidades(List<Turma> t, int l) {
                VerticalLayout v = new VerticalLayout();
                v.setPadding(false);
                Map<String, Integer> s = new HashMap<>();
                t.forEach(tr -> {
                        String m = tr.getModalidade() != null ? tr.getModalidade().getDescricao() : "Outra";
                        s.put(m, s.getOrDefault(m, 0) + alunoTurmaRepository.findByTurma(tr).size());
                });
                int m = s.values().stream().max(Integer::compare).orElse(1);
                s.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(l)
                                .forEach(e -> {
                                        ProgressBar p = new ProgressBar();
                                        p.setValue(Math.max(0.0, Math.min(1.0, (double) e.getValue() / m)));
                                        v.add(new Span(e.getKey() + " (" + e.getValue() + ")"), p);
                                });
                return v;
        }

        private void abrirModalSimples(String t, com.vaadin.flow.component.Component c) {
                Dialog d = new Dialog();
                d.setHeaderTitle(t);
                d.add(c);
                d.open();
        }

        private Div criarCardRenovacoes(List<Aluno> alunosDoStudio, LocalDate inicioAnoLetivo) {
                long pendentes = dashboardService.countRenovacoesPendentes(alunosDoStudio);
                long concluidas = dashboardService.countRenovacoesConcluidasAnoLetivo(alunosDoStudio, inicioAnoLetivo);
                long elegiveis = dashboardService.countElegiveisRenovacaoAnoLetivo(alunosDoStudio, inicioAnoLetivo);

                VerticalLayout content = new VerticalLayout();
                content.setPadding(false);
                content.setSpacing(false);
                content.getStyle().set("gap", "6px");

                HorizontalLayout linhaTopo = new HorizontalLayout(
                                valorGrande(String.valueOf(pendentes), pendentes > 0 ? "#e65100" : "#607d8b"),
                                new Span("pedidos por validar"));
                linhaTopo.setAlignItems(FlexComponent.Alignment.BASELINE);

                ProgressBar pb = new ProgressBar();
                pb.setWidthFull();
                pb.setValue(elegiveis > 0 ? Math.max(0.0, Math.min(1.0, (double) concluidas / elegiveis)) : 0.0);

                Span legenda = new Span(concluidas + " de " + elegiveis + " alunos elegíveis já renovaram este ano letivo");
                legenda.getStyle().set("font-size", "12px").set("color", "#7f8c8d");

                content.add(linhaTopo, pb, legenda);

                Div card = criarCard("Renovações", VaadinIcon.REFRESH, content);
                card.getStyle().set("grid-column", "1 / -1");
                card.addClickListener(e -> abrirModalRenovacoesPendentes(alunosDoStudio));
                return card;
        }

        private void abrirModalRenovacoesPendentes(List<Aluno> alunosDoStudio) {
                List<Aluno> pendentes = dashboardService.listarRenovacoesPendentes(alunosDoStudio);

                Dialog d = new Dialog();
                d.setHeaderTitle("Renovações Pendentes de Validação");
                d.setWidth("650px");

                if (pendentes.isEmpty()) {
                        d.add(new Span("Não há pedidos de renovação por validar."));
                } else {
                        Grid<Aluno> g = new Grid<>(Aluno.class, false);
                        g.setItems(pendentes);
                        g.addColumn(Aluno::getNomeCompleto).setHeader("Nome").setFlexGrow(2);
                        g.addColumn(a -> a.getTurmas() != null && !a.getTurmas().isEmpty()
                                        ? a.getTurmas().stream().map(at -> at.getTurma().getDescricao())
                                                        .collect(Collectors.joining(", "))
                                        : "—").setHeader("Turma Atual").setFlexGrow(2);
                        g.addColumn(Aluno::getDataInscricaoRenovacao).setHeader("Pedido em");
                        d.add(g);

                        Button btnIr = new Button("Ir para Validação de Inscrições",
                                        e -> getUI().ifPresent(ui -> ui.navigate("validar-inscricoes")));
                        d.getFooter().add(btnIr);
                }
                d.open();
        }

        private void abrirModalNovosAlunos(List<Aluno> a, String titulo) {
                Dialog d = new Dialog();
                d.setHeaderTitle(titulo);
                Grid<Aluno> g = new Grid<>(Aluno.class, false);
                g.setItems(a);
                g.addColumn(Aluno::getNomeCompleto).setHeader("Nome");
                d.add(g);
                d.open();
        }

        private void abrirModalDividaHistorica(List<Mensalidade> mens) {
                Dialog d = new Dialog();
                d.setHeaderTitle("📊 Histórico de Dívidas por Mês");
                d.setWidth("750px");
                d.setHeight("80vh");

                // Agrupar dívidas FATURADO por mês/ano
                record ChaveMes(int ano, int mes) {}
                Map<ChaveMes, Double> dividaPorMes = new TreeMap<>(
                        Comparator.<ChaveMes>comparingInt(ChaveMes::ano).thenComparingInt(ChaveMes::mes));

                for (Mensalidade m : mens) {
                        if (m.getEstado() == EstadoMensalidade.FATURADO) {
                                ChaveMes chave = new ChaveMes(m.getAno(), m.getMes().getValue());
                                dividaPorMes.merge(chave, m.getValor(), Double::sum);
                        }
                }

                // Grid por mês
                record DividaMesDTO(String periodo, double valor) {}
                List<DividaMesDTO> linhas = dividaPorMes.entrySet().stream()
                        .map(e -> new DividaMesDTO(
                                Month.of(e.getKey().mes()).getDisplayName(TextStyle.FULL, new Locale("pt")) + " " + e.getKey().ano(),
                                e.getValue()))
                        .sorted(Comparator.comparingDouble(DividaMesDTO::valor).reversed())
                        .toList();

                double totalDivida = linhas.stream().mapToDouble(DividaMesDTO::valor).sum();

                Grid<DividaMesDTO> grid = new Grid<>();
                grid.setItems(linhas);
                grid.setSizeFull();
                grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
                grid.addColumn(DividaMesDTO::periodo).setHeader("Período").setAutoWidth(true).setSortable(true);
                grid.addColumn(row -> String.format("%.2f €", row.valor())).setHeader("Dívida").setAutoWidth(true).setSortable(true);
                grid.addComponentColumn(row -> {
                        double pct = totalDivida > 0 ? row.valor() / totalDivida : 0;
                        ProgressBar pb = new ProgressBar();
                        pb.setValue(Math.min(1.0, pct));
                        pb.setWidth("120px");
                        pb.getStyle().set("--lumo-primary-color", "#d32f2f");
                        return pb;
                }).setHeader("% do Total").setAutoWidth(true);

                Div totalDiv = new Div();
                totalDiv.getStyle()
                        .set("background", "#fbe9e7").set("border-radius", "8px")
                        .set("padding", "12px 16px").set("margin-bottom", "12px")
                        .set("font-weight", "700").set("color", "#c62828")
                        .set("font-size", "1.1rem");
                totalDiv.setText("Dívida Total: " + String.format("%.2f €", totalDivida));

                VerticalLayout content = new VerticalLayout(totalDiv, grid);
                content.setSizeFull();
                content.expand(grid);
                content.setPadding(false);

                d.add(content);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private void abrirModalAssiduidadePorTurma(List<Turma> turmas, List<Aula> aulas, List<Presenca> todasPresencas, YearMonth mes) {
                Dialog d = new Dialog();
                d.setHeaderTitle("📋 Assiduidade por Turma — " +
                        mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt")) + " " + mes.getYear());
                d.setWidth("850px");
                d.setHeight("80vh");

                record AssiduidadeTurmaDTO(String turma, String professor, long presencas, long esperadas, double pct) {}

                List<AssiduidadeTurmaDTO> dados = turmas.stream().map(t -> {
                        List<Aula> aulasTurma = aulas.stream()
                                .filter(a -> a.getTurma() != null && a.getTurma().getId().equals(t.getId()))
                                .toList();

                        long nAlunos = alunoTurmaRepository.findByTurma(t).size();
                        long esperadas = 0;
                        LocalDate hoje = LocalDate.now();
                        int diasNoMes = mes.equals(YearMonth.from(hoje)) ? hoje.getDayOfMonth() : mes.lengthOfMonth();

                        for (Aula au : aulasTurma) {
                                for (int i = 1; i <= diasNoMes; i++) {
                                        if (mes.atDay(i).getDayOfWeek() == au.getDia()) esperadas += nAlunos;
                                }
                        }

                        long presencasReais = todasPresencas.stream()
                                .filter(p -> p.getAluno() != null && p.getData() != null
                                        && YearMonth.from(p.getData()).equals(mes)
                                        && alunoTurmaRepository.findByTurma(t).stream()
                                                .anyMatch(at -> at.getAluno().getId().equals(p.getAluno().getId())))
                                .count();

                        double pct = esperadas > 0 ? (double) presencasReais * 100 / esperadas : 0;
                        String nomeProfessor = t.getProfessor() != null ? t.getProfessor().getNome() : "—";
                        return new AssiduidadeTurmaDTO(t.getDescricao(), nomeProfessor, presencasReais, esperadas, pct);
                }).sorted(Comparator.comparingDouble(AssiduidadeTurmaDTO::pct).reversed()).toList();

                Grid<AssiduidadeTurmaDTO> grid = new Grid<>();
                grid.setItems(dados);
                grid.setSizeFull();
                grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
                grid.addColumn(AssiduidadeTurmaDTO::turma).setHeader("Turma").setAutoWidth(true).setSortable(true);
                grid.addColumn(AssiduidadeTurmaDTO::professor).setHeader("Professor").setAutoWidth(true);
                grid.addColumn(AssiduidadeTurmaDTO::presencas).setHeader("Presenças").setAutoWidth(true).setSortable(true);
                grid.addColumn(AssiduidadeTurmaDTO::esperadas).setHeader("Esperadas").setAutoWidth(true);
                grid.addComponentColumn(row -> {
                        Span badge = new Span(String.format("%.0f%%", row.pct()));
                        String cor = row.pct() >= 75 ? "#2e7d32" : row.pct() >= 50 ? "#e65100" : "#c62828";
                        String bgCor = row.pct() >= 75 ? "#e8f5e9" : row.pct() >= 50 ? "#fff3e0" : "#ffebee";
                        badge.getStyle().set("background", bgCor).set("color", cor)
                                .set("padding", "3px 10px").set("border-radius", "12px")
                                .set("font-weight", "700");
                        return badge;
                }).setHeader("Assiduidade").setAutoWidth(true).setSortable(true).setComparator(AssiduidadeTurmaDTO::pct);

                d.add(grid);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private double calcularPercentagemAssiduidade(List<Turma> t, List<Aula> a, long pr, YearMonth m) {
                long v = 0;
                LocalDate h = LocalDate.now();
                for (Turma tr : t) {
                        long n = alunoTurmaRepository.findByTurma(tr).size();
                        List<Aula> as = a.stream()
                                        .filter(au -> au.getTurma() != null && au.getTurma().getId().equals(tr.getId()))
                                        .toList();
                        for (Aula au : as)
                                for (int i = 1; i <= h.getDayOfMonth(); i++)
                                        if (m.atDay(i).getDayOfWeek() == au.getDia())
                                                v += n;
                }
                return v == 0 ? 0 : (double) (pr * 100.0) / v;
        }

        private com.vaadin.flow.component.Component criarLinhaAgenda(String hora, String descricao, String cor) {
                com.vaadin.flow.component.html.Span spanHora = new com.vaadin.flow.component.html.Span(hora);
                spanHora.getStyle().set("font-weight", "700").set("color", cor).set("min-width", "50px");
                com.vaadin.flow.component.html.Span spanDesc = new com.vaadin.flow.component.html.Span(descricao);
                spanDesc.getStyle().set("flex-grow", "1");
                HorizontalLayout linha = new HorizontalLayout(spanHora, spanDesc);
                linha.setWidthFull();
                linha.setAlignItems(FlexComponent.Alignment.CENTER);
                linha.getStyle().set("padding", "4px 8px").set("border-left", "4px solid " + cor)
                                .set("border-radius", "4px").set("background", cor + "18");
                return linha;
        }

        private String extrairNomesCurtos(List<Aluno> a) {
                if (a == null)
                        return "";
                return a.stream().map(al -> {
                        String n = al.getNomeCompleto();
                        if (n == null || n.isBlank())
                                return "";
                        return n.split(" ")[0];
                        }).collect(java.util.stream.Collectors.joining(", "));
        }
}
