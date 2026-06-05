package pt.studioflow.view;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.MarcacaoSala;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Transferencia;
import pt.studioflow.model.User;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.MarcacaoSalaRepository;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.PresencaRepository;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.RegistoHorasRepository;
import pt.studioflow.repository.TransferenciaRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.AuthService;

@PageTitle("Notificações | CoreoFlow")
@Route(value = "notificacoes", layout = MainLayout.class)
@RolesAllowed({"ADMIN", "PROF"})
public class NotificacoesView extends VerticalLayout {

    private final AlunoRepository alunoRepository;
    private final MensalidadeRepository mensalidadeRepository;
    private final MarcacaoSalaRepository marcacaoSalaRepository;
    private final PresencaRepository presencaRepository;
    private final TransferenciaRepository transferenciaRepository;
    private final ProfessorRepository professorRepository;
    private final RegistoHorasRepository registoHorasRepository;
    private final TurmaRepository turmaRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final AuthService authService;

    private final Div conteudo = new Div();

    public NotificacoesView(AlunoRepository alunoRepository,
            MensalidadeRepository mensalidadeRepository,
            MarcacaoSalaRepository marcacaoSalaRepository,
            PresencaRepository presencaRepository,
            TransferenciaRepository transferenciaRepository,
            ProfessorRepository professorRepository,
            RegistoHorasRepository registoHorasRepository,
            TurmaRepository turmaRepository,
            AlunoTurmaRepository alunoTurmaRepository,
            AuthService authService) {
        this.alunoRepository = alunoRepository;
        this.mensalidadeRepository = mensalidadeRepository;
        this.marcacaoSalaRepository = marcacaoSalaRepository;
        this.presencaRepository = presencaRepository;
        this.transferenciaRepository = transferenciaRepository;
        this.professorRepository = professorRepository;
        this.registoHorasRepository = registoHorasRepository;
        this.turmaRepository = turmaRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.authService = authService;

        setSizeFull();
        setPadding(true);
        setSpacing(false);
        injectStyles();

        HorizontalLayout headerRow = new HorizontalLayout();
        headerRow.setWidthFull();
        headerRow.setAlignItems(Alignment.CENTER);
        headerRow.setJustifyContentMode(JustifyContentMode.BETWEEN);
        H2 titulo = new H2("Notificações");
        titulo.getStyle().set("margin", "0");
        Button btnAtualizar = new Button("Atualizar", VaadinIcon.REFRESH.create());
        btnAtualizar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        btnAtualizar.addClickListener(e -> {
            conteudo.removeAll();
            carregarNotificacoes();
        });
        headerRow.add(titulo, btnAtualizar);

        conteudo.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("gap", "16px")
            .set("width", "100%")
            .set("margin-top", "20px");

        add(headerRow, conteudo);
        carregarNotificacoes();
    }

    private void injectStyles() {
        String css =
            ".notif-card { background: white; border-radius: 14px; box-shadow: 0 4px 15px rgba(0,0,0,0.06); border: 1px solid #eee; overflow: hidden; }" +
            ".notif-card-header { display: flex; align-items: center; gap: 12px; padding: 16px 20px; border-bottom: 1px solid #f0f0f0; }" +
            ".notif-badge { border-radius: 20px; padding: 3px 10px; font-size: 0.75rem; font-weight: 700; color: white; }" +
            ".notif-row { display: flex; align-items: center; gap: 12px; padding: 10px 20px; border-bottom: 1px solid #f9f9f9; }" +
            ".notif-row:last-child { border-bottom: none; }" +
            ".notif-row:hover { background: #fafafa; }" +
            ".notif-empty { padding: 16px 20px; color: #aaa; font-style: italic; }" +
            ".notif-section-label { font-size: 0.7rem; font-weight: 700; text-transform: uppercase; letter-spacing: 1px; color: #aaa; padding: 8px 0 4px 0; }";

        getElement().executeJs(
            "const s = document.createElement('style'); s.textContent = $0; document.head.appendChild(s);", css);
    }

    private boolean isAdmin() {
        return authService.getCurrentUser()
            .map(u -> "ADMIN".equals(u.getRole()) || "SUPERADMIN".equals(u.getRole()) || "DELEG".equals(u.getRole()))
            .orElse(false);
    }

    private Optional<Professor> getProfessorLogado() {
        return authService.getCurrentUser()
            .flatMap(u -> {
                // Tenta por email primeiro, depois por firstName
                Optional<Professor> byEmail = u.getEmail() != null
                    ? professorRepository.findByEmail(u.getEmail())
                    : Optional.empty();
                if (byEmail.isPresent()) return byEmail;
                return u.getFirstName() != null
                    ? professorRepository.findByNome(u.getFirstName())
                    : Optional.empty();
            });
    }

    private void carregarNotificacoes() {
        if (isAdmin()) {
            carregarNotificacoesAdmin();
        } else {
            carregarNotificacoesProfessor();
        }
    }

    // =========================================================
    // NOTIFICAÇÕES ADMIN
    // =========================================================
    private void carregarNotificacoesAdmin() {
        LocalDate hoje = LocalDate.now();
        YearMonth mesAtual = YearMonth.now();
        Studio studio = TenantContext.getCurrentStudio();
        List<Aluno> todosAlunos = studio != null
            ? alunoRepository.findAllByStudio(studio)
            : alunoRepository.findAll();

        // 1. Pedidos de sala pendentes
        List<MarcacaoSala> salasPendentes = (studio != null
            ? marcacaoSalaRepository.findByStudioAndStatus(studio, "PENDENTE")
            : marcacaoSalaRepository.findAll().stream()
                .filter(m -> "PENDENTE".equalsIgnoreCase(m.getStatus())).collect(Collectors.toList()))
            .stream().sorted((a, b) -> {
                if (a.getData() == null) return 1;
                if (b.getData() == null) return -1;
                return a.getData().compareTo(b.getData());
            }).collect(Collectors.toList());

        // 2. Inscrições pendentes de validação
        List<Aluno> inscricoesPendentes = todosAlunos.stream()
            .filter(a -> a.getStatus() == Aluno.AlunoStatus.PENDENTE
                      || a.getStatus() == Aluno.AlunoStatus.EXPERIMENTAL)
            .collect(Collectors.toList());

        // 3. Transferências pendentes
        List<Transferencia> transferPendentes = transferenciaRepository.findAll().stream()
            .filter(t -> studio == null || (t.getStudio() != null && t.getStudio().getId().equals(studio.getId())))
            .filter(t -> "AGUARDA_ASSINATURAS".equals(t.getEstado())
                      || "AGUARDA_UMA_ASSINATURA".equals(t.getEstado())
                      || "AGUARDA_PAGAMENTO".equals(t.getEstado()))
            .collect(Collectors.toList());

        // 4. Mensalidades em dívida (faturadas há mais de 10 dias)
        List<Aluno> comDivida = todosAlunos.stream()
            .filter(Aluno::isAtivo)
            .filter(a -> {
                List<Mensalidade> faturadas = mensalidadeRepository.findByAlunoAndEstado(a, EstadoMensalidade.FATURADO);
                return faturadas.stream().anyMatch(m -> hoje.isAfter(LocalDate.of(m.getAno(), m.getMes(), 10)));
            }).collect(Collectors.toList());

        // 5. Mensalidades por emitir este mês
        List<Mensalidade> porEmitir = (studio != null
            ? mensalidadeRepository.findAllByStudio(studio)
            : mensalidadeRepository.findAll()).stream()
            .filter(m -> m.getEstado() == EstadoMensalidade.POR_EMITIR
                      && m.getAno() == mesAtual.getYear()
                      && m.getMes().getValue() == mesAtual.getMonthValue())
            .collect(Collectors.toList());

        // 6. Seguros expirados
        List<Aluno> segurosExpirados = todosAlunos.stream()
            .filter(Aluno::isAtivo)
            .filter(a -> a.getDataExpiracaoSeguro() != null && a.getDataExpiracaoSeguro().isBefore(hoje))
            .sorted((a, b) -> a.getDataExpiracaoSeguro().compareTo(b.getDataExpiracaoSeguro()))
            .collect(Collectors.toList());

        // 7. Quotas de sócio expiradas
        List<Aluno> quotasExpiradas = todosAlunos.stream()
            .filter(Aluno::isAtivo)
            .filter(a -> a.getDataExpiracaoQuota() != null && a.getDataExpiracaoQuota().isBefore(hoje))
            .sorted((a, b) -> a.getDataExpiracaoQuota().compareTo(b.getDataExpiracaoQuota()))
            .collect(Collectors.toList());

        // 8. Alunos em risco (sem presença há 15+ dias)
        LocalDate corte = hoje.minusDays(15);
        List<Aluno> emRisco = todosAlunos.stream()
            .filter(Aluno::isAtivo)
            .filter(a -> {
                var ultima = presencaRepository.findByAlunoId(a.getId()).stream()
                    .map(p -> p.getData()).filter(d -> d != null)
                    .max(LocalDate::compareTo);
                return ultima.map(d -> d.isBefore(corte)).orElse(true);
            }).collect(Collectors.toList());

        // 9. Aniversários hoje e esta semana
        List<Aluno> aniversariosHoje = todosAlunos.stream()
            .filter(a -> a.getDataNascimento() != null
                && a.getDataNascimento().getDayOfMonth() == hoje.getDayOfMonth()
                && a.getDataNascimento().getMonth() == hoje.getMonth())
            .collect(Collectors.toList());
        List<Aluno> aniversariosSemana = todosAlunos.stream()
            .filter(a -> a.getDataNascimento() != null && aniversariosHoje.stream()
                .noneMatch(b -> b.getId().equals(a.getId())))
            .filter(a -> {
                LocalDate aniv = a.getDataNascimento().withYear(hoje.getYear());
                return !aniv.isBefore(hoje.plusDays(1)) && !aniv.isAfter(hoje.plusDays(7));
            }).collect(Collectors.toList());

        // 10. Alunos com seguro a expirar em 30 dias
        List<Aluno> segurosAExpirar = todosAlunos.stream()
            .filter(Aluno::isAtivo)
            .filter(a -> a.getDataExpiracaoSeguro() != null
                && !a.getDataExpiracaoSeguro().isBefore(hoje)
                && a.getDataExpiracaoSeguro().isBefore(hoje.plusDays(30)))
            .collect(Collectors.toList());

        // --- RESUMO ---
        int totalAlertas = salasPendentes.size() + inscricoesPendentes.size() + transferPendentes.size()
            + comDivida.size() + segurosExpirados.size() + quotasExpiradas.size() + emRisco.size();
        conteudo.add(criarResumoGlobal(totalAlertas, aniversariosHoje.size()));

        // --- CARDS ---
        if (!aniversariosHoje.isEmpty()) {
            conteudo.add(criarCard("🎂 Aniversários Hoje!", aniversariosHoje.size(), "#e91e63", VaadinIcon.GIFT,
                aniversariosHoje.stream().map(Aluno::getNomeCompleto).collect(Collectors.toList()), "alunos"));
        }
        if (!aniversariosSemana.isEmpty()) {
            conteudo.add(criarCard("🎉 Aniversários esta semana", aniversariosSemana.size(), "#f06292", VaadinIcon.CALENDAR,
                aniversariosSemana.stream().map(a -> a.getNomeCompleto() + " — " +
                    a.getDataNascimento().getDayOfMonth() + "/" + a.getDataNascimento().getMonthValue())
                    .collect(Collectors.toList()), "alunos"));
        }

        conteudo.add(criarCard("Pedidos de Sala Pendentes", salasPendentes.size(), "#6a1b9a", VaadinIcon.HOME,
            salasPendentes.stream().map(m ->
                (m.getData() != null ? m.getData() + " " : "") + m.getHoraInicio() + "–" + m.getHoraFim()
                + " | " + (m.getProfessor() != null ? m.getProfessor() : "—") + " (" + m.getTipo() + ")"
            ).collect(Collectors.toList()), "sala-schedule"));

        conteudo.add(criarCard("Inscrições Pendentes de Validação", inscricoesPendentes.size(), "#1565c0", VaadinIcon.USER_CHECK,
            inscricoesPendentes.stream().map(a -> a.getNomeCompleto() + " (" + a.getStatus() + ")")
                .collect(Collectors.toList()), "validar-inscricoes"));

        conteudo.add(criarCard("Transferências Pendentes", transferPendentes.size(), "#e65100", VaadinIcon.INSTITUTION,
            transferPendentes.stream().map(t ->
                (t.getDescricao() != null ? t.getDescricao() : "Transferência") + " | " + t.getEstado()
            ).collect(Collectors.toList()), "transferencias"));

        conteudo.add(criarCard("Mensalidades em Dívida", comDivida.size(), "#c62828", VaadinIcon.MONEY,
            comDivida.stream().map(a -> a.getNomeCompleto() + " — " + a.getTelemovel())
                .collect(Collectors.toList()), "mensalidade"));

        String nomeMes = mesAtual.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt"));
        conteudo.add(criarCard("Mensalidades por Emitir (" + nomeMes + ")", porEmitir.size(), "#f57f17", VaadinIcon.FILE_PROCESS,
            porEmitir.stream().map(m ->
                (m.getAluno() != null ? m.getAluno().getNomeCompleto() : "—") + " — "
                + (m.getTurma() != null ? m.getTurma().getDescricao() : "—")
                + " | " + String.format("%.2f€", m.getValor())
            ).collect(Collectors.toList()), "mensalidade"));

        conteudo.add(criarCard("Seguros Desportivos Expirados", segurosExpirados.size(), "#d32f2f", VaadinIcon.SHIELD,
            segurosExpirados.stream().map(a -> a.getNomeCompleto() + " — expirou em " + a.getDataExpiracaoSeguro())
                .collect(Collectors.toList()), "alunos"));

        if (!segurosAExpirar.isEmpty()) {
            conteudo.add(criarCard("⚠️ Seguros a Expirar em 30 dias", segurosAExpirar.size(), "#ff8f00", VaadinIcon.ALARM,
                segurosAExpirar.stream().map(a -> a.getNomeCompleto() + " — expira em " + a.getDataExpiracaoSeguro())
                                .collect(Collectors.toList()), "alunos"));
        }

        conteudo.add(criarCard("Quotas de Sócio Expiradas", quotasExpiradas.size(), "#880e4f", VaadinIcon.CREDIT_CARD,
            quotasExpiradas.stream().map(a -> a.getNomeCompleto() + " — expirou em " + a.getDataExpiracaoQuota())
                .collect(Collectors.toList()), "socios"));

        conteudo.add(criarCard("Alunos em Risco de Abandono (sem presença há 15+ dias)", emRisco.size(), "#bf360c", VaadinIcon.WARNING,
            emRisco.stream().map(a -> a.getNomeCompleto() + " — " + a.getTelemovel())
                .collect(Collectors.toList()), "alunos"));
    }

    // =========================================================
    // NOTIFICAÇÕES PROFESSOR
    // =========================================================
    private void carregarNotificacoesProfessor() {
        LocalDate hoje = LocalDate.now();
        YearMonth mesAtual = YearMonth.now();
        Optional<Professor> profOpt = getProfessorLogado();
        String nomeProfessor = profOpt.map(Professor::getNome).orElse(null);

        List<MarcacaoSala> salasAprovadas = nomeProfessor != null
            ? marcacaoSalaRepository.findByProfessorAndStatusAndDataBetween(
                nomeProfessor, "APROVADO", hoje.minusDays(2), hoje.plusDays(30))
            : List.of();

        List<MarcacaoSala> salasPendentes = nomeProfessor != null
            ? marcacaoSalaRepository.findByProfessorAndStatusAndDataBetween(
                nomeProfessor, "PENDENTE", hoje.minusDays(1), hoje.plusDays(60))
            : List.of();

        boolean fimDeMes = hoje.getDayOfMonth() >= mesAtual.lengthOfMonth() - 4;
        int horasRegistadasMes = nomeProfessor != null
            ? registoHorasRepository.findByProfessorAndAnoAndMesNumero(
                nomeProfessor, mesAtual.getYear(), mesAtual.getMonthValue()).size()
            : 0;

        LocalDate corte = hoje.minusDays(10);
        List<String> alunosEmRisco = profOpt.map(p -> {
            var turmas = turmaRepository.findByProfessorAndStudio(p, TenantContext.getCurrentStudio());
            return turmas.stream()
                .flatMap(t -> alunoTurmaRepository.findByTurma(t).stream())
                .map(at -> at.getAluno())
                .filter(a -> a != null && a.isAtivo())
                .filter(a -> {
                    var ultima = presencaRepository.findByAlunoId(a.getId()).stream()
                        .map(pr -> pr.getData()).filter(d -> d != null)
                        .max(LocalDate::compareTo);
                    return ultima.map(d -> d.isBefore(corte)).orElse(true);
                })
                .map(a -> a.getNomeCompleto()).distinct().collect(Collectors.toList());
        }).orElse(List.of());

        List<String> aniversariosHoje = profOpt.map(p -> {
            var turmas = turmaRepository.findByProfessorAndStudio(p, TenantContext.getCurrentStudio());
            return turmas.stream()
                .flatMap(t -> alunoTurmaRepository.findByTurma(t).stream())
                .map(at -> at.getAluno())
                .filter(a -> a != null && a.getDataNascimento() != null
                    && a.getDataNascimento().getDayOfMonth() == hoje.getDayOfMonth()
                    && a.getDataNascimento().getMonth() == hoje.getMonth())
                .map(Aluno::getNomeCompleto).distinct().collect(Collectors.toList());
        }).orElse(List.of());

        int totalAlertas = salasPendentes.size() + alunosEmRisco.size();
        conteudo.add(criarResumoGlobal(totalAlertas, aniversariosHoje.size()));

        if (!aniversariosHoje.isEmpty()) {
            conteudo.add(criarCard("🎂 Aniversários Hoje nas suas Turmas", aniversariosHoje.size(), "#e91e63", VaadinIcon.GIFT,
                aniversariosHoje, "alunos"));
        }

        conteudo.add(criarCard("✅ Salas Aprovadas (últimos 2 dias / próximas)", salasAprovadas.size(), "#2e7d32", VaadinIcon.CHECK_CIRCLE,
            salasAprovadas.stream().map(m ->
                (m.getData() != null ? m.getData().toString() : "—") + " " + m.getHoraInicio() + "–" + m.getHoraFim()
                + " | " + (m.getSala() != null ? m.getSala().getNome() : "—") + " (" + m.getTipo() + ")"
            ).collect(Collectors.toList()), "sala-schedule"));

        conteudo.add(criarCard("⏳ Pedidos de Sala Aguardam Aprovação", salasPendentes.size(), "#f57f17", VaadinIcon.CLOCK,
            salasPendentes.stream().map(m ->
                (m.getData() != null ? m.getData().toString() : "—") + " " + m.getHoraInicio() + "–" + m.getHoraFim()
                + " | " + (m.getSala() != null ? m.getSala().getNome() : "—") + " (" + m.getTipo() + ")"
            ).collect(Collectors.toList()), "sala-schedule"));

        if (fimDeMes) {
            String nomeMes = mesAtual.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt"));
            int ultimoDia = mesAtual.lengthOfMonth();
            String aviso = "Tem " + horasRegistadasMes + " registo(s) em " + nomeMes
                + ". Certifique-se que estão todos registados até ao dia " + ultimoDia + ".";
            conteudo.add(criarCardAlerta(
                "⏰ Registe as Horas de " + nomeMes + " até ao último dia!",
                aviso, "#6a1b9a", VaadinIcon.ALARM, "registo-horas"));
        }

        conteudo.add(criarCard("Alunos sem Presença há 10+ dias", alunosEmRisco.size(), "#bf360c", VaadinIcon.WARNING,
            alunosEmRisco, "presencas"));
    }

    // =========================================================
    // COMPONENTES UI
    // =========================================================
    private Div criarResumoGlobal(int totalAlertas, int aniversarios) {
        Div card = new Div();
        card.getStyle()
            .set("background", totalAlertas == 0 ? "#e8f5e9" : "#fff8e1")
            .set("border-radius", "14px").set("padding", "20px 24px")
            .set("display", "flex").set("align-items", "center").set("gap", "16px")
            .set("border", totalAlertas == 0 ? "1px solid #c8e6c9" : "1px solid #ffe082");
        Icon ico = totalAlertas == 0 ? VaadinIcon.CHECK_CIRCLE.create() : VaadinIcon.WARNING.create();
        ico.setSize("28px");
        ico.setColor(totalAlertas == 0 ? "#2e7d32" : "#f57f17");
        String mensagem = totalAlertas == 0 ? "Tudo em ordem — sem alertas ativos."
            : totalAlertas + " alerta(s) requerem atenção.";
        if (aniversarios > 0) mensagem += "  🎂 " + aniversarios + " aniversário(s) hoje!";
        Span texto = new Span(mensagem);
        texto.getStyle().set("font-weight", "600").set("font-size", "1rem")
            .set("color", totalAlertas == 0 ? "#2e7d32" : "#e65100");
        card.add(ico, texto);
        return card;
    }

    private Div criarCardAlerta(String titulo, String mensagem, String cor, VaadinIcon icone, String rota) {
        Div card = new Div();
        card.addClassName("notif-card");
        Div header = new Div();
        header.addClassName("notif-card-header");
        Icon ico = icone.create(); ico.setColor(cor); ico.setSize("20px");
        H3 titCard = new H3(titulo);
        titCard.getStyle().set("margin", "0").set("font-size", "0.95rem").set("flex", "1");
        Button btnVer = new Button("Ver", VaadinIcon.ARROW_FORWARD.create());
        btnVer.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        btnVer.addClickListener(e -> UI.getCurrent().navigate(rota));
        header.add(ico, titCard, btnVer);
        card.add(header);
        Div row = new Div(); row.addClassName("notif-row");
        Span label = new Span(mensagem);
        label.getStyle().set("font-size", "0.88rem").set("color", "#333");
        row.add(label); card.add(row);
        return card;
    }

    private Div criarCard(String titulo, int contagem, String cor, VaadinIcon icone,
            List<String> linhas, String rotaDestino) {
        Div card = new Div();
        card.addClassName("notif-card");
        Div header = new Div();
        header.addClassName("notif-card-header");
        Icon ico = icone.create(); ico.setColor(cor); ico.setSize("20px");
        H3 titCard = new H3(titulo);
        titCard.getStyle().set("margin", "0").set("font-size", "0.95rem").set("flex", "1");
        Span badge = new Span(String.valueOf(contagem));
        badge.addClassName("notif-badge");
        badge.getStyle().set("background-color", contagem == 0 ? "#4caf50" : cor);
        Button btnVer = new Button("Ver", VaadinIcon.ARROW_FORWARD.create());
        btnVer.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        btnVer.addClickListener(e -> UI.getCurrent().navigate(rotaDestino));
        header.add(ico, titCard, badge, btnVer);
        card.add(header);
        if (linhas.isEmpty()) {
            Div empty = new Div(); empty.addClassName("notif-empty");
            empty.setText("Sem alertas nesta categoria."); card.add(empty);
        } else {
            List<String> aExibir = linhas.size() > 8 ? linhas.subList(0, 8) : linhas;
            for (String linha : aExibir) {
                Div row = new Div(); row.addClassName("notif-row");
                Icon dot = VaadinIcon.CIRCLE.create(); dot.setColor(cor); dot.setSize("10px");
                dot.getStyle().set("flex-shrink", "0").set("opacity", "0.7");
                Span label = new Span(linha);
                label.getStyle().set("font-size", "0.88rem").set("color", "#333");
                row.add(dot, label); card.add(row);
            }
            if (linhas.size() > 8) {
                Div mais = new Div(); mais.addClassName("notif-row");
                Span maisSpan = new Span("... e mais " + (linhas.size() - 8) + " registos.");
                maisSpan.getStyle().set("font-size", "0.82rem").set("color", "#999").set("font-style", "italic");
                mais.add(maisSpan); card.add(mais);
            }
        }
        return card;
    }
}
