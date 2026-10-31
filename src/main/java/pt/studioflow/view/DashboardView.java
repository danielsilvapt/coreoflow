package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
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
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;
import pt.studioflow.util.LogoUrl;

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
                addClassName("cf-dashboard");
                getStyle().set("display", "flex").set("flex-direction", "column");

                FlexLayout gridLayout = new FlexLayout();
                gridLayout.setWidthFull();
                gridLayout.addClassName("cf-dashboard-grid");
                // FlexLayout põe display:flex inline; a grelha responsiva vem de .cf-dashboard-grid
                gridLayout.getStyle().set("display", "grid");

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

                // --- 0. BOAS-VINDAS (ADMIN): logo, saudação e atalhos inteligentes ---
                if (isAdmin && studio != null) {
                        gridLayout.add(criarCardBoasVindas(studio, firstNameBD, todosAlunos, todasPresencas, hoje));
                }

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
                                                                + a.getTurma().getDescricao() + "|var(--cf-success)"));

                                todasAsPrivadasDeHoje.stream()
                                                .filter(ms -> ms.getProfessor() != null
                                                                && normalizar(ms.getProfessor()).contains(busca))
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
                                                .mapToDouble(r -> pt.studioflow.util.HorasUtil.faturaveis(r.getInicio(), r.getFim()))
                                                .sum();

                                double hEns = registosMes.stream()
                                                .filter(r -> normalizar(r.getProfessor()).contains(busca)
                                                                && r.getMesNumero() == mesAtual.getMonthValue()
                                                                && "ENSAIO".equalsIgnoreCase(r.getTipoAtividade()))
                                                .mapToDouble(r -> pt.studioflow.util.HorasUtil.faturaveis(r.getInicio(), r.getFim()))
                                                .sum();

                                List<MarcacaoSala> marcacoesMes = studio != null
                                                ? marcacaoSalaRepository.findAllByStudio(studio)
                                                : marcacaoSalaRepository.findAll();
                                double hPriv = marcacoesMes.stream()
                                                .filter(m -> normalizar(m.getProfessor()).contains(busca)
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

                                // BI do Professor
                                VerticalLayout vBI = new VerticalLayout();
                                vBI.setPadding(false);
                                // Conta cabeças: aluno em várias turmas do professor só conta uma vez.
                                // Só alunos ativados e confirmados (sem experimentais nem pendentes)
                                // e com pelo menos uma presença registada numa turma do professor
                                final int META_BONUS_ALUNOS = 20;
                                Set<Long> idsTurmasProfBI = turmas.stream().filter(t -> t.getProfessor() != null
                                                        && normalizar(t.getProfessor().getNome()).contains(busca))
                                                .map(Turma::getId).collect(Collectors.toSet());
                                Set<Long> alunosComPresencaProf = todasPresencas.stream()
                                                .filter(p -> p.isPresente() && p.getAluno() != null && p.getTurma() != null
                                                        && idsTurmasProfBI.contains(p.getTurma().getId()))
                                                .map(p -> p.getAluno().getId()).collect(Collectors.toSet());
                                int tAlunos = (int) turmas.stream().filter(t -> t.getProfessor() != null
                                                && normalizar(t.getProfessor().getNome()).contains(busca))
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

                // --- CARDS KPI ADMIN ---
                // Cada estúdio escolhe, na Gestão de Estúdios (SuperAdmin), que cards mostrar aqui.
                if (isAdmin) {
                        java.util.function.Predicate<DashboardCard> cardAtivo = c -> studio == null || studio.hasDashboardCard(c);

                        if (cardAtivo.test(DashboardCard.ALUNOS_ATIVOS)) {
                                Div cAtivos = criarCard("Alunos Ativos", VaadinIcon.USERS,
                                                valorGrande(String.valueOf(todosAlunos.stream().filter(Aluno::isAtivo).count()),
                                                                "cf-tone-info"));
                                cAtivos.addClassName("cf-card-clickable");
                                cAtivos.addClickListener(e -> abrirModalAlocacaoPorTurma(turmas));
                                gridLayout.add(cAtivos);
                        }

                        if (cardAtivo.test(DashboardCard.ANIVERSARIOS)) {
                                List<Aluno> anivs = todosAlunos.stream().filter(a -> a.getDataNascimento() != null
                                                && a.getDataNascimento().getMonth() == hoje.getMonth()
                                                && a.getDataNascimento().getDayOfMonth() == hoje.getDayOfMonth()).toList();
                                gridLayout.add(criarCardAniversarios(anivs));
                        }

                        LocalDate inicioAnoLetivo = LocalDate.of(
                                        hoje.getMonthValue() >= 9 ? hoje.getYear() : hoje.getYear() - 1, 9, 1);

                        if (cardAtivo.test(DashboardCard.NOVOS_ALUNOS)) {
                                LocalDate inicioMes = mesAtual.atDay(1);
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
                                                VaadinIcon.STAR, valorGrande(String.valueOf(novosMes.size()), "cf-tone-warning"));
                                cNovosMes.addClassName("cf-card-clickable");
                                cNovosMes.addClickListener(e -> abrirModalNovosAlunos(novosMes,
                                                "Novos Alunos — " + mesAtual.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt"))));
                                gridLayout.add(cNovosMes);

                                List<Aluno> novosAnoLetivo = todosAlunos.stream().filter(a -> a.getDataInscricaoRenovacao() != null
                                                && !a.getDataInscricaoRenovacao().isBefore(inicioContagemNovosAlunos)
                                                && !alunosComPresencaAntesDaContagemAnoLetivo.contains(a.getId())).toList();
                                Div cNovosAno = criarCard("Novos Alunos (Ano Letivo)", VaadinIcon.CALENDAR,
                                                valorGrande(String.valueOf(novosAnoLetivo.size()), "cf-tone-warning"));
                                cNovosAno.addClassName("cf-card-clickable");
                                cNovosAno.addClickListener(e -> abrirModalNovosAlunos(novosAnoLetivo, "Novos Alunos — Ano Letivo"));
                                gridLayout.add(cNovosAno);
                        }

                        if (studio != null && cardAtivo.test(DashboardCard.RENOVACOES)) {
                                gridLayout.add(criarCardRenovacoes(todosAlunos, inicioAnoLetivo));
                        }

                        if (cardAtivo.test(DashboardCard.ALUNOS_RISCO)) {
                                LocalDate lR = hoje.minusDays(15);
                                List<Aluno> risco = todosAlunos.stream().filter(Aluno::isAtivo).filter(a -> {
                                        Optional<LocalDate> up = todasPresencas.stream()
                                                        .filter(p -> p.getAluno().getId().equals(a.getId()))
                                                        .map(Presenca::getData).max(LocalDate::compareTo);
                                        return up.map(d -> d.isBefore(lR)).orElse(true);
                                }).toList();
                                Div cRisco = criarCard("Alunos em Risco", VaadinIcon.WARNING,
                                                valorGrande(String.valueOf(risco.size()), "cf-tone-warning"));
                                cRisco.addClassName("cf-card-clickable");
                                cRisco.addClickListener(e -> abrirModalAlunosRisco(risco));
                                gridLayout.add(cRisco);
                        }

                        if (cardAtivo.test(DashboardCard.SEGUROS_EXPIRADOS)) {
                                List<Aluno> segs = todosAlunos.stream().filter(Aluno::isAtivo)
                                                .filter(a -> a.getDataExpiracaoSeguro() != null
                                                                && a.getDataExpiracaoSeguro().isBefore(hoje))
                                                .toList();
                                gridLayout.add(criarCardSeguro(segs));
                        }

                        if (cardAtivo.test(DashboardCard.ASSIDUIDADE_GLOBAL)) {
                                long tP = todasPresencas.stream().filter(
                                                p -> p.getData() != null && YearMonth.from(p.getData()).equals(mesAtual))
                                                .count();
                                double pA = calcularPercentagemAssiduidade(turmas, todasAulas, tP, mesAtual);
                                Div cA = criarCard("Assiduidade Global", VaadinIcon.CHECK_SQUARE_O,
                                                criarAssiduidadeContent(pA, tP));
                                cA.addClassName("cf-card-clickable");
                                cA.addClickListener(e -> abrirModalAssiduidadePorTurma(turmas, todasAulas, todasPresencas, mesAtual));
                                gridLayout.add(cA);
                        }

                        if (cardAtivo.test(DashboardCard.PREVISAO_MENSAL)) {
                                double vPrev = todasMensalidades.stream()
                                                .filter(m -> m.getAno() == mesAtual.getYear()
                                                                && m.getMes().getValue() == mesAtual.getMonthValue())
                                                .mapToDouble(Mensalidade::getValor).sum();
                                Div cP = criarCard("Previsão Mensal", VaadinIcon.MONEY,
                                                valorGrande(String.format("%.2f €", vPrev), "cf-tone-neutral"));
                                cP.addClassName("cf-card-clickable");
                                cP.addClickListener(e -> abrirModalPrevisaoPorTurma(turmas, todasMensalidades, mesAtual));
                                gridLayout.add(cP);
                        }

                        if (cardAtivo.test(DashboardCard.TOTAL_PAGO)) {
                                double vPa = todasMensalidades.stream()
                                                .filter(m -> m.getEstado() == EstadoMensalidade.PAGO
                                                                && m.getAno() == mesAtual.getYear()
                                                                && m.getMes().getValue() == mesAtual.getMonthValue())
                                                .mapToDouble(Mensalidade::getValor).sum();
                                gridLayout.add(criarCard("Total Pago", VaadinIcon.MONEY,
                                                valorGrande(String.format("%.2f €", vPa), "cf-tone-success")));
                        }

                        if (cardAtivo.test(DashboardCard.DIVIDA_MES)) {
                                double vDM = todasMensalidades.stream()
                                                .filter(m -> m.getEstado() == EstadoMensalidade.FATURADO
                                                                && m.getAno() == mesAtual.getYear()
                                                                && m.getMes().getValue() == mesAtual.getMonthValue())
                                                .mapToDouble(Mensalidade::getValor).sum();
                                gridLayout.add(criarCard("Dívida do Mês", VaadinIcon.CLOCK,
                                                valorGrande(String.format("%.2f €", vDM), "cf-tone-error")));
                        }

                        if (cardAtivo.test(DashboardCard.DIVIDA_TOTAL)) {
                                double vDT = todasMensalidades.stream().filter(m -> m.getEstado() == EstadoMensalidade.FATURADO)
                                                .mapToDouble(Mensalidade::getValor).sum();
                                Div cDT = criarCard("Dívida Total", VaadinIcon.COINS,
                                                valorGrande(String.format("%.2f €", vDT), "cf-tone-error"));
                                cDT.addClassName("cf-card-clickable");
                                cDT.addClickListener(e -> abrirModalDividaHistorica(todasMensalidades));
                                gridLayout.add(cDT);
                        }

                        if (cardAtivo.test(DashboardCard.MODALIDADES)) {
                                Div cMod = criarCard("Modalidades", VaadinIcon.BAR_CHART, criarGraficoModalidades(turmas, 4));
                                cMod.addClassName("cf-card-clickable");
                                cMod.addClickListener(e -> abrirModalSimples("Ranking", criarGraficoModalidades(turmas, 100)));
                                gridLayout.add(cMod);
                        }

                        if (cardAtivo.test(DashboardCard.RENTABILIDADE)) {
                                Map<String, Double> lucros = calcularRentabilidadeMap(turmas, todasMensalidades, todasAulas,
                                                mesAtual);
                                double totalL = lucros.values().stream().mapToDouble(v -> v).sum();
                                Div cRent = criarCard("Rentabilidade", VaadinIcon.CHART_LINE, valorGrande(
                                                String.format("%.2f €", totalL), totalL >= 0 ? "cf-tone-success" : "cf-tone-error"));
                                cRent.addClassName("cf-card-clickable");
                                cRent.addClickListener(e -> abrirModalRentabilidadeDetalhada(lucros, turmas, todasMensalidades,
                                                mesAtual));
                                gridLayout.add(cRent);
                        }
                }

                add(gridLayout);
        }

        // --- CARD DE BOAS-VINDAS (ADMIN) ---

        private Div criarCardBoasVindas(Studio studio, String firstName, List<Aluno> todosAlunos,
                        List<Presenca> todasPresencas, LocalDate hoje) {
                Div card = new Div();
                card.addClassName("cf-welcome");
                // Gradiente com as cores do estúdio (o resto do visual vem de .cf-welcome)
                card.getStyle()
                                .set("--cf-welcome-from", corOuDefault(studio.getCorPrimaria(), "#2F6FD6"))
                                .set("--cf-welcome-to", corOuDefault(studio.getCorSecundaria(), "#0F1F4B"));

                HorizontalLayout topo = new HorizontalLayout();
                topo.setWidthFull();
                topo.setAlignItems(FlexComponent.Alignment.CENTER);
                topo.setSpacing(false);
                topo.addClassName("cf-welcome-top");

                if (studio.getLogoPath() != null && !studio.getLogoPath().isBlank()) {
                        Image logo = new Image(LogoUrl.comVersao(studio.getLogoPath()), studio.getNome());
                        logo.addClassName("cf-welcome-logo");
                        topo.add(logo);
                }

                VerticalLayout texto = new VerticalLayout();
                texto.setPadding(false);
                texto.setSpacing(false);
                texto.addClassName("cf-welcome-text");

                Span titulo = new Span(saudacaoPeloRelogio()
                                + (firstName != null && !firstName.isBlank() ? ", " + firstName : "") + "! 👋");
                titulo.addClassName("cf-welcome-title");

                Span sub = new Span("Aqui tens um resumo rápido do " + studio.getNome() + " para hoje.");
                sub.addClassName("cf-welcome-sub");

                texto.add(titulo, sub);
                topo.add(texto);
                topo.expand(texto);
                card.add(topo);

                // --- Atalhos rápidos inteligentes: sempre úteis + contextuais consoante o estado do estúdio ---
                HorizontalLayout acoes = new HorizontalLayout();
                acoes.setSpacing(false);
                acoes.addClassName("cf-welcome-actions");

                acoes.add(botaoAcaoRapida("➕ Novo Aluno", null, e -> getUI().ifPresent(ui -> ui.navigate("alunos"))));
                acoes.add(botaoAcaoRapida("🪄 Aula Experimental", null, e -> getUI().ifPresent(ui -> ui.navigate("presencas"))));

                long pendentesRenovacao = dashboardService.countRenovacoesPendentes(todosAlunos);
                if (pendentesRenovacao > 0) {
                        acoes.add(botaoAcaoRapida("🔔 " + pendentesRenovacao + " renovação(ões) por validar", "warning",
                                        e -> getUI().ifPresent(ui -> ui.navigate("validar-inscricoes"))));
                }

                long aniversariantesHoje = todosAlunos.stream().filter(a -> a.getDataNascimento() != null
                                && a.getDataNascimento().getMonth() == hoje.getMonth()
                                && a.getDataNascimento().getDayOfMonth() == hoje.getDayOfMonth()).count();
                if (aniversariantesHoje > 0) {
                        acoes.add(botaoAcaoRapida("🎂 " + aniversariantesHoje + " aniversário(s) hoje", "pink",
                                        e -> getUI().ifPresent(ui -> ui.navigate("crm"))));
                }

                long segurosExpirados = todosAlunos.stream().filter(Aluno::isAtivo)
                                .filter(a -> a.getDataExpiracaoSeguro() != null && a.getDataExpiracaoSeguro().isBefore(hoje))
                                .count();
                if (segurosExpirados > 0) {
                        acoes.add(botaoAcaoRapida("⚠️ " + segurosExpirados + " seguro(s) expirado(s)", "error",
                                        e -> getUI().ifPresent(ui -> ui.navigate("alunos"))));
                }

                LocalDate limiteRisco = hoje.minusDays(15);
                long alunosRisco = todosAlunos.stream().filter(Aluno::isAtivo).filter(a -> {
                        Optional<LocalDate> ultima = todasPresencas.stream()
                                        .filter(p -> p.getAluno().getId().equals(a.getId()))
                                        .map(Presenca::getData).max(LocalDate::compareTo);
                        return ultima.map(d -> d.isBefore(limiteRisco)).orElse(true);
                }).count();
                if (alunosRisco > 0) {
                        acoes.add(botaoAcaoRapida("📉 " + alunosRisco + " aluno(s) em risco", "warning",
                                        e -> getUI().ifPresent(ui -> ui.navigate("crm"))));
                }

                acoes.add(botaoAcaoRapida("💬 Comunicação", null, e -> getUI().ifPresent(ui -> ui.navigate("comunicacao"))));

                card.add(acoes);
                return card;
        }

        /** Atalho do cartão de boas-vindas; "variante" = warning | error | pink | null (neutro). */
        private Button botaoAcaoRapida(String texto, String variante,
                        com.vaadin.flow.component.ComponentEventListener<com.vaadin.flow.component.ClickEvent<Button>> listener) {
                Button b = new Button(texto, listener);
                b.addClassName("cf-quick-action");
                if (variante != null) {
                        b.addClassName("cf-quick-action--" + variante);
                }
                return b;
        }

        private String saudacaoPeloRelogio() {
                int hora = java.time.LocalTime.now().getHour();
                if (hora < 12) return "Bom dia";
                if (hora < 20) return "Boa tarde";
                return "Boa noite";
        }

        private String corOuDefault(String cor, String def) {
                return (cor != null && !cor.isBlank()) ? cor : def;
        }

        // --- MODAIS E AUXILIARES (PROTEÇÕES DE LIMITE INCLUÍDAS) ---

        /** Tamanho uniforme para todas as popups abertas a partir dos cards do dashboard. */
        private void aplicarTamanhoPadraoModal(Dialog d) {
                d.setWidth("900px");
                d.setMaxWidth("95vw");
                d.setHeight("80vh");
                d.setMaxHeight("90vh");
        }

        private void abrirModalAlocacaoPorTurma(List<Turma> turmas) {
                Dialog d = new Dialog();
                d.setHeaderTitle("Alocação por Turma");
                aplicarTamanhoPadraoModal(d);
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
                        bar.addClassName("cf-bar");
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
                aplicarTamanhoPadraoModal(d);
                Grid<Aluno> g = new Grid<>(Aluno.class, false);
                g.setItems(alunos);
                g.addColumn(Aluno::getNomeCompleto).setHeader("Nome").setSortable(true);
                g.addColumn(Aluno::getTelemovel).setHeader("Telemóvel");
                g.addColumn(a -> pt.studioflow.util.DataUtil.formatar(a.getDataExpiracaoSeguro()))
                                .setHeader("Data Expiração").setSortable(true);
                g.setSizeFull();
                d.add(g);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private void abrirModalAlunosRisco(List<Aluno> alunos) {
                Dialog d = new Dialog();
                d.setHeaderTitle("Alunos em Risco");
                aplicarTamanhoPadraoModal(d);
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
                aplicarTamanhoPadraoModal(d);
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
                g.setSizeFull();
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
                aplicarTamanhoPadraoModal(d);
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
                g.setSizeFull();
                d.add(g);
                d.open();
        }

        private VerticalLayout criarAssiduidadeContent(double p, long t) {
                VerticalLayout v = new VerticalLayout();
                v.setAlignItems(FlexComponent.Alignment.CENTER);
                Span s = new Span(String.format("%.1f%%", p));
                s.addClassName("cf-kpi-value");
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
                card.addClassName("cf-card");
                HorizontalLayout h = new HorizontalLayout(i.create(), new Span(t));
                h.setSpacing(false);
                h.addClassName("cf-card-header");
                Div s = new Div(c);
                s.addClassName("cf-card-body");
                card.add(h, s);
                return card;
        }

        private Div criarCardSeguro(List<Aluno> e) {
                HorizontalLayout hl = new HorizontalLayout();
                if (!e.isEmpty()) {
                        Icon i = VaadinIcon.WARNING.create();
                        i.addClassName("cf-tone-error");
                        hl.add(i);
                }
                hl.setAlignItems(FlexComponent.Alignment.CENTER);
                hl.add(valorGrande(String.valueOf(e.size()), e.isEmpty() ? "cf-tone-success" : "cf-tone-error"));
                Div c = criarCard("Seguros Expirados", VaadinIcon.EXCLAMATION_CIRCLE_O, hl);
                c.addClassName("cf-card-clickable");
                c.addClickListener(ev -> abrirModalSeguros(e));
                return c;
        }

        /** Valor KPI em destaque; "tom" = classe de cor do tema (cf-tone-success, cf-tone-error, ...). */
        private Span valorGrande(String v, String tom) {
                Span s = new Span(v);
                s.addClassNames("cf-kpi-value", "cf-kpi-center", tom);
                return s;
        }

        /** Linha "rótulo …… valor"; "tom" = classe de cor do tema para o valor. */
        private Span criarLinhaTextoDestaque(String d, String v, String tom) {
                HorizontalLayout hl = new HorizontalLayout(new Span(d), new Span(v));
                hl.setWidthFull();
                hl.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                hl.getChildren().toArray(Span[]::new)[1].addClassNames(tom, "cf-strong");
                return new Span(hl);
        }

        private Div criarCardAniversarios(List<Aluno> h) {
                boolean t = !h.isEmpty();
                VerticalLayout l = new VerticalLayout();
                l.setAlignItems(FlexComponent.Alignment.CENTER);
                Span n = new Span(String.valueOf(h.size()));
                n.addClassNames("cf-kpi-value", "cf-kpi-value--hero", t ? "cf-tone-violet" : "cf-tone-muted");
                l.add(n, new Span(t ? "Aniversários Hoje!" : "Sem festas hoje"));
                Div c = criarCard("Aniversários", t ? VaadinIcon.GIFT : VaadinIcon.CALENDAR_CLOCK, l);
                c.addClassName("cf-card-clickable");
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
                aplicarTamanhoPadraoModal(d);
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
                                valorGrande(String.valueOf(pendentes), pendentes > 0 ? "cf-tone-warning" : "cf-tone-neutral"),
                                new Span("pedidos por validar"));
                linhaTopo.setAlignItems(FlexComponent.Alignment.BASELINE);

                ProgressBar pb = new ProgressBar();
                pb.setWidthFull();
                pb.setValue(elegiveis > 0 ? Math.max(0.0, Math.min(1.0, (double) concluidas / elegiveis)) : 0.0);

                Span legenda = new Span(concluidas + " de " + elegiveis + " alunos elegíveis já renovaram este ano letivo");
                legenda.addClassNames("cf-text-xs", "cf-muted");

                content.add(linhaTopo, pb, legenda);

                Div card = criarCard("Renovações", VaadinIcon.REFRESH, content);
                card.addClassName("cf-card-clickable");
                card.addClickListener(e -> abrirModalRenovacoesPendentes(alunosDoStudio));
                return card;
        }

        private void abrirModalRenovacoesPendentes(List<Aluno> alunosDoStudio) {
                List<Aluno> pendentes = dashboardService.listarRenovacoesPendentes(alunosDoStudio);

                Dialog d = new Dialog();
                d.setHeaderTitle("Renovações Pendentes de Validação");
                aplicarTamanhoPadraoModal(d);

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
                        g.addColumn(a -> pt.studioflow.util.DataUtil.formatar(a.getDataInscricaoRenovacao()))
                                        .setHeader("Pedido em");
                        g.setSizeFull();
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
                aplicarTamanhoPadraoModal(d);
                Grid<Aluno> g = new Grid<>(Aluno.class, false);
                g.setItems(a);
                g.addColumn(Aluno::getNomeCompleto).setHeader("Nome");
                g.setSizeFull();
                d.add(g);
                d.open();
        }

        private void abrirModalDividaHistorica(List<Mensalidade> mens) {
                Dialog d = new Dialog();
                d.setHeaderTitle("📊 Histórico de Dívidas por Mês");
                aplicarTamanhoPadraoModal(d);

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
                        pb.addClassName("cf-progress-error");
                        return pb;
                }).setHeader("% do Total").setAutoWidth(true);

                Div totalDiv = new Div();
                totalDiv.addClassName("cf-total-banner");
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
                aplicarTamanhoPadraoModal(d);

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
                        String tom = row.pct() >= 75 ? "success" : row.pct() >= 50 ? "warning" : "error";
                        badge.addClassNames("cf-pill", "cf-pill--" + tom);
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
