package pt.studioflow.view;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
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
import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;
import pt.studioflow.util.LogoUrl;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Dashboard do estúdio.
 * <p>
 * ADMIN: organizado pela ordem em que a dona/secretaria precisa da informação de manhã:
 * <ol>
 * <li>Cabeçalho (saudação, data, atalhos);</li>
 * <li>"Precisa de atenção" (o que exige ação hoje) e "Hoje no estúdio" (aulas e marcações do dia);</li>
 * <li>"Saúde do negócio" (KPIs do mês, clicáveis);</li>
 * <li>"Tendências" (receita dos últimos 6 meses, modalidades, turmas).</li>
 * </ol>
 * Os cartões de KPI respeitam a escolha por estúdio feita na Gestão de Estúdios
 * ({@link Studio#hasDashboardCard}); as ligações respeitam os módulos ativos ({@link Studio#hasModulo}).
 * <p>
 * PROF / ALUNO: área pessoal (agenda do dia, pedidos de sala, atividade e metas) — sem alterações de lógica.
 */
@Route(value = "dashboard", layout = MainLayout.class)
@UIScope
@RolesAllowed({ "ADMIN", "PROF", "DELEG", "ALUNO" })
public class DashboardView extends Div {

        private static final Locale PT = Locale.forLanguageTag("pt-PT");
        private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
        private static final DateTimeFormatter DIA_LONGO = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT);
        private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("d 'de' MMMM", PT);
        /** Aluno ativo sem presenças há mais do que estes dias conta como "em risco". */
        private static final int DIAS_SEM_PRESENCA_RISCO = 15;
        /** Linhas mostradas nos rankings do dashboard (o resto fica no "Ver todas"). */
        private static final int TOP_RANKING = 5;

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
        private final OcorrenciaAulaRepository ocorrenciaAulaRepository;
        private final InterrupcaoLetivaRepository interrupcaoLetivaRepository;
        private final ListaEsperaRepository listaEsperaRepository;
        private final MensalidadeConfig mensalidadeConfig;
        private final pt.studioflow.service.DashboardService dashboardService;
        private final pt.studioflow.service.RemuneracaoService remuneracaoService;

        @org.springframework.beans.factory.annotation.Autowired
        private pt.studioflow.service.AulaExperimentalService aulaExperimentalService;

        @org.springframework.beans.factory.annotation.Autowired
        private pt.studioflow.service.ProfessorTurmasService professorTurmasService;

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
                        OcorrenciaAulaRepository ocorrenciaAulaRepository,
                        InterrupcaoLetivaRepository interrupcaoLetivaRepository,
                        ListaEsperaRepository listaEsperaRepository,
                        MensalidadeConfig mensalidadeConfig,
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
                this.ocorrenciaAulaRepository = ocorrenciaAulaRepository;
                this.interrupcaoLetivaRepository = interrupcaoLetivaRepository;
                this.listaEsperaRepository = listaEsperaRepository;
                this.mensalidadeConfig = mensalidadeConfig;
                this.dashboardService = dashboardService;
                this.remuneracaoService = remuneracaoService;

                addClassName("cf-dashboard");
                setWidthFull();
        }

        /**
         * A vista é @UIScope (a instância é reutilizada dentro da mesma janela): reconstrói o
         * conteúdo a cada entrada para os números refletirem o que mudou noutras vistas
         * (ex.: mensalidade marcada como paga, inscrição validada).
         */
        @Override
        protected void onAttach(AttachEvent attachEvent) {
                super.onAttach(attachEvent);
                removeAll();
                construir();
        }

        private void reconstruirDepois() {
                removeAll();
                construir();
        }

        private void construir() {
                boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                boolean isProf = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_PROF"));
                boolean isAluno = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ALUNO"));

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
                // Professor do utilizador resolvido por email (fallback: nome único) —
                // nunca por nome.contains(primeiroNome): "Ana" apanhava a "Ana Maria Costa".
                final Professor profEu = isProf
                                ? professorTurmasService.professorDoUtilizador(studio).orElse(null) : null;
                final Long profEuId = profEu != null ? profEu.getId() : null;
                final Predicate<String> ehMeu = nome -> profEu != null
                                && pt.studioflow.service.ProfessorTurmasService.nomeGravadoCorresponde(nome, profEu, login);

                // --- ADMIN: dashboard do estúdio ---
                if (isAdmin) {
                        add(construirDashboardAdmin(studio, firstNameBD, turmas, todasAulas, todasMensalidades,
                                        todosAlunos, todasPresencas, todasAsPrivadasDeHoje, mesAtual, hoje));
                }

                // --- ÁREA PESSOAL & PROFESSOR (comportamento inalterado) ---
                if (isProf || isAluno) {
                        FlexLayout gridLayout = new FlexLayout();
                        gridLayout.setWidthFull();
                        gridLayout.addClassName("cf-dashboard-grid");
                        // FlexLayout põe display:flex inline; a grelha responsiva vem de .cf-dashboard-grid
                        gridLayout.getStyle().set("display", "grid");

                        VerticalLayout vPerso = new VerticalLayout();
                        vPerso.setPadding(false);
                        vPerso.setSpacing(true);
                        vPerso.add(new H3("Olá, " + firstNameBD + "!"));

                        Set<String> agenda = new TreeSet<>(Comparator.comparing(s -> s.split("\\|")[0]));

                        if (isProf) {
                                todasAulas.stream()
                                                .filter(a -> a.getDia() == hoje.getDayOfWeek())
                                                .filter(a -> a.getTurma() != null && profEuId != null
                                                                && a.getProfessorEfetivo() != null
                                                                && profEuId.equals(a.getProfessorEfetivo().getId()))
                                                .forEach(a -> agenda.add(a.getHoraInicio() + "|"
                                                                + a.getTurma().getDescricao() + "|var(--cf-success)"));

                                todasAsPrivadasDeHoje.stream()
                                                .filter(ms -> ehMeu.test(ms.getProfessor()))
                                                .forEach(ms -> {
                                                        String nomesAlunos = extrairNomesCurtos(ms.getAlunos());
                                                        agenda.add(ms.getHoraInicio() + "|Aula "
                                                                        + ms.getTipo().toLowerCase() + " ("
                                                                        + nomesAlunos + ")|var(--cf-violet-text)");
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
                        cardPessoal.addClassNames("cf-card--span-all", "cf-card--info");
                        gridLayout.add(cardPessoal);

                        // Aulas experimentais da semana nas turmas do professor (o admin tem painel próprio)
                        if (isProf && !isAdmin && studio != null) {
                                java.util.Set<Long> idsTurmas = professorTurmasService.restritoAsProprias()
                                                ? professorTurmasService.turmaIdsDoUtilizador(studio) : null;
                                List<pt.studioflow.model.AulaExperimental> expSemana = aulaExperimentalService.daSemana(studio, idsTurmas);
                                if (!expSemana.isEmpty()) {
                                        Div cExp = criarCard("Aulas experimentais esta semana", VaadinIcon.MAGIC,
                                                        listaExperimentaisSemana(expSemana));
                                        cExp.addClassNames("cf-card--span-all", "cf-card--violet");
                                        gridLayout.add(cExp);
                                }
                        }

                        if (isProf) {
                                // Pedidos Sala
                                List<MarcacaoSala> pendentes = (studio != null
                                                ? marcacaoSalaRepository.findByStudioAndStatus(studio, "PENDENTE")
                                                : marcacaoSalaRepository.findAll()).stream()
                                                .filter(m -> ehMeu.test(m.getProfessor()))
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
                                                .filter(r -> ehMeu.test(r.getProfessor())
                                                                && r.getMesNumero() == mesAtual.getMonthValue()
                                                                && !"ENSAIO".equalsIgnoreCase(r.getTipoAtividade()))
                                                .mapToDouble(r -> pt.studioflow.util.HorasUtil.faturaveis(r.getInicio(), r.getFim()))
                                                .sum();

                                double hEns = registosMes.stream()
                                                .filter(r -> ehMeu.test(r.getProfessor())
                                                                && r.getMesNumero() == mesAtual.getMonthValue()
                                                                && "ENSAIO".equalsIgnoreCase(r.getTipoAtividade()))
                                                .mapToDouble(r -> pt.studioflow.util.HorasUtil.faturaveis(r.getInicio(), r.getFim()))
                                                .sum();

                                List<MarcacaoSala> marcacoesMes = studio != null
                                                ? marcacaoSalaRepository.findAllByStudio(studio)
                                                : marcacaoSalaRepository.findAll();
                                double hPriv = marcacoesMes.stream()
                                                .filter(m -> ehMeu.test(m.getProfessor())
                                                                && m.getData().getMonthValue() == mesAtual
                                                                                .getMonthValue()
                                                                && "PRIVADA".equalsIgnoreCase(m.getTipo()))
                                                .mapToDouble(m -> pt.studioflow.util.HorasUtil.faturaveis(m.getHoraInicio(), m.getHoraFim()))
                                                .sum();

                                vA.add(criarLinhaTextoDestaque("Regulares", String.format("%.1f h", hReg), "cf-tone-success"));
                                vA.add(criarLinhaTextoDestaque("Ensaios", String.format("%.1f h", hEns), "cf-tone-info"));
                                vA.add(criarLinhaTextoDestaque("Privadas", String.format("%.1f h", hPriv), "cf-tone-violet"));

                                // Valor total acumulado com maior destaque
                                double totalGeral = hReg + hEns + hPriv;
                                Span totalSpan = new Span(String.format("%.1f", totalGeral));
                                totalSpan.addClassNames("cf-kpi-value", "cf-kpi-value--hero");

                                Span labelTotal = new Span("HORAS TOTAIS NO MÊS");
                                labelTotal.addClassName("cf-kpi-label");

                                VerticalLayout vTotal = new VerticalLayout(totalSpan, labelTotal);
                                vTotal.setAlignItems(FlexComponent.Alignment.CENTER);
                                vTotal.setPadding(false);
                                vTotal.setSpacing(false);
                                vTotal.addClassName("cf-kpi-block");

                                vA.add(vTotal);

                                // --- Valor estimado a receber (conforme modo de remuneração efetivo) ---
                                Professor profLogado = profEu;
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
                                        valorSpan.addClassNames("cf-kpi-value", "cf-tone-success");
                                        Span labelValor = new Span("VALOR ESTIMADO A RECEBER");
                                        labelValor.addClassName("cf-kpi-label");
                                        VerticalLayout vValor = new VerticalLayout(valorSpan, labelValor);
                                        vValor.setAlignItems(FlexComponent.Alignment.CENTER);
                                        vValor.setPadding(false);
                                        vValor.setSpacing(false);
                                        vA.add(vValor);
                                }

                                gridLayout.add(criarCard("Atividade de "
                                                + mesAtual.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt")),
                                                VaadinIcon.CHART, vA));

                                // BI do Professor (só em estúdios com bónus de professores)
                                if (studio != null && studio.isBonusProfessores()) {
                                VerticalLayout vBI = new VerticalLayout();
                                vBI.setPadding(false);
                                // Conta cabeças: aluno em várias turmas do professor só conta uma vez.
                                // Só alunos ativados e confirmados (sem experimentais nem pendentes)
                                // e com pelo menos uma presença registada numa turma do professor
                                final int META_BONUS_ALUNOS = studio.getMetaBonusAlunos();
                                Set<Long> idsTurmasProfBI = turmas.stream().filter(t -> t.getProfessor() != null
                                                        && t.getProfessor().getId().equals(profEuId))
                                                .map(Turma::getId).collect(Collectors.toSet());
                                Set<Long> alunosComPresencaProf = todasPresencas.stream()
                                                .filter(p -> p.isPresente() && p.getAluno() != null && p.getTurma() != null
                                                        && idsTurmasProfBI.contains(p.getTurma().getId()))
                                                .map(p -> p.getAluno().getId()).collect(Collectors.toSet());
                                int tAlunos = (int) turmas.stream().filter(t -> t.getProfessor() != null
                                                && t.getProfessor().getId().equals(profEuId))
                                                .flatMap(t -> alunoTurmaRepository.findByTurma(t).stream())
                                                .map(AlunoTurma::getAluno)
                                                .filter(a -> a != null && a.isAtivo()
                                                        && a.getStatus() == Aluno.AlunoStatus.ATIVO
                                                        && alunosComPresencaProf.contains(a.getId()))
                                                .map(Aluno::getId)
                                                .distinct().count();
                                vBI.add(new Span("Alunos Ativos: " + tAlunos));
                                ProgressBar pbBI = new ProgressBar();
                                pbBI.setValue(Math.max(0.0, Math.min(1.0, (double) tAlunos / META_BONUS_ALUNOS)));
                                vBI.add(new Span("Meta " + META_BONUS_ALUNOS + " alunos"), pbBI);
                                if (tAlunos >= META_BONUS_ALUNOS) {
                                        Span s = new Span("Elegível para Bónus!");
                                        s.addClassNames("cf-tone-success", "cf-strong");
                                        vBI.add(s);
                                }
                                Div cBI = criarCard("Metas do Professor", VaadinIcon.LIGHTBULB, vBI);
                                cBI.addClassName("cf-card--violet");
                                gridLayout.add(cBI);
                                }
                        }

                        if (isAdmin) {
                                add(tituloSecao("A minha área", null));
                        }
                        add(gridLayout);
                }
        }

        // =====================================================================
        // DASHBOARD DO ADMIN
        // =====================================================================

        private Div construirDashboardAdmin(Studio studio, String firstName, List<Turma> turmas,
                        List<Aula> todasAulas, List<Mensalidade> todasMensalidades, List<Aluno> todosAlunos,
                        List<Presenca> todasPresencas, List<MarcacaoSala> marcacoesHoje, YearMonth mesAtual,
                        LocalDate hoje) {

                // Cada estúdio escolhe, na Gestão de Estúdios (SuperAdmin), que KPIs mostrar aqui.
                Predicate<DashboardCard> cardAtivo = c -> studio == null || studio.hasDashboardCard(c);
                Predicate<StudioModulo> moduloAtivo = m -> studio == null || studio.hasModulo(m);

                // --- Dados partilhados (calculados uma só vez) ---
                Map<Long, Integer> inscritosPorTurma = new HashMap<>();
                Map<Long, Set<Long>> alunosPorTurma = new HashMap<>();
                for (Turma t : turmas) {
                        List<AlunoTurma> ats = alunoTurmaRepository.findByTurma(t);
                        inscritosPorTurma.put(t.getId(), ats.size());
                        alunosPorTurma.put(t.getId(), ats.stream()
                                        .filter(at -> at.getAluno() != null)
                                        .map(at -> at.getAluno().getId())
                                        .collect(Collectors.toSet()));
                }

                Map<Long, LocalDate> ultimaPresenca = new HashMap<>();
                for (Presenca p : todasPresencas) {
                        if (p.getAluno() != null && p.getData() != null) {
                                ultimaPresenca.merge(p.getAluno().getId(), p.getData(),
                                                (a, b) -> a.isAfter(b) ? a : b);
                        }
                }

                LocalDate limiteRisco = hoje.minusDays(DIAS_SEM_PRESENCA_RISCO);
                List<Aluno> alunosRisco = todosAlunos.stream().filter(Aluno::isAtivo).filter(a -> {
                        LocalDate ultima = ultimaPresenca.get(a.getId());
                        return ultima == null || ultima.isBefore(limiteRisco);
                }).toList();

                List<Aluno> segurosExpirados = todosAlunos.stream().filter(Aluno::isAtivo)
                                .filter(a -> a.getDataExpiracaoSeguro() != null
                                                && a.getDataExpiracaoSeguro().isBefore(hoje))
                                .toList();

                List<Aluno> aniversariantes = todosAlunos.stream().filter(a -> a.getDataNascimento() != null
                                && a.getDataNascimento().getMonth() == hoje.getMonth()
                                && a.getDataNascimento().getDayOfMonth() == hoje.getDayOfMonth()).toList();

                // Novos alunos: mesma regra de sempre — inscrição/renovação no período e sem presenças
                // antes do início do período (senão é só a renovação de um aluno já existente).
                // A contagem do ano letivo arranca a 1 de agosto (época de inscrições).
                LocalDate inicioMes = mesAtual.atDay(1);
                LocalDate inicioContagemNovosAlunos = LocalDate.of(
                                hoje.getMonthValue() >= 8 ? hoje.getYear() : hoje.getYear() - 1, 8, 1);
                LocalDate inicioAnoLetivo = LocalDate.of(
                                hoje.getMonthValue() >= 9 ? hoje.getYear() : hoje.getYear() - 1, 9, 1);
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
                List<Aluno> novosAnoLetivo = todosAlunos.stream().filter(a -> a.getDataInscricaoRenovacao() != null
                                && !a.getDataInscricaoRenovacao().isBefore(inicioContagemNovosAlunos)
                                && !alunosComPresencaAntesDaContagemAnoLetivo.contains(a.getId())).toList();

                // Mensalidades: estado efetivo (FATURADO com data-limite ultrapassada = EM_DIVIDA),
                // tal como na vista de Mensalidades.
                ResumoFinanceiro fin = resumirMensalidades(todasMensalidades, studio, mesAtual);

                String nomeMesAtual = nomeMes(mesAtual.getMonth());

                Div dash = new Div();
                dash.addClassName("cf-dash");

                // --- 1. Cabeçalho ---
                dash.add(criarCabecalho(studio, firstName, hoje, moduloAtivo));

                // --- 2. O que exige ação hoje + o dia no estúdio ---
                Div topo = new Div();
                topo.addClassNames("cf-dash-split", "cf-dash-split--top");
                topo.add(criarPainelAtencao(studio, todosAlunos, fin, aniversariantes, segurosExpirados,
                                alunosRisco, ultimaPresenca, cardAtivo, moduloAtivo));
                topo.add(criarPainelHoje(studio, todasAulas, marcacoesHoje, inscritosPorTurma, hoje, moduloAtivo));
                dash.add(topo);

                if (studio != null) {
                        dash.add(criarPainelExperimentaisSemana(studio));
                }

                // --- 3. Saúde do negócio ---
                List<Component> principais = new ArrayList<>();

                if (cardAtivo.test(DashboardCard.ALUNOS_ATIVOS)) {
                        long ativos = todosAlunos.stream().filter(Aluno::isAtivo).count();
                        Div c = kpi("Alunos ativos", formatarInteiro(ativos), "info", VaadinIcon.USERS);
                        Div meta = metaKpi();
                        if (!novosMes.isEmpty()) {
                                meta.add(pill("+" + novosMes.size() + " em " + nomeMesAtual, "success", VaadinIcon.ARROW_UP));
                        } else {
                                meta.add(new Span("Sem novos alunos em " + nomeMesAtual));
                        }
                        c.add(meta);
                        tornarAcionavel(c, "link", "Alunos ativos: " + ativos + ". Abrir lista de alunos",
                                        () -> navegar("alunos"));
                        principais.add(c);
                }

                if (cardAtivo.test(DashboardCard.PREVISAO_MENSAL)) {
                        Div c = kpi("Receita prevista · " + nomeMesAtual, euros(fin.previstoMes), "violet",
                                        VaadinIcon.TRENDING_UP);
                        c.getElement().setAttribute("title", euros2(fin.previstoMes));
                        Div meta = metaKpi();
                        String mesAnt = nomeMes(mesAtual.minusMonths(1).getMonth());
                        if (fin.previstoMesAnterior > 0) {
                                double delta = (fin.previstoMes - fin.previstoMesAnterior) / fin.previstoMesAnterior * 100.0;
                                long deltaArred = Math.round(delta);
                                if (deltaArred == 0) {
                                        meta.add(pill("Igual a " + mesAnt, "neutral", null));
                                } else {
                                        meta.add(pill((deltaArred > 0 ? "+" : "−") + Math.abs(deltaArred) + "% vs " + mesAnt,
                                                        deltaArred > 0 ? "success" : "warning",
                                                        deltaArred > 0 ? VaadinIcon.ARROW_UP : VaadinIcon.ARROW_DOWN));
                                }
                        } else {
                                meta.add(new Span("Sem dados de " + mesAnt + " para comparar"));
                        }
                        c.add(meta);
                        tornarAcionavel(c, "button", "Receita prevista: " + euros(fin.previstoMes) + ". Ver por turma",
                                        () -> abrirModalPrevisaoPorTurma(turmas, todasMensalidades, mesAtual));
                        principais.add(c);
                }

                if (cardAtivo.test(DashboardCard.TOTAL_PAGO)) {
                        Div c = kpi("Cobrado · " + nomeMesAtual, euros(fin.pagoMes), "success", VaadinIcon.CHECK_CIRCLE);
                        c.getElement().setAttribute("title", euros2(fin.pagoMes));
                        if (fin.previstoMes > 0) {
                                double pct = fin.pagoMes / fin.previstoMes;
                                c.add(barraProgresso(pct, "success", "Percentagem cobrada do previsto"));
                                Div meta = metaKpi();
                                meta.add(new Span(Math.round(pct * 100) + "% do previsto"));
                                c.add(meta);
                        } else {
                                Div meta = metaKpi();
                                meta.add(new Span("Ainda sem mensalidades em " + nomeMesAtual));
                                c.add(meta);
                        }
                        if (moduloAtivo.test(StudioModulo.MENSALIDADES)) {
                                tornarAcionavel(c, "link", "Cobrado este mês: " + euros(fin.pagoMes) + ". Abrir Mensalidades",
                                                () -> navegar("mensalidade"));
                        }
                        principais.add(c);
                }

                boolean mostraDividaTotal = cardAtivo.test(DashboardCard.DIVIDA_TOTAL);
                boolean mostraDividaMes = cardAtivo.test(DashboardCard.DIVIDA_MES);
                if (mostraDividaTotal || mostraDividaMes) {
                        double valor = mostraDividaTotal ? fin.porCobrarTotal : fin.porCobrarMes;
                        String titulo = mostraDividaTotal ? "Por cobrar" : "Por cobrar · " + nomeMesAtual;
                        Div c = kpi(titulo, euros(valor), fin.emAtraso > 0 ? "error" : "neutral", VaadinIcon.WALLET);
                        c.getElement().setAttribute("title", euros2(valor));
                        Div meta = metaKpi();
                        if (fin.emAtraso > 0) {
                                meta.add(pill(euros(fin.emAtraso) + " em atraso", "error", VaadinIcon.EXCLAMATION_CIRCLE_O));
                        } else if (valor > 0) {
                                meta.add(pill("Nada em atraso", "success", VaadinIcon.CHECK));
                        } else {
                                meta.add(new Span("Tudo pago"));
                        }
                        if (mostraDividaTotal && mostraDividaMes) {
                                meta.add(new Span(nomeMesAtual + ": " + euros(fin.porCobrarMes)));
                        }
                        c.add(meta);
                        tornarAcionavel(c, "button", titulo + ": " + euros(valor) + ". Ver por mês",
                                        () -> abrirModalPorCobrar(todasMensalidades, studio));
                        principais.add(c);
                }

                if (cardAtivo.test(DashboardCard.ASSIDUIDADE_GLOBAL)) {
                        long tP = todasPresencas.stream().filter(
                                        p -> p.getData() != null && YearMonth.from(p.getData()).equals(mesAtual))
                                        .count();
                        double pA = calcularPercentagemAssiduidade(turmas, todasAulas, inscritosPorTurma, tP, mesAtual);
                        String tom = pA >= 75 ? "success" : pA >= 50 ? "warning" : "error";
                        Div c = kpi("Assiduidade · " + nomeMesAtual, String.format(PT, "%.0f%%", pA), tom,
                                        VaadinIcon.CHECK_SQUARE_O);
                        c.add(barraProgresso(pA / 100.0, tom, "Assiduidade do mês"));
                        Div meta = metaKpi();
                        meta.add(new Span(formatarInteiro(tP) + (tP == 1 ? " presença registada" : " presenças registadas")));
                        c.add(meta);
                        tornarAcionavel(c, "button", "Assiduidade: " + String.format(PT, "%.0f%%", pA) + ". Ver por turma",
                                        () -> abrirModalAssiduidadePorTurma(turmas, todasAulas, todasPresencas,
                                                        alunosPorTurma, mesAtual));
                        principais.add(c);
                }

                List<Component> secundarios = new ArrayList<>();

                if (cardAtivo.test(DashboardCard.NOVOS_ALUNOS)) {
                        Div cMes = kpi("Novos alunos · " + nomeMesAtual, formatarInteiro(novosMes.size()), "warning",
                                        VaadinIcon.STAR);
                        tornarAcionavel(cMes, "button", "Novos alunos em " + nomeMesAtual + ": " + novosMes.size(),
                                        () -> abrirModalNovosAlunos(novosMes, "Novos alunos — " + nomeMesAtual));
                        secundarios.add(cMes);

                        Div cAno = kpi("Novos alunos · ano letivo", formatarInteiro(novosAnoLetivo.size()), "warning",
                                        VaadinIcon.CALENDAR);
                        Div meta = metaKpi();
                        meta.add(new Span("Desde " + inicioContagemNovosAlunos.format(DIA_MES)));
                        cAno.add(meta);
                        tornarAcionavel(cAno, "button", "Novos alunos no ano letivo: " + novosAnoLetivo.size(),
                                        () -> abrirModalNovosAlunos(novosAnoLetivo, "Novos alunos — ano letivo"));
                        secundarios.add(cAno);
                }

                if (studio != null && cardAtivo.test(DashboardCard.RENOVACOES)) {
                        long concluidas = dashboardService.countRenovacoesConcluidasAnoLetivo(todosAlunos, inicioAnoLetivo);
                        long elegiveis = dashboardService.countElegiveisRenovacaoAnoLetivo(todosAlunos, inicioAnoLetivo);
                        double pct = elegiveis > 0 ? Math.min(1.0, (double) concluidas / elegiveis) : 0.0;
                        Div c = kpi("Renovações · ano letivo", elegiveis > 0 ? Math.round(pct * 100) + "%" : "—", "info",
                                        VaadinIcon.REFRESH);
                        c.add(barraProgresso(pct, "info", "Renovações concluídas"));
                        Div meta = metaKpi();
                        meta.add(new Span(concluidas + " de " + elegiveis + " alunos elegíveis já renovaram"));
                        c.add(meta);
                        tornarAcionavel(c, "button", "Renovações: " + concluidas + " de " + elegiveis
                                        + ". Ver pedidos por validar", () -> abrirModalRenovacoesPendentes(todosAlunos));
                        secundarios.add(c);
                }

                if (cardAtivo.test(DashboardCard.RENTABILIDADE)) {
                        Map<String, Double> lucros = calcularRentabilidadeMap(turmas, todasMensalidades, todasAulas,
                                        mesAtual);
                        double totalL = lucros.values().stream().mapToDouble(v -> v).sum();
                        Div c = kpi("Rentabilidade · " + nomeMesAtual, euros(totalL), totalL >= 0 ? "success" : "error",
                                        VaadinIcon.CHART_LINE);
                        Div meta = metaKpi();
                        meta.add(new Span("Receita menos custo dos professores"));
                        c.add(meta);
                        tornarAcionavel(c, "button", "Rentabilidade: " + euros(totalL) + ". Ver por turma",
                                        () -> abrirModalRentabilidadeDetalhada(lucros, turmas, todasMensalidades, mesAtual));
                        secundarios.add(c);
                }

                if (!principais.isEmpty() || !secundarios.isEmpty()) {
                        dash.add(tituloSecao("Saúde do negócio", capitalizar(nomeMesAtual) + " de " + mesAtual.getYear()));
                        if (!principais.isEmpty()) {
                                dash.add(linhaKpis(principais));
                        }
                        if (!secundarios.isEmpty()) {
                                Div linha = linhaKpis(secundarios);
                                linha.addClassName("cf-dash-kpis--compact");
                                dash.add(linha);
                        }
                }

                // --- 4. Tendências ---
                List<Component> tendencias = new ArrayList<>();
                if (cardAtivo.test(DashboardCard.TOTAL_PAGO) || cardAtivo.test(DashboardCard.PREVISAO_MENSAL)) {
                        tendencias.add(criarPainelReceita(todasMensalidades, mesAtual, moduloAtivo));
                }
                if (cardAtivo.test(DashboardCard.MODALIDADES)) {
                        tendencias.add(criarPainelModalidades(turmas, inscritosPorTurma));
                }
                if (cardAtivo.test(DashboardCard.ALUNOS_ATIVOS)) {
                        tendencias.add(criarPainelTurmas(turmas, inscritosPorTurma));
                }
                if (!tendencias.isEmpty()) {
                        dash.add(tituloSecao("Tendências", null));
                        Div grelha = new Div();
                        grelha.addClassNames("cf-dash-split", "cf-dash-split--trio");
                        tendencias.forEach(grelha::add);
                        dash.add(grelha);
                }

                return dash;
        }

        // --- Cabeçalho ---

        private Component criarCabecalho(Studio studio, String firstName, LocalDate hoje,
                        Predicate<StudioModulo> moduloAtivo) {
                Div header = new Div();
                header.addClassNames("cf-page-header", "cf-dash-header");

                Div ola = new Div();
                ola.addClassName("cf-dash-hello");
                if (studio != null && studio.getLogoPath() != null && !studio.getLogoPath().isBlank()) {
                        Image logo = new Image(LogoUrl.comVersao(studio.getLogoPath()), "Logótipo " + studio.getNome());
                        logo.addClassName("cf-dash-logo");
                        ola.add(logo);
                }
                Div textos = new Div();
                textos.addClassName("cf-dash-hello-text");
                H2 titulo = new H2(saudacaoPeloRelogio()
                                + (firstName != null && !firstName.isBlank() ? ", " + firstName : ""));
                Span sub = new Span(capitalizar(hoje.format(DIA_LONGO))
                                + (studio != null && studio.getNome() != null ? " · " + studio.getNome() : ""));
                sub.addClassName("cf-page-subtitle");
                textos.add(titulo, sub);
                ola.add(textos);

                Div acoes = new Div();
                acoes.addClassName("cf-dash-actions");
                acoes.add(ViewUtils.botaoNovo("Novo aluno", e -> getUI().ifPresent(ui -> ui.navigate("alunos",
                                com.vaadin.flow.router.QueryParameters.of("novo", "1")))));
                if (studio != null) {
                        acoes.add(new Button("Aula experimental", VaadinIcon.MAGIC.create(),
                                        e -> new pt.studioflow.view.component.AulaExperimentalDialog(
                                                        aulaExperimentalService, studio, ae -> reconstruirDepois()).open()));
                }
                if (moduloAtivo.test(StudioModulo.COMUNICACAO)) {
                        acoes.add(new Button("Comunicação", VaadinIcon.CHAT.create(), e -> navegar("comunicacao")));
                }

                header.add(ola, acoes);
                return header;
        }

        // --- Precisa de atenção ---

        /** Item da lista "Precisa de atenção". {@code link} = navega para outra vista (senão abre um diálogo). */
        private record Tarefa(VaadinIcon icone, String tom, String titulo, String detalhe, String valor,
                        boolean link, Runnable acao) {
        }

        private Component criarPainelAtencao(Studio studio, List<Aluno> todosAlunos, ResumoFinanceiro fin,
                        List<Aluno> aniversariantes, List<Aluno> segurosExpirados, List<Aluno> alunosRisco,
                        Map<Long, LocalDate> ultimaPresenca, Predicate<DashboardCard> cardAtivo,
                        Predicate<StudioModulo> moduloAtivo) {
                List<Tarefa> tarefas = new ArrayList<>();

                // 1. Inscrições e renovações à espera de resposta
                if (moduloAtivo.test(StudioModulo.INSCRICOES)) {
                        long novas = todosAlunos.stream()
                                        .filter(a -> a.getStatus() == Aluno.AlunoStatus.PENDENTE && !a.isPedidoRenovacao())
                                        .count();
                        long renov = dashboardService.countRenovacoesPendentes(todosAlunos);
                        long total = novas + renov;
                        if (total > 0) {
                                List<String> partes = new ArrayList<>();
                                if (novas > 0) partes.add(plural(novas, "inscrição nova", "inscrições novas"));
                                if (renov > 0) partes.add(plural(renov, "renovação", "renovações"));
                                tarefas.add(new Tarefa(VaadinIcon.USER_CHECK, "warning",
                                                plural(total, "pedido por validar", "pedidos por validar"),
                                                String.join(" · ", partes), null, true,
                                                () -> navegar("validar-inscricoes")));
                        }
                }

                // 2. Pedidos de sala dos professores
                if (moduloAtivo.test(StudioModulo.MAPA_SALAS)) {
                        List<MarcacaoSala> pedidos = (studio != null
                                        ? marcacaoSalaRepository.findByStudioAndStatus(studio, "PENDENTE")
                                        : marcacaoSalaRepository.findAll().stream()
                                                        .filter(m -> "PENDENTE".equalsIgnoreCase(m.getStatus()))
                                                        .toList())
                                        .stream()
                                        .sorted(Comparator.comparing(MarcacaoSala::getData,
                                                        Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                                                        .thenComparing(MarcacaoSala::getHoraInicio,
                                                                        Comparator.nullsLast(Comparator.<LocalTime>naturalOrder())))
                                        .toList();
                        if (!pedidos.isEmpty()) {
                                MarcacaoSala prox = pedidos.get(0);
                                String detalhe = null;
                                if (prox.getData() != null) {
                                        detalhe = "Mais próximo: " + prox.getData().format(DIA_MES)
                                                        + (prox.getHoraInicio() != null ? ", " + prox.getHoraInicio().format(HORA) : "")
                                                        + (prox.getProfessor() != null && !prox.getProfessor().isBlank()
                                                                        ? " · " + prox.getProfessor() : "");
                                }
                                tarefas.add(new Tarefa(VaadinIcon.CALENDAR_CLOCK, "info",
                                                plural(pedidos.size(), "pedido de sala por aprovar", "pedidos de sala por aprovar"),
                                                detalhe, null, true, () -> navegar("horario-salas")));
                        }
                }

                // 3. Mensalidades em atraso
                if ((cardAtivo.test(DashboardCard.DIVIDA_MES) || cardAtivo.test(DashboardCard.DIVIDA_TOTAL))
                                && fin.emAtraso > 0) {
                        String detalhe = fin.maisAntigaEmAtraso != null
                                        ? "A mais antiga é de " + nomeMes(fin.maisAntigaEmAtraso.getMonth()) + " de "
                                                        + fin.maisAntigaEmAtraso.getYear()
                                        : null;
                        boolean temVista = moduloAtivo.test(StudioModulo.MENSALIDADES);
                        tarefas.add(new Tarefa(VaadinIcon.WALLET, "error",
                                        plural(fin.alunosEmAtraso, "aluno com mensalidades em atraso",
                                                        "alunos com mensalidades em atraso"),
                                        detalhe, euros(fin.emAtraso), temVista,
                                        temVista ? () -> navegar("mensalidade")
                                                        : () -> abrirModalPorCobrar(fin.mensalidades, studio)));
                }

                // 4. Aniversários de hoje
                if (!aniversariantes.isEmpty()) {
                        String nomes = aniversariantes.stream().limit(4).map(this::primeiroNome)
                                        .collect(Collectors.joining(", "))
                                        + (aniversariantes.size() > 4 ? " e mais " + (aniversariantes.size() - 4) : "");
                        boolean temVista = moduloAtivo.test(StudioModulo.ANIVERSARIOS);
                        tarefas.add(new Tarefa(VaadinIcon.GIFT, "violet",
                                        aniversariantes.size() == 1 ? "1 aniversário hoje"
                                                        : aniversariantes.size() + " aniversários hoje",
                                        nomes, null, temVista,
                                        temVista ? () -> navegar("crm")
                                                        : () -> abrirModalListaAlunos("Aniversários hoje", aniversariantes, null)));
                }

                // 5. Seguros desportivos expirados
                if (!segurosExpirados.isEmpty()) {
                        tarefas.add(new Tarefa(VaadinIcon.SHIELD, "error",
                                        plural(segurosExpirados.size(), "seguro expirado", "seguros expirados"),
                                        "Alunos ativos com o seguro desportivo fora de validade", null, false,
                                        () -> abrirModalSeguros(segurosExpirados)));
                }

                // 6. Lista de espera
                if (moduloAtivo.test(StudioModulo.LISTA_ESPERA)) {
                        List<ListaEspera> aguardam = (studio != null
                                        ? listaEsperaRepository.findByStudioOrderByDataInscricaoAsc(studio)
                                        : listaEsperaRepository.findAll()).stream()
                                        .filter(l -> l.getEstado() == ListaEspera.Estado.AGUARDA)
                                        .toList();
                        if (!aguardam.isEmpty()) {
                                long nTurmas = aguardam.stream().filter(l -> l.getTurma() != null)
                                                .map(l -> l.getTurma().getId()).distinct().count();
                                tarefas.add(new Tarefa(VaadinIcon.HOURGLASS, "info",
                                                plural(aguardam.size(), "pessoa na lista de espera", "pessoas na lista de espera"),
                                                nTurmas > 0 ? (nTurmas == 1 ? "Para 1 turma" : "Para " + nTurmas + " turmas") : null,
                                                null, true, () -> navegar("lista-espera")));
                        }
                }

                // 7. Alunos em risco (só faz sentido se o estúdio regista presenças)
                if (cardAtivo.test(DashboardCard.ALUNOS_RISCO) && moduloAtivo.test(StudioModulo.PRESENCAS)
                                && !alunosRisco.isEmpty()) {
                        tarefas.add(new Tarefa(VaadinIcon.TRENDING_DOWN, "warning",
                                        plural(alunosRisco.size(), "aluno em risco", "alunos em risco"),
                                        "Sem presenças nos últimos " + DIAS_SEM_PRESENCA_RISCO + " dias", null, false,
                                        () -> abrirModalAlunosRisco(alunosRisco, ultimaPresenca)));
                }

                Span contador = tarefas.isEmpty()
                                ? pill("Tudo em dia", "success", VaadinIcon.CHECK)
                                : pill(String.valueOf(tarefas.size()), "warning", null);
                if (!tarefas.isEmpty()) {
                        contador.getElement().setAttribute("aria-label",
                                        plural(tarefas.size(), "assunto pendente", "assuntos pendentes"));
                }

                Component corpo;
                if (tarefas.isEmpty()) {
                        corpo = estadoVazio(VaadinIcon.CHECK_CIRCLE, "success", "Está tudo em dia",
                                        "Não há pedidos, dívidas em atraso nem alertas para hoje.");
                } else {
                        Div lista = new Div();
                        lista.addClassName("cf-dash-tasks");
                        tarefas.forEach(t -> lista.add(criarLinhaTarefa(t)));
                        corpo = lista;
                }
                return criarPainel("Precisa de atenção", VaadinIcon.BELL, contador, corpo);
        }

        private Component criarLinhaTarefa(Tarefa t) {
                Div linha = new Div();
                linha.addClassNames("cf-dash-task", "cf-dash-task--" + t.tom());

                Icon icone = t.icone().create();
                icone.addClassName("cf-dash-task-icon");
                icone.getElement().setAttribute("aria-hidden", "true");

                Div corpo = new Div();
                corpo.addClassName("cf-dash-task-body");
                Span titulo = new Span(t.titulo());
                titulo.addClassName("cf-dash-task-title");
                corpo.add(titulo);
                if (t.detalhe() != null && !t.detalhe().isBlank()) {
                        Span detalhe = new Span(t.detalhe());
                        detalhe.addClassName("cf-dash-task-sub");
                        corpo.add(detalhe);
                }
                linha.add(icone, corpo);

                if (t.valor() != null) {
                        Span valor = new Span(t.valor());
                        valor.addClassName("cf-dash-task-value");
                        linha.add(valor);
                }
                if (t.acao() != null) {
                        Icon seta = VaadinIcon.ANGLE_RIGHT.create();
                        seta.addClassName("cf-dash-chevron");
                        seta.getElement().setAttribute("aria-hidden", "true");
                        linha.add(seta);
                        String rotulo = t.titulo() + (t.valor() != null ? ", " + t.valor() : "")
                                        + (t.detalhe() != null ? ". " + t.detalhe() : "");
                        tornarAcionavel(linha, t.link() ? "link" : "button", rotulo, t.acao());
                }
                return linha;
        }

        // --- Hoje no estúdio ---

        /** Linha da agenda de hoje (aula regular ou marcação de sala). */
        private record LinhaHoje(LocalTime inicio, LocalTime fim, String titulo, String meta, String cor,
                        boolean marcacao, boolean cancelada, boolean pendente, String etiqueta, String rota) {
        }

        private Component criarPainelHoje(Studio studio, List<Aula> todasAulas, List<MarcacaoSala> marcacoesHoje,
                        Map<Long, Integer> inscritosPorTurma, LocalDate hoje, Predicate<StudioModulo> moduloAtivo) {

                InterrupcaoLetiva interrupcao = null;
                List<OcorrenciaAula> ocorrencias = List.of();
                if (studio != null) {
                        interrupcao = interrupcaoLetivaRepository.findByStudioOrderByDataInicioAsc(studio).stream()
                                        .filter(i -> i.getDataInicio() != null && !hoje.isBefore(i.getDataInicio())
                                                        && (i.getDataFim() == null || !hoje.isAfter(i.getDataFim())))
                                        .findFirst().orElse(null);
                        ocorrencias = ocorrenciaAulaRepository.findByStudioAndDataBetweenOrderByDataAsc(studio, hoje, hoje);
                }

                String rotaAulas = moduloAtivo.test(StudioModulo.PRESENCAS) ? "presencas" : "horario";
                String rotaMarcacoes = moduloAtivo.test(StudioModulo.MAPA_SALAS) ? "horario-salas" : null;

                List<LinhaHoje> linhas = new ArrayList<>();
                if (interrupcao == null) {
                        for (Aula a : todasAulas) {
                                Turma t = a.getTurma();
                                if (t == null || !t.isAtivo() || a.getDia() != hoje.getDayOfWeek() || a.getHoraInicio() == null)
                                        continue;
                                if (a.getDataInicio() != null && hoje.isBefore(a.getDataInicio())) continue;
                                if (a.getDataFim() != null && hoje.isAfter(a.getDataFim())) continue;

                                OcorrenciaAula oc = ocorrencias.stream()
                                                .filter(o -> o.getTipo() != OcorrenciaAula.Tipo.REPOSICAO
                                                                && o.getTurma() != null
                                                                && o.getTurma().getId().equals(t.getId()))
                                                .findFirst().orElse(null);
                                boolean cancelada = oc != null && oc.getTipo() == OcorrenciaAula.Tipo.CANCELAMENTO;
                                boolean substituida = oc != null && oc.getTipo() == OcorrenciaAula.Tipo.SUBSTITUICAO;

                                List<String> meta = new ArrayList<>();
                                if (substituida) {
                                        meta.add("Substituição: " + (oc.getProfessorSubstituto() != null
                                                        ? oc.getProfessorSubstituto().getNome() : "por definir"));
                                } else if (a.getProfessorEfetivo() != null) {
                                        meta.add(a.getProfessorEfetivo().getNome());
                                }
                                Sala sala = a.getSalaEfetiva();
                                if (sala != null && sala.getNome() != null) meta.add(sala.getNome());
                                int inscritos = inscritosPorTurma.getOrDefault(t.getId(), 0);
                                meta.add(plural(inscritos, "aluno", "alunos"));

                                linhas.add(new LinhaHoje(a.getHoraInicio(), a.getHoraFim(), t.getDescricao(),
                                                String.join(" · ", meta), t.getCor(), false, cancelada, false,
                                                substituida ? "Substituição" : null, rotaAulas));
                        }
                }

                for (MarcacaoSala m : marcacoesHoje) {
                        if (m.getHoraInicio() == null) continue;
                        String tipo = m.getTipo() != null ? m.getTipo() : "";
                        String titulo = m.getTurma() != null ? m.getTurma().getDescricao()
                                        : "ENSAIO".equalsIgnoreCase(tipo) ? "Ensaio" : "Aula privada";
                        List<String> meta = new ArrayList<>();
                        if ("PRIVADA".equalsIgnoreCase(tipo)) meta.add("Privada");
                        else if ("ENSAIO".equalsIgnoreCase(tipo) && m.getTurma() != null) meta.add("Ensaio");
                        if (m.getProfessor() != null && !m.getProfessor().isBlank()) meta.add(m.getProfessor());
                        if (m.getSala() != null && m.getSala().getNome() != null) meta.add(m.getSala().getNome());
                        String nomes = extrairNomesCurtos(m.getAlunos());
                        if (!nomes.isBlank()) meta.add(nomes);
                        boolean pendente = "PENDENTE".equalsIgnoreCase(m.getStatus());
                        linhas.add(new LinhaHoje(m.getHoraInicio(), m.getHoraFim(), titulo, String.join(" · ", meta),
                                        null, true, false, pendente,
                                        "ENSAIO".equalsIgnoreCase(tipo) ? "Ensaio" : "Privada", rotaMarcacoes));
                }

                linhas.sort(Comparator.comparing(LinhaHoje::inicio));

                long naoCanceladas = linhas.stream().filter(l -> !l.cancelada()).count();
                Span contador = pill(plural(naoCanceladas, "aula", "aulas"), "neutral", null);

                Div corpo = new Div();
                corpo.addClassName("cf-dash-today");
                if (interrupcao != null) {
                        Div aviso = new Div();
                        aviso.addClassNames("cf-callout", "cf-callout--info");
                        Icon ic = VaadinIcon.INFO_CIRCLE_O.create();
                        ic.getElement().setAttribute("aria-hidden", "true");
                        aviso.add(ic, new Span("Interrupção letiva"
                                        + (interrupcao.getDescricao() != null && !interrupcao.getDescricao().isBlank()
                                                        ? ": " + interrupcao.getDescricao() : "")
                                        + ". Não há aulas regulares hoje."));
                        corpo.add(aviso);
                }

                if (linhas.isEmpty()) {
                        if (interrupcao == null) {
                                corpo.add(estadoVazio(VaadinIcon.CALENDAR_O, "neutral", "Sem aulas hoje",
                                                "Não há aulas regulares nem marcações de sala para hoje."));
                        }
                } else {
                        LocalTime agora = LocalTime.now();
                        Div lista = new Div();
                        lista.addClassName("cf-dash-aulas");
                        boolean proximaMarcada = false;
                        for (LinhaHoje l : linhas) {
                                // Sem hora de fim registada, assume-se 1 hora de aula
                                LocalTime fim = l.fim() != null ? l.fim() : l.inicio().plusHours(1);
                                boolean terminada = !agora.isBefore(fim);
                                boolean aDecorrer = !terminada && !agora.isBefore(l.inicio());
                                boolean proxima = false;
                                if (!l.cancelada() && !terminada && !aDecorrer && !proximaMarcada) {
                                        proxima = true;
                                        proximaMarcada = true;
                                }
                                lista.add(criarLinhaHoje(l, terminada, aDecorrer, proxima));
                        }
                        corpo.add(lista);
                }

                Div card = criarPainel("Hoje no estúdio", VaadinIcon.CALENDAR, contador, corpo);
                Button verHorario = new Button("Ver horário da semana", VaadinIcon.ARROW_RIGHT.create(),
                                e -> navegar("horario"));
                verHorario.setIconAfterText(true);
                verHorario.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                verHorario.addClassName("cf-dash-more");
                card.add(verHorario);
                return card;
        }

        /** Painel do admin: aulas experimentais da semana atual (todas as turmas). */
        private Component criarPainelExperimentaisSemana(Studio studio) {
                List<pt.studioflow.model.AulaExperimental> semana = aulaExperimentalService.daSemana(studio, null);
                Span contador = pill(semana.isEmpty() ? "nenhuma" : plural(semana.size(), "marcada", "marcadas"),
                                semana.isEmpty() ? "neutral" : "violet", null);
                Component corpo = semana.isEmpty()
                                ? estadoVazio(VaadinIcon.MAGIC, "neutral", "Sem aulas experimentais esta semana",
                                                "Usa o botão \"Aula experimental\" para registar um contacto.")
                                : listaExperimentaisSemana(semana);
                Div card = criarPainel("Aulas experimentais esta semana", VaadinIcon.MAGIC, contador, corpo);
                Button ver = new Button("Ver todas", VaadinIcon.ARROW_RIGHT.create(), e -> navegar("aulas-experimentais"));
                ver.setIconAfterText(true);
                ver.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                ver.addClassName("cf-dash-more");
                card.add(ver);
                return card;
        }

        /** Lista das aulas experimentais agrupada por dia (hoje em destaque). */
        private Component listaExperimentaisSemana(List<pt.studioflow.model.AulaExperimental> semana) {
                LocalDate hoje = LocalDate.now();
                Locale lpt = Locale.forLanguageTag("pt-PT");
                java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", lpt);
                Div lista = new Div();
                lista.addClassName("cf-exp-semana");
                java.util.Map<LocalDate, List<pt.studioflow.model.AulaExperimental>> porDia = new java.util.TreeMap<>(
                                semana.stream().collect(Collectors.groupingBy(pt.studioflow.model.AulaExperimental::getData)));
                porDia.forEach((dia, aulas) -> {
                        Div grupo = new Div();
                        grupo.addClassName("cf-exp-semana-dia");
                        if (dia.equals(hoje)) grupo.addClassName("cf-exp-semana-dia--hoje");
                        if (dia.isBefore(hoje)) grupo.addClassName("cf-exp-semana-dia--passado");
                        Div cab = new Div();
                        cab.addClassName("cf-exp-semana-cab");
                        String nd = dia.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, lpt);
                        Span nomeDia = new Span(Character.toUpperCase(nd.charAt(0)) + nd.substring(1) + " " + dia.format(fmt));
                        nomeDia.addClassName("cf-strong");
                        cab.add(nomeDia);
                        if (dia.equals(hoje)) cab.add(pill("Hoje", "info", null));
                        grupo.add(cab);
                        for (pt.studioflow.model.AulaExperimental ae : aulas) {
                                Div linha = new Div();
                                linha.addClassName("cf-exp-semana-linha");
                                Span hora = new Span(ae.getHoraInicio() != null ? ae.getHoraInicio().toString() : "");
                                hora.addClassName("cf-exp-semana-hora");
                                Span quem = new Span(ae.getNomeCompleto());
                                quem.addClassName("cf-strong");
                                Span turma = new Span(ae.getTurma().getDescricao()
                                                + (ae.getTelemovel() != null ? " · " + ae.getTelemovel() : ""));
                                turma.addClassName("cf-cell-sub");
                                Div txt = new Div(quem, turma);
                                txt.addClassName("cf-exp-semana-txt");
                                linha.add(hora, txt);
                                if (ae.getEstado() != pt.studioflow.model.AulaExperimental.Estado.MARCADA) {
                                        String tom = switch (ae.getEstado()) {
                                                case REALIZADA -> "success";
                                                case INSCRITO -> "violet";
                                                case FALTOU -> "warning";
                                                default -> "neutral";
                                        };
                                        linha.add(pill(ae.getEstado().getLabel(), tom, null));
                                }
                                grupo.add(linha);
                        }
                        lista.add(grupo);
                });
                return lista;
        }

        private Component criarLinhaHoje(LinhaHoje l, boolean terminada, boolean aDecorrer, boolean proxima) {
                Div linha = new Div();
                linha.addClassName("cf-dash-aula");
                if (l.marcacao()) linha.addClassName("cf-dash-aula--marcacao");
                String cor = corSegura(l.cor());
                if (cor != null) {
                        // Cor da turma (dado, não tema)
                        linha.getStyle().set("--cf-accent", cor);
                }

                Div hora = new Div();
                hora.addClassName("cf-dash-aula-hora");
                Span ini = new Span(l.inicio().format(HORA));
                ini.addClassName("cf-dash-aula-ini");
                hora.add(ini);
                if (l.fim() != null) {
                        Span fim = new Span(l.fim().format(HORA));
                        fim.addClassName("cf-dash-aula-fim");
                        hora.add(fim);
                }

                Div barra = new Div();
                barra.addClassName("cf-dash-aula-bar");
                barra.getElement().setAttribute("aria-hidden", "true");

                Div corpo = new Div();
                corpo.addClassName("cf-dash-aula-body");
                Span titulo = new Span(l.titulo() != null ? l.titulo() : "—");
                titulo.addClassName("cf-dash-aula-title");
                corpo.add(titulo);
                if (l.meta() != null && !l.meta().isBlank()) {
                        Span meta = new Span(l.meta());
                        meta.addClassName("cf-dash-aula-meta");
                        corpo.add(meta);
                }

                linha.add(hora, barra, corpo);

                // Um só estado, pelo que é mais relevante agora
                String texto = null;
                String tom = null;
                if (l.cancelada()) {
                        texto = "Cancelada";
                        tom = "error";
                        linha.addClassName("cf-dash-aula--cancelada");
                } else if (l.pendente()) {
                        texto = "Por aprovar";
                        tom = "warning";
                } else if (aDecorrer) {
                        texto = "A decorrer";
                        tom = "success";
                        linha.addClassName("cf-dash-aula--agora");
                } else if (terminada) {
                        texto = "Terminada";
                        tom = "neutral";
                        linha.addClassName("cf-dash-aula--passada");
                } else if (l.etiqueta() != null && !l.marcacao()) {
                        texto = l.etiqueta();
                        tom = "warning";
                } else if (proxima) {
                        texto = "A seguir";
                        tom = "info";
                } else if (l.etiqueta() != null) {
                        texto = l.etiqueta();
                        tom = "violet";
                }
                if (texto != null) {
                        Span estado = pill(texto, tom, null);
                        estado.addClassName("cf-dash-aula-estado");
                        linha.add(estado);
                }

                if (l.rota() != null) {
                        String rotulo = l.inicio().format(HORA) + (l.fim() != null ? " às " + l.fim().format(HORA) : "")
                                        + ", " + l.titulo() + (texto != null ? ", " + texto : "");
                        tornarAcionavel(linha, "link", rotulo, () -> navegar(l.rota()));
                }
                return linha;
        }

        // --- Tendências ---

        private Component criarPainelReceita(List<Mensalidade> mensalidades, YearMonth mesAtual,
                        Predicate<StudioModulo> moduloAtivo) {
                final int n = 6;
                YearMonth primeiro = mesAtual.minusMonths(n - 1);
                double[] previsto = new double[n];
                double[] pago = new double[n];
                for (Mensalidade m : mensalidades) {
                        if (m.getMes() == null) continue;
                        YearMonth ym = YearMonth.of(m.getAno(), m.getMes());
                        if (ym.isBefore(primeiro) || ym.isAfter(mesAtual)) continue;
                        int i = (int) java.time.temporal.ChronoUnit.MONTHS.between(primeiro, ym);
                        previsto[i] += m.getValor();
                        if (m.getEstado() == EstadoMensalidade.PAGO) pago[i] += m.getValor();
                }
                double max = 0;
                for (int i = 0; i < n; i++) max = Math.max(max, Math.max(previsto[i], pago[i]));

                Component corpo;
                if (max <= 0) {
                        corpo = estadoVazio(VaadinIcon.CHART_LINE, "neutral", "Sem dados",
                                        "Ainda não há mensalidades registadas nos últimos 6 meses.");
                } else {
                        Div wrap = new Div();
                        wrap.addClassName("cf-dash-chart-wrap");

                        Div legenda = new Div();
                        legenda.addClassName("cf-dash-legend");
                        legenda.add(itemLegenda("Cobrado", "cf-dash-swatch--pago"),
                                        itemLegenda("Previsto", "cf-dash-swatch--previsto"));

                        Div grafico = new Div();
                        grafico.addClassName("cf-dash-chart");
                        for (int i = 0; i < n; i++) {
                                YearMonth ym = primeiro.plusMonths(i);
                                Div col = new Div();
                                col.addClassName("cf-dash-col");
                                if (ym.equals(mesAtual)) col.addClassName("cf-dash-col--atual");
                                col.getElement().setAttribute("role", "img");
                                String descricao = nomeMes(ym.getMonth()) + " de " + ym.getYear() + ": "
                                                + euros(pago[i]) + " cobrados de " + euros(previsto[i]) + " previstos";
                                col.getElement().setAttribute("aria-label", descricao);
                                col.getElement().setAttribute("title", descricao);

                                Span valor = new Span(eurosCompacto(pago[i]));
                                valor.addClassName("cf-dash-col-val");
                                valor.getElement().setAttribute("aria-hidden", "true");

                                Div plot = new Div();
                                plot.addClassName("cf-dash-col-plot");
                                Div track = new Div();
                                track.addClassName("cf-dash-col-track");
                                double base = Math.max(previsto[i], pago[i]);
                                track.getStyle().set("--cf-h", pct(base / max));
                                Div fill = new Div();
                                fill.addClassName("cf-dash-col-fill");
                                fill.getStyle().set("--cf-fill", pct(base > 0 ? pago[i] / base : 0));
                                track.add(fill);
                                plot.add(track);

                                Span rotulo = new Span(mesCurto(ym.getMonth()));
                                rotulo.addClassName("cf-dash-col-label");
                                rotulo.getElement().setAttribute("aria-hidden", "true");

                                col.add(valor, plot, rotulo);
                                grafico.add(col);
                        }

                        int ia = n - 1;
                        Span resumo = new Span("Em " + nomeMes(mesAtual.getMonth()) + ": " + euros(pago[ia])
                                        + " cobrados de " + euros(previsto[ia]) + " previstos.");
                        resumo.addClassName("cf-dash-chart-summary");

                        wrap.add(legenda, grafico, resumo);
                        corpo = wrap;
                }

                Div card = criarPainel("Receita · últimos 6 meses", VaadinIcon.CHART, null, corpo);
                if (moduloAtivo.test(StudioModulo.FINANCEIRO)) {
                        Button ver = new Button("Abrir Financeiro", VaadinIcon.ARROW_RIGHT.create(),
                                        e -> navegar("financeiro"));
                        ver.setIconAfterText(true);
                        ver.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                        ver.addClassName("cf-dash-more");
                        card.add(ver);
                }
                return card;
        }

        /** Linha de um ranking com barra (modalidades, turmas). */
        private record Barra(String rotulo, int valor, String cor) {
        }

        private Component criarPainelModalidades(List<Turma> turmas, Map<Long, Integer> inscritosPorTurma) {
                List<Barra> dados = dadosModalidades(turmas, inscritosPorTurma);
                Component corpo = dados.isEmpty()
                                ? estadoVazio(VaadinIcon.BAR_CHART, "neutral", "Sem turmas",
                                                "Crie turmas e associe-as a modalidades para ver a distribuição.")
                                : listaBarras(dados.stream().limit(TOP_RANKING).toList(), "inscrições");
                Div card = criarPainel("Inscrições por modalidade", VaadinIcon.BAR_CHART, null, corpo);
                if (dados.size() > TOP_RANKING) {
                        card.add(botaoVerTodas("Ver todas as modalidades",
                                        () -> abrirModalRanking("Inscrições por modalidade", dados)));
                }
                return card;
        }

        private Component criarPainelTurmas(List<Turma> turmas, Map<Long, Integer> inscritosPorTurma) {
                List<Barra> dados = turmas.stream()
                                .map(t -> new Barra(t.getDescricao(), inscritosPorTurma.getOrDefault(t.getId(), 0), t.getCor()))
                                .sorted(Comparator.comparingInt(Barra::valor).reversed())
                                .toList();
                Component corpo = dados.isEmpty()
                                ? estadoVazio(VaadinIcon.GROUP, "neutral", "Sem turmas", "Ainda não há turmas criadas.")
                                : listaBarras(dados.stream().limit(TOP_RANKING).toList(), "alunos");
                Div card = criarPainel("Turmas com mais alunos", VaadinIcon.GROUP, null, corpo);
                if (dados.size() > TOP_RANKING) {
                        card.add(botaoVerTodas("Ver todas as turmas", () -> abrirModalRanking("Alunos por turma", dados)));
                }
                return card;
        }

        private List<Barra> dadosModalidades(List<Turma> turmas, Map<Long, Integer> inscritosPorTurma) {
                Map<String, Integer> s = new HashMap<>();
                turmas.forEach(tr -> {
                        String m = tr.getModalidade() != null ? tr.getModalidade().getDescricao() : "Outra";
                        s.merge(m, inscritosPorTurma.getOrDefault(tr.getId(), 0), Integer::sum);
                });
                return s.entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                                .map(e -> new Barra(e.getKey(), e.getValue(), null))
                                .toList();
        }

        private Component listaBarras(List<Barra> dados, String unidade) {
                int max = Math.max(1, dados.stream().mapToInt(Barra::valor).max().orElse(1));
                Div lista = new Div();
                lista.addClassName("cf-dash-bars");
                for (Barra b : dados) {
                        Div linha = new Div();
                        linha.addClassName("cf-dash-bar-row");
                        String cor = corSegura(b.cor());
                        if (cor != null) {
                                linha.getStyle().set("--cf-accent", cor);
                        }
                        Span rotulo = new Span(b.rotulo() != null ? b.rotulo() : "—");
                        rotulo.addClassName("cf-dash-bar-label");
                        rotulo.getElement().setAttribute("title", b.rotulo() != null ? b.rotulo() : "");
                        Span valor = new Span(formatarInteiro(b.valor()));
                        valor.addClassName("cf-dash-bar-value");
                        valor.getElement().setAttribute("aria-label", b.valor() + " " + unidade);
                        Div track = new Div();
                        track.addClassName("cf-dash-bar-track");
                        track.getElement().setAttribute("aria-hidden", "true");
                        Div fill = new Div();
                        fill.addClassName("cf-dash-bar-fill");
                        fill.getStyle().set("--cf-w", pct((double) b.valor() / max));
                        track.add(fill);
                        linha.add(rotulo, valor, track);
                        lista.add(linha);
                }
                return lista;
        }

        private void abrirModalRanking(String titulo, List<Barra> dados) {
                Dialog d = new Dialog();
                d.setHeaderTitle(titulo);
                aplicarTamanhoPadraoModal(d);
                Div conteudo = new Div(listaBarras(dados, "inscrições"));
                conteudo.addClassName("cf-dash-dialog-body");
                d.add(conteudo);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        // =====================================================================
        // COMPONENTES E AUXILIARES DO DASHBOARD
        // =====================================================================

        /** Totais de mensalidades usados pelos KPIs e pela lista "Precisa de atenção". */
        private static final class ResumoFinanceiro {
                List<Mensalidade> mensalidades;
                double previstoMes;
                double previstoMesAnterior;
                double pagoMes;
                double porCobrarMes;
                double porCobrarTotal;
                double emAtraso;
                long alunosEmAtraso;
                YearMonth maisAntigaEmAtraso;
        }

        private ResumoFinanceiro resumirMensalidades(List<Mensalidade> mensalidades, Studio studio, YearMonth mesAtual) {
                ResumoFinanceiro r = new ResumoFinanceiro();
                r.mensalidades = mensalidades;
                YearMonth mesAnterior = mesAtual.minusMonths(1);
                Set<Long> alunosAtraso = new HashSet<>();
                for (Mensalidade m : mensalidades) {
                        if (m.getMes() == null) continue;
                        YearMonth ym = YearMonth.of(m.getAno(), m.getMes());
                        boolean doMes = ym.equals(mesAtual);
                        if (doMes) {
                                r.previstoMes += m.getValor();
                                if (m.getEstado() == EstadoMensalidade.PAGO) r.pagoMes += m.getValor();
                        } else if (ym.equals(mesAnterior)) {
                                r.previstoMesAnterior += m.getValor();
                        }
                        EstadoMensalidade efetivo = mensalidadeConfig.estadoEfetivo(m, studio);
                        if (efetivo == EstadoMensalidade.FATURADO || efetivo == EstadoMensalidade.EM_DIVIDA) {
                                double v = mensalidadeConfig.valorComMulta(m, studio);
                                r.porCobrarTotal += v;
                                if (doMes) r.porCobrarMes += v;
                                if (efetivo == EstadoMensalidade.EM_DIVIDA) {
                                        r.emAtraso += v;
                                        if (m.getAluno() != null) alunosAtraso.add(m.getAluno().getId());
                                        if (r.maisAntigaEmAtraso == null || ym.isBefore(r.maisAntigaEmAtraso)) {
                                                r.maisAntigaEmAtraso = ym;
                                        }
                                }
                        }
                }
                r.alunosEmAtraso = alunosAtraso.size();
                return r;
        }

        private Div criarPainel(String titulo, VaadinIcon icone, Component extraCabecalho, Component corpo) {
                Div card = new Div();
                card.addClassNames("cf-card", "cf-dash-panel");
                Div cabecalho = new Div();
                cabecalho.addClassName("cf-card-header");
                Icon i = icone.create();
                i.getElement().setAttribute("aria-hidden", "true");
                H3 h = new H3(titulo);
                h.addClassName("cf-dash-panel-title");
                cabecalho.add(i, h);
                if (extraCabecalho != null) cabecalho.add(extraCabecalho);
                card.add(cabecalho, corpo);
                return card;
        }

        private Component tituloSecao(String titulo, String meta) {
                Div d = new Div();
                d.addClassName("cf-dash-section");
                H3 h = new H3(titulo);
                h.addClassName("cf-dash-section-title");
                d.add(h);
                if (meta != null) {
                        Span s = new Span(meta);
                        s.addClassName("cf-dash-section-meta");
                        d.add(s);
                }
                return d;
        }

        private Div kpi(String titulo, String valor, String tom, VaadinIcon icone) {
                Div card = ViewUtils.statCardTom(titulo, new Span(valor), tom, icone, false);
                card.addClassName("cf-dash-kpi");
                return card;
        }

        private Div metaKpi() {
                Div meta = new Div();
                meta.addClassName("cf-dash-kpi-meta");
                return meta;
        }

        private Div linhaKpis(List<Component> cards) {
                Div linha = new Div();
                linha.addClassName("cf-dash-kpis");
                cards.forEach(linha::add);
                return linha;
        }

        private Span pill(String texto, String tom, VaadinIcon icone) {
                Span s = new Span();
                s.addClassNames("cf-pill", "cf-pill--" + (tom != null ? tom : "neutral"));
                if (icone != null) {
                        Icon i = icone.create();
                        i.getElement().setAttribute("aria-hidden", "true");
                        s.add(i);
                }
                s.add(new Span(texto));
                return s;
        }

        private ProgressBar barraProgresso(double valor, String tom, String rotulo) {
                ProgressBar pb = new ProgressBar();
                pb.setValue(Math.max(0.0, Math.min(1.0, valor)));
                pb.addClassNames("cf-dash-progress", "cf-dash-progress--" + tom);
                pb.getElement().setAttribute("aria-label", rotulo);
                return pb;
        }

        private Component estadoVazio(VaadinIcon icone, String tom, String titulo, String texto) {
                Div d = new Div();
                d.addClassNames("cf-empty", "cf-dash-empty");
                Icon i = icone.create();
                i.addClassNames("cf-dash-empty-icon", "cf-tone-" + tom);
                i.getElement().setAttribute("aria-hidden", "true");
                Span t = new Span(titulo);
                t.addClassName("cf-dash-empty-title");
                Span s = new Span(texto);
                s.addClassName("cf-dash-empty-text");
                d.add(i, t, s);
                return d;
        }

        private Component itemLegenda(String texto, String classeAmostra) {
                Span item = new Span();
                item.addClassName("cf-dash-legend-item");
                Span amostra = new Span();
                amostra.addClassNames("cf-dash-swatch", classeAmostra);
                amostra.getElement().setAttribute("aria-hidden", "true");
                item.add(amostra, new Span(texto));
                return item;
        }

        private Button botaoVerTodas(String texto, Runnable acao) {
                Button b = new Button(texto, VaadinIcon.ARROW_RIGHT.create(), e -> acao.run());
                b.setIconAfterText(true);
                b.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                b.addClassName("cf-dash-more");
                return b;
        }

        /**
         * Torna um bloco clicável e acessível por teclado (foco, Enter/Espaço, papel ARIA).
         * {@code papel} = "link" (navega para outra vista) ou "button" (abre um diálogo).
         */
        private void tornarAcionavel(HasElement componente, String papel, String rotulo, Runnable acao) {
                var el = componente.getElement();
                el.getClassList().add("cf-dash-link");
                el.setAttribute("role", papel);
                el.setAttribute("tabindex", "0");
                if (rotulo != null) el.setAttribute("aria-label", rotulo);
                el.addEventListener("click", e -> acao.run());
                el.addEventListener("keydown", e -> acao.run())
                                .setFilter("event.key === 'Enter' || event.key === ' '")
                                .addEventData("(event.key === 'Enter' || event.key === ' ') && event.preventDefault()");
        }

        private void navegar(String rota) {
                getUI().ifPresent(ui -> ui.navigate(rota));
        }

        private void abrirFicha(Aluno a) {
                if (a == null || a.getId() == null) return;
                getUI().ifPresent(ui -> ui.navigate("alunos", new com.vaadin.flow.router.QueryParameters(
                                Collections.singletonMap("edit", Collections.singletonList(a.getId().toString())))));
        }

        private String saudacaoPeloRelogio() {
                int hora = LocalTime.now().getHour();
                if (hora < 12) return "Bom dia";
                if (hora < 20) return "Boa tarde";
                return "Boa noite";
        }

        /** Só aceita cores hex (vêm da BD e vão para uma custom property CSS). */
        private static String corSegura(String cor) {
                if (cor == null) return null;
                String c = cor.trim();
                return c.matches("#[0-9a-fA-F]{3,8}") ? c : null;
        }

        private static String pct(double fracao) {
                double v = Math.max(0.0, Math.min(1.0, fracao)) * 100.0;
                return String.format(Locale.ROOT, "%.1f%%", v);
        }

        private static String euros(double v) {
                NumberFormat f = NumberFormat.getNumberInstance(PT);
                f.setMaximumFractionDigits(0);
                f.setMinimumFractionDigits(0);
                f.setGroupingUsed(true);
                return f.format(v) + " €";
        }

        private static String euros2(double v) {
                NumberFormat f = NumberFormat.getNumberInstance(PT);
                f.setMaximumFractionDigits(2);
                f.setMinimumFractionDigits(2);
                f.setGroupingUsed(true);
                return f.format(v) + " €";
        }

        /** Valor curto para os rótulos do gráfico (ex.: "850 €", "1,2 mil €"). */
        private static String eurosCompacto(double v) {
                if (Math.abs(v) < 1000) return euros(v);
                NumberFormat f = NumberFormat.getNumberInstance(PT);
                f.setMaximumFractionDigits(1);
                f.setMinimumFractionDigits(0);
                return f.format(v / 1000.0) + " mil €";
        }

        private static String formatarInteiro(long v) {
                NumberFormat f = NumberFormat.getIntegerInstance(PT);
                f.setGroupingUsed(true);
                return f.format(v);
        }

        private static String nomeMes(Month m) {
                return m.getDisplayName(TextStyle.FULL, PT);
        }

        private static String mesCurto(Month m) {
                return m.getDisplayName(TextStyle.SHORT, PT).replace(".", "");
        }

        private static String capitalizar(String s) {
                if (s == null || s.isEmpty()) return s;
                return s.substring(0, 1).toUpperCase(PT) + s.substring(1);
        }

        private static String plural(long n, String singular, String pluralTxt) {
                return n + " " + (n == 1 ? singular : pluralTxt);
        }

        private String primeiroNome(Aluno a) {
                String n = a.getNomeCompleto();
                if (n == null || n.isBlank()) return "—";
                return n.trim().split("\\s+")[0];
        }

        // =====================================================================
        // DIÁLOGOS DE DETALHE
        // =====================================================================

        /** Tamanho uniforme para todas as popups abertas a partir dos cards do dashboard. */
        private void aplicarTamanhoPadraoModal(Dialog d) {
                d.setWidth("900px");
                d.setMaxWidth("95vw");
                d.setHeight("80vh");
                d.setMaxHeight("90vh");
        }

        /** Lista de alunos; clicar numa linha abre a ficha do aluno. */
        private void abrirModalListaAlunos(String titulo, List<Aluno> alunos,
                        java.util.function.Consumer<Grid<Aluno>> colunasExtra) {
                Dialog d = new Dialog();
                d.setHeaderTitle(titulo);
                aplicarTamanhoPadraoModal(d);
                if (alunos.isEmpty()) {
                        d.add(estadoVazio(VaadinIcon.USERS, "neutral", "Sem alunos", "Não há alunos nesta lista."));
                } else {
                        Grid<Aluno> g = new Grid<>(Aluno.class, false);
                        g.setItems(alunos);
                        g.addColumn(Aluno::getNomeCompleto).setHeader("Nome").setSortable(true).setFlexGrow(2);
                        g.addColumn(Aluno::getTelemovel).setHeader("Telemóvel").setAutoWidth(true);
                        if (colunasExtra != null) colunasExtra.accept(g);
                        g.addItemClickListener(e -> {
                                d.close();
                                abrirFicha(e.getItem());
                        });
                        g.setSizeFull();
                        Span dica = new Span("Clique num aluno para abrir a ficha.");
                        dica.addClassNames("cf-text-xs", "cf-muted");
                        VerticalLayout v = new VerticalLayout(dica, g);
                        v.setPadding(false);
                        v.setSizeFull();
                        v.expand(g);
                        d.add(v);
                }
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private void abrirModalSeguros(List<Aluno> alunos) {
                abrirModalListaAlunos("Seguros expirados", alunos,
                                g -> g.addColumn(a -> pt.studioflow.util.DataUtil.formatar(a.getDataExpiracaoSeguro()))
                                                .setHeader("Expirou em").setAutoWidth(true).setSortable(true)
                                                .setComparator(Aluno::getDataExpiracaoSeguro));
        }

        private void abrirModalAlunosRisco(List<Aluno> alunos, Map<Long, LocalDate> ultimaPresenca) {
                abrirModalListaAlunos("Alunos em risco — sem presenças há mais de " + DIAS_SEM_PRESENCA_RISCO + " dias",
                                alunos,
                                g -> g.addColumn(a -> {
                                        LocalDate u = ultimaPresenca.get(a.getId());
                                        return u != null ? pt.studioflow.util.DataUtil.formatar(u) : "Nunca";
                                }).setHeader("Última presença").setAutoWidth(true).setSortable(true)
                                                .setComparator(a -> ultimaPresenca.getOrDefault(a.getId(), LocalDate.MIN)));
        }

        private void abrirModalNovosAlunos(List<Aluno> a, String titulo) {
                abrirModalListaAlunos(titulo, a,
                                g -> g.addColumn(al -> pt.studioflow.util.DataUtil.formatar(al.getDataInscricaoRenovacao()))
                                                .setHeader("Inscrição").setAutoWidth(true).setSortable(true)
                                                .setComparator(al -> al.getDataInscricaoRenovacao() != null
                                                                ? al.getDataInscricaoRenovacao() : LocalDate.MIN));
        }

        private void abrirModalPrevisaoPorTurma(List<Turma> turmas, List<Mensalidade> todasM, YearMonth mesRef) {
                Dialog d = new Dialog();
                d.setHeaderTitle("Receita prevista por turma — " + nomeMes(mesRef.getMonth()) + " de " + mesRef.getYear());
                aplicarTamanhoPadraoModal(d);
                Map<String, Double> prev = new HashMap<>();
                for (Turma t : turmas) {
                        double s = todasM.stream().filter(m -> m.getAno() == mesRef.getYear()
                                        && m.getMes() != null && m.getMes().getValue() == mesRef.getMonthValue()
                                        && m.getTurma() != null
                                        && m.getTurma().getId().equals(t.getId())).mapToDouble(Mensalidade::getValor)
                                        .sum();
                        if (s > 0)
                                prev.put(t.getDescricao(), s);
                }
                Grid<Map.Entry<String, Double>> g = new Grid<>();
                g.setItems(prev.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                                .toList());
                g.addColumn(Map.Entry::getKey).setHeader("Turma").setFlexGrow(2);
                g.addColumn(e -> euros2(e.getValue())).setHeader("Previsto").setAutoWidth(true)
                                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
                g.setSizeFull();
                d.add(g);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
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
                d.setHeaderTitle("Lucro real por turma — " + nomeMes(mes.getMonth()) + " de " + mes.getYear());
                aplicarTamanhoPadraoModal(d);
                Grid<String> g = new Grid<>();
                g.setItems(lucros.keySet().stream().sorted((a, b) -> lucros.get(b).compareTo(lucros.get(a))).toList());
                g.addColumn(n -> n).setHeader("Turma").setFlexGrow(2);
                g.addColumn(n -> euros2(
                                todasM.stream().filter(m -> m.getTurma() != null
                                                && m.getTurma().getDescricao().equals(n) && m.getAno() == mes.getYear()
                                                && m.getMes() != null && m.getMes().getValue() == mes.getMonthValue())
                                                .mapToDouble(Mensalidade::getValor).sum()))
                                .setHeader("Receita").setAutoWidth(true)
                                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
                g.addComponentColumn(n -> {
                        double v = lucros.get(n);
                        Span s = new Span(euros2(v));
                        s.addClassNames("cf-strong", v >= 0 ? "cf-tone-success" : "cf-tone-error");
                        return s;
                }).setHeader("Lucro").setAutoWidth(true)
                                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
                g.setSizeFull();
                d.add(g);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private void abrirModalRenovacoesPendentes(List<Aluno> alunosDoStudio) {
                List<Aluno> pendentes = dashboardService.listarRenovacoesPendentes(alunosDoStudio);

                Dialog d = new Dialog();
                d.setHeaderTitle("Renovações pendentes de validação");
                aplicarTamanhoPadraoModal(d);

                if (pendentes.isEmpty()) {
                        d.add(estadoVazio(VaadinIcon.CHECK_CIRCLE, "success", "Nada por validar",
                                        "Não há pedidos de renovação por validar."));
                } else {
                        Grid<Aluno> g = new Grid<>(Aluno.class, false);
                        g.setItems(pendentes);
                        g.addColumn(Aluno::getNomeCompleto).setHeader("Nome").setFlexGrow(2);
                        g.addColumn(a -> a.getTurmas() != null && !a.getTurmas().isEmpty()
                                        ? a.getTurmas().stream().map(at -> at.getTurma().getDescricao())
                                                        .collect(Collectors.joining(", "))
                                        : "—").setHeader("Turma atual").setFlexGrow(2);
                        g.addColumn(a -> pt.studioflow.util.DataUtil.formatar(a.getDataInscricaoRenovacao()))
                                        .setHeader("Pedido em").setAutoWidth(true);
                        g.setSizeFull();
                        d.add(g);

                        Button btnIr = new Button("Ir para Validação de Inscrições",
                                        e -> {
                                                d.close();
                                                navegar("validar-inscricoes");
                                        });
                        btnIr.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                        d.getFooter().add(btnIr);
                }
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        /** Valores por cobrar (faturados e em atraso, multas incluídas) agrupados por mês. */
        private void abrirModalPorCobrar(List<Mensalidade> mens, Studio studio) {
                Dialog d = new Dialog();
                d.setHeaderTitle("Por cobrar, por mês");
                aplicarTamanhoPadraoModal(d);

                record ChaveMes(int ano, int mes) {
                }
                Map<ChaveMes, double[]> porMes = new TreeMap<>(
                                Comparator.<ChaveMes>comparingInt(ChaveMes::ano).thenComparingInt(ChaveMes::mes));

                for (Mensalidade m : mens) {
                        if (m.getMes() == null) continue;
                        EstadoMensalidade efetivo = mensalidadeConfig.estadoEfetivo(m, studio);
                        if (efetivo == EstadoMensalidade.FATURADO || efetivo == EstadoMensalidade.EM_DIVIDA) {
                                double[] v = porMes.computeIfAbsent(new ChaveMes(m.getAno(), m.getMes().getValue()),
                                                k -> new double[2]);
                                double valor = mensalidadeConfig.valorComMulta(m, studio);
                                v[0] += valor;
                                if (efetivo == EstadoMensalidade.EM_DIVIDA) v[1] += valor;
                        }
                }

                record LinhaDTO(int ano, int mes, String periodo, double valor, double atraso) {
                }
                List<LinhaDTO> linhas = porMes.entrySet().stream()
                                .map(e -> new LinhaDTO(e.getKey().ano(), e.getKey().mes(),
                                                capitalizar(nomeMes(Month.of(e.getKey().mes()))) + " de " + e.getKey().ano(),
                                                e.getValue()[0], e.getValue()[1]))
                                .sorted(Comparator.comparingInt(LinhaDTO::ano).thenComparingInt(LinhaDTO::mes).reversed())
                                .toList();

                double total = linhas.stream().mapToDouble(LinhaDTO::valor).sum();
                double totalAtraso = linhas.stream().mapToDouble(LinhaDTO::atraso).sum();

                Grid<LinhaDTO> grid = new Grid<>();
                grid.setItems(linhas);
                grid.setSizeFull();
                grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
                grid.addColumn(LinhaDTO::periodo).setHeader("Mês").setFlexGrow(2);
                grid.addColumn(row -> euros2(row.valor())).setHeader("Por cobrar").setAutoWidth(true)
                                .setSortable(true).setComparator(LinhaDTO::valor)
                                .setTextAlign(com.vaadin.flow.component.grid.ColumnTextAlign.END);
                grid.addComponentColumn(row -> {
                        if (row.atraso() <= 0) return new Span("—");
                        Span s = pill(euros2(row.atraso()), "error", null);
                        return s;
                }).setHeader("Em atraso").setAutoWidth(true).setSortable(true).setComparator(LinhaDTO::atraso);

                Div resumo = new Div();
                resumo.addClassName("cf-callout");
                resumo.addClassName(totalAtraso > 0 ? "cf-callout--error" : "cf-callout--info");
                resumo.add(new Span("Total por cobrar: " + euros2(total)
                                + (totalAtraso > 0 ? " · em atraso: " + euros2(totalAtraso) : "")));

                VerticalLayout content = new VerticalLayout(resumo, grid);
                content.setSizeFull();
                content.expand(grid);
                content.setPadding(false);

                d.add(content);
                if (linhas.isEmpty()) {
                        content.removeAll();
                        content.add(estadoVazio(VaadinIcon.CHECK_CIRCLE, "success", "Nada por cobrar",
                                        "Todas as mensalidades faturadas estão pagas."));
                }
                if (studio == null || studio.hasModulo(StudioModulo.MENSALIDADES)) {
                        Button ir = new Button("Abrir Mensalidades", e -> {
                                d.close();
                                navegar("mensalidade");
                        });
                        ir.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                        d.getFooter().add(ir);
                }
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private void abrirModalAssiduidadePorTurma(List<Turma> turmas, List<Aula> aulas, List<Presenca> todasPresencas,
                        Map<Long, Set<Long>> alunosPorTurma, YearMonth mes) {
                Dialog d = new Dialog();
                d.setHeaderTitle("Assiduidade por turma — " + nomeMes(mes.getMonth()) + " de " + mes.getYear());
                aplicarTamanhoPadraoModal(d);

                record AssiduidadeTurmaDTO(String turma, String professor, long presencas, long esperadas, double pct) {
                }

                LocalDate hoje = LocalDate.now();
                int diasNoMes = mes.equals(YearMonth.from(hoje)) ? hoje.getDayOfMonth() : mes.lengthOfMonth();
                List<Presenca> presencasMes = todasPresencas.stream()
                                .filter(p -> p.getAluno() != null && p.getData() != null
                                                && YearMonth.from(p.getData()).equals(mes))
                                .toList();

                List<AssiduidadeTurmaDTO> dados = turmas.stream().map(t -> {
                        List<Aula> aulasTurma = aulas.stream()
                                        .filter(a -> a.getTurma() != null && a.getTurma().getId().equals(t.getId()))
                                        .toList();

                        Set<Long> alunosDaTurma = alunosPorTurma.getOrDefault(t.getId(), Set.of());
                        long nAlunos = alunosDaTurma.size();
                        long esperadas = 0;
                        for (Aula au : aulasTurma) {
                                for (int i = 1; i <= diasNoMes; i++) {
                                        if (mes.atDay(i).getDayOfWeek() == au.getDia()) esperadas += nAlunos;
                                }
                        }

                        long presencasReais = presencasMes.stream()
                                        .filter(p -> alunosDaTurma.contains(p.getAluno().getId()))
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
                        String tom = row.pct() >= 75 ? "success" : row.pct() >= 50 ? "warning" : "error";
                        return pill(String.format(PT, "%.0f%%", row.pct()), tom, null);
                }).setHeader("Assiduidade").setAutoWidth(true).setSortable(true).setComparator(AssiduidadeTurmaDTO::pct);

                d.add(grid);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private double calcularPercentagemAssiduidade(List<Turma> t, List<Aula> a, Map<Long, Integer> inscritosPorTurma,
                        long pr, YearMonth m) {
                long v = 0;
                LocalDate h = LocalDate.now();
                for (Turma tr : t) {
                        long n = inscritosPorTurma.getOrDefault(tr.getId(), 0);
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

        // =====================================================================
        // AUXILIARES DA ÁREA PESSOAL (PROF / ALUNO)
        // =====================================================================

        private String normalizar(String t) {
                if (t == null)
                        return "";
                String s = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD);
                return s.replaceAll("[^\\p{ASCII}]", "").toLowerCase().trim();
        }

        private Div criarCard(String t, VaadinIcon i, com.vaadin.flow.component.Component c) {
                Div card = new Div();
                card.addClassName("cf-card");
                HorizontalLayout h = new HorizontalLayout(i.create(), new Span(t));
                h.setSpacing(false);
                h.addClassName("cf-card-header");
                Div s = new Div(c);
                s.addClassName("cf-card-body");
                card.add(h, s);
                return card;
        }

        /** Linha "rótulo …… valor"; "tom" = classe de cor do tema para o valor. */
        private Span criarLinhaTextoDestaque(String d, String v, String tom) {
                HorizontalLayout hl = new HorizontalLayout(new Span(d), new Span(v));
                hl.setWidthFull();
                hl.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                hl.getChildren().toArray(Span[]::new)[1].addClassNames(tom, "cf-strong");
                return new Span(hl);
        }

        private com.vaadin.flow.component.Component criarLinhaAgenda(String hora, String descricao, String cor) {
                com.vaadin.flow.component.html.Span spanHora = new com.vaadin.flow.component.html.Span(hora);
                spanHora.addClassName("cf-agenda-hora");
                com.vaadin.flow.component.html.Span spanDesc = new com.vaadin.flow.component.html.Span(descricao);
                spanDesc.getStyle().set("flex-grow", "1");
                HorizontalLayout linha = new HorizontalLayout(spanHora, spanDesc);
                linha.setWidthFull();
                linha.setAlignItems(FlexComponent.Alignment.CENTER);
                linha.addClassName("cf-agenda-line");
                linha.getStyle().set("--cf-agenda-color", cor);
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
