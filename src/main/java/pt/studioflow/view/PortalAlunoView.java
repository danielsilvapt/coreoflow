package pt.studioflow.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;
import pt.studioflow.service.AuthService;
import pt.studioflow.service.CheckinService;
import pt.studioflow.service.CompraCreditoService;
import pt.studioflow.service.PagamentoOnlineService;
import pt.studioflow.service.R2StorageService;
import pt.studioflow.view.component.ListaEventos;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "portal", layout = MainLayout.class)
@PageTitle("Portal | CoreoFlow")
@RolesAllowed("ALUNO")
public class PortalAlunoView extends VerticalLayout implements BeforeEnterObserver {

    private final MensalidadeConfig mensalidadeConfig;
    private final AuthService authService;
    private final MensalidadeRepository mensalidadeRepo;
    private final PresencaRepository presencaRepo;
    private final AvaliacaoAlunoRepository avaliacaoRepo;
    private final ContratoDigitalRepository contratoRepo;
    private final VideoAulaRepository videoAulaRepo;
    private final R2StorageService storageService;
    private final ConviteRepository conviteRepo;
    private final InscricaoEventoRepository inscricaoRepo;
    private final CompraCreditoService compraCreditoService;
    private final CheckinService checkinService;
    private final PagamentoOnlineService pagamentoOnlineService;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:https://app.coreoflow.me}")
    private String baseUrl;

    public PortalAlunoView(AuthService authService,
                            AlunoRepository alunoRepo,
                            MensalidadeRepository mensalidadeRepo,
                            PresencaRepository presencaRepo,
                            AvaliacaoAlunoRepository avaliacaoRepo,
                            ContratoDigitalRepository contratoRepo,
                            VideoAulaRepository videoAulaRepo,
                            R2StorageService storageService,
                            MensalidadeConfig mensalidadeConfig,
                            ConviteRepository conviteRepo,
                            InscricaoEventoRepository inscricaoRepo,
                            CompraCreditoService compraCreditoService,
                            CheckinService checkinService,
                            PagamentoOnlineService pagamentoOnlineService) {

        this.mensalidadeConfig = mensalidadeConfig;
        this.authService = authService;
        this.mensalidadeRepo = mensalidadeRepo;
        this.presencaRepo = presencaRepo;
        this.avaliacaoRepo = avaliacaoRepo;
        this.contratoRepo = contratoRepo;
        this.videoAulaRepo = videoAulaRepo;
        this.storageService = storageService;
        this.conviteRepo = conviteRepo;
        this.inscricaoRepo = inscricaoRepo;
        this.compraCreditoService = compraCreditoService;
        this.checkinService = checkinService;
        this.pagamentoOnlineService = pagamentoOnlineService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
    }

    /**
     * O conteúdo é montado aqui (e não no construtor) para que, no regresso do
     * checkout Mollie ({@code ?pagamento=<id>}), o estado do pagamento seja
     * confirmado antes de se listarem as mensalidades.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Studio studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        String pagamentoId = event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("pagamento", List.of()).stream()
                .findFirst().orElse(null);
        String notaPagamento = pagamentoId != null && studio != null
                ? pagamentoOnlineService.mensagemRegresso(pagamentoId, studio) : null;

        removeAll();
        construir();

        if (notaPagamento != null) {
            Notification.show(notaPagamento, 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(notaPagamento.startsWith("Pagamento recebido")
                            ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_CONTRAST);
        }
    }

    private void construir() {
        Aluno aluno = authService.getAlunoLogado();
        Studio studio = pt.studioflow.config.TenantContext.getCurrentStudio();

        if (aluno == null) {
            add(new Span("Perfil de aluno não encontrado. Contacta a secretaria."));
            return;
        }

        // Header
        add(criarHeader(aluno, studio));

        // Cards de resumo
        List<Mensalidade> mensalidades = studio != null
                ? mensalidadeRepo.findByAlunoAndStudio(aluno, studio)
                : mensalidadeRepo.findByAluno(aluno);
        long emDivida = mensalidades.stream()
                .filter(m -> mensalidadeConfig.estadoEfetivo(m, studio) == EstadoMensalidade.EM_DIVIDA
                          || m.getEstado() == EstadoMensalidade.POR_EMITIR)
                .count();

        // Destaque: próxima mensalidade a pagar + prazo do estúdio
        add(criarCardProximaMensalidade(mensalidades, studio));

        // Estúdio com Mollie: pagar online as mensalidades em aberto até ao mês corrente
        List<Mensalidade> pagaveis = studio != null && studio.isMollieAtivo()
                ? pagamentoOnlineService.mensalidadesPagaveis(mensalidades) : List.of();
        if (!pagaveis.isEmpty()) {
            add(criarCardPagamentoOnline(aluno, studio, pagaveis));
        }

        List<Presenca> presencas = presencaRepo.findByAlunoId(aluno.getId());
        long presencasMes = presencas.stream()
                .filter(p -> p.getData() != null
                        && p.getData().getMonth() == LocalDate.now().getMonth()
                        && p.getData().getYear() == LocalDate.now().getYear()
                        && Boolean.TRUE.equals(p.isPresente()))
                .count();

        long turmasInscritas = aluno.getTurmas() != null ? aluno.getTurmas().size() : 0;

        add(criarCards(turmasInscritas, presencasMes, emDivida));

        // Tabs com detalhes
        TabSheet tabs = new TabSheet();
        tabs.setSizeFull();
        tabs.addClassName("cf-portal-tabs");

        tabs.add("📅 Presenças", criarTabPresencas(presencas, videoAulaRepo, storageService));
        tabs.add("💳 Mensalidades", criarTabMensalidades(aluno, mensalidades, pagaveis, studio));
        if (studio == null || studio.hasModulo(StudioModulo.EVENTOS)) {
            tabs.add("🎭 Eventos", criarTabEventos(aluno, studio, conviteRepo, inscricaoRepo));
        }
        tabs.add("⭐ Avaliações", criarTabAvaliacoes(aluno, avaliacaoRepo));
        tabs.add("📄 Contratos", criarTabContratos(aluno, contratoRepo));
        if (studio == null || studio.hasModulo(StudioModulo.AULAS_AVULSO)) {
            tabs.add("🎟️ Aulas Avulso", criarTabAulasAvulso(aluno, studio, compraCreditoService, checkinService));
        }

        add(tabs);
        expand(tabs);
    }

    private VerticalLayout criarHeader(Aluno aluno, Studio studio) {
        VerticalLayout header = new VerticalLayout();
        header.addClassName("cf-portal-hero");
        header.setSpacing(false);

        String primeiroNome = aluno.getNomeCompleto() != null
                ? aluno.getNomeCompleto().split(" ")[0] : "Aluno";

        H2 saudacao = new H2("Olá, " + primeiroNome + "! 👋");

        Span sub = new Span((studio != null ? studio.getNome() : "CoreoFlow")
                + " · " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy",
                new Locale("pt"))));
        sub.addClassName("cf-portal-hero-sub");

        header.add(saudacao, sub);
        return header;
    }

    private HorizontalLayout criarCards(long turmas, long presencas, long dividas) {
        HorizontalLayout cards = new HorizontalLayout(
                card("Turmas Inscritas", String.valueOf(turmas), VaadinIcon.GROUP, "#4A90E2"),
                card("Presenças este Mês", String.valueOf(presencas), VaadinIcon.CHECK_CIRCLE, "#27AE60"),
                card("Mensalidades Pendentes", String.valueOf(dividas), VaadinIcon.WARNING,
                        dividas > 0 ? "#E74C3C" : "#27AE60")
        );
        cards.setWidthFull();
        cards.setSpacing(false);
        cards.addClassName("cf-portal-cards");
        return cards;
    }

    private VerticalLayout card(String label, String valor, VaadinIcon icone, String cor) {
        String tom = "cf-tone-" + ViewUtils.tomDaCor(cor);
        Icon icon = icone.create();
        icon.addClassNames("cf-stat-icon", tom);
        icon.getStyle().set("position", "static");
        Span v = new Span(valor);
        v.addClassNames("cf-kpi-value", tom);
        Span l = new Span(label);
        l.addClassName("cf-kpi-label");
        VerticalLayout c = new VerticalLayout(icon, v, l);
        c.setAlignItems(FlexComponent.Alignment.CENTER);
        c.setSpacing(false);
        c.setPadding(false);
        c.addClassNames("cf-card", "cf-portal-stat");
        return c;
    }

    private VerticalLayout criarTabPresencas(List<Presenca> presencas, VideoAulaRepository videoAulaRepo,
            R2StorageService storageService) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSizeFull();

        // Últimas 20 presenças
        List<Presenca> ultimas = presencas.stream()
                .filter(p -> p.getData() != null)
                .sorted((a, b) -> b.getData().compareTo(a.getData()))
                .limit(20).toList();

        // Vídeos disponíveis para as turmas+datas destas presenças (1 query, sem N+1)
        List<Turma> turmasDasPresencas = ultimas.stream()
                .map(Presenca::getTurma).filter(java.util.Objects::nonNull).distinct().toList();
        List<LocalDate> datasDasPresencas = ultimas.stream()
                .map(Presenca::getData).distinct().toList();
        List<VideoAula> videosDisponiveis = turmasDasPresencas.isEmpty() || datasDasPresencas.isEmpty()
                ? List.of()
                : videoAulaRepo.findByTurmaInAndDataInOrderByDataUploadDesc(turmasDasPresencas, datasDasPresencas);
        java.util.Map<String, List<VideoAula>> videosPorTurmaData = videosDisponiveis.stream()
                .collect(java.util.stream.Collectors.groupingBy(v -> v.getTurma().getId() + "|" + v.getData()));

        Grid<Presenca> grid = new Grid<>(Presenca.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(p -> p.getData().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .setHeader("Data").setAutoWidth(true);
        grid.addColumn(p -> p.getTurma() != null ? p.getTurma().getDescricao() : "—")
                .setHeader("Turma").setAutoWidth(true);
        grid.addComponentColumn(p -> {
            boolean presente = Boolean.TRUE.equals(p.isPresente());
            Span s = new Span(presente ? "✅ Presente" : "❌ Falta");
            s.addClassNames("cf-semibold", presente ? "cf-tone-success" : "cf-tone-error");
            return s;
        }).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(p -> {
            if (p.getTurma() == null) return new Span("");
            List<VideoAula> videos = videosPorTurmaData.get(p.getTurma().getId() + "|" + p.getData());
            if (videos == null || videos.isEmpty()) return new Span("");
            Button verVideo = new Button("Vídeo", VaadinIcon.PLAY_CIRCLE.create(),
                    e -> mostrarVideosDaAula(p.getTurma(), p.getData(), videos, storageService));
            verVideo.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return verVideo;
        }).setHeader("Vídeo").setAutoWidth(true);

        grid.setItems(ultimas);
        H3 t = new H3("Últimas Presenças");
        layout.add(t, grid);
        layout.expand(grid);
        return layout;
    }

    private void mostrarVideosDaAula(Turma turma, LocalDate data, List<VideoAula> videos,
            R2StorageService storageService) {
        com.vaadin.flow.component.dialog.Dialog dialog = new com.vaadin.flow.component.dialog.Dialog();
        dialog.setHeaderTitle(turma.getDescricao() + " · " + data.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        dialog.setWidth("380px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        videos.forEach(v -> {
            // Anchor (não Page.open()) — o popup-blocker bloqueia janelas abertas
            // após um round-trip do servidor.
            Button verBtn = new Button(v.getNomeFicheiro(), VaadinIcon.PLAY_CIRCLE.create());
            verBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            verBtn.setWidthFull();
            com.vaadin.flow.component.html.Anchor ver = new com.vaadin.flow.component.html.Anchor(
                    storageService.gerarUrlTemporario(v.getChaveArmazenamento(), java.time.Duration.ofHours(2)), verBtn);
            ver.setTarget("_blank");
            ver.setRouterIgnore(true);
            ver.getStyle().set("flex-grow", "1").set("text-decoration", "none");

            Button descarregarBtn = new Button(VaadinIcon.DOWNLOAD_ALT.create());
            descarregarBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
            com.vaadin.flow.component.html.Anchor descarregar = new com.vaadin.flow.component.html.Anchor(
                    storageService.gerarUrlDownload(v.getChaveArmazenamento(), v.getNomeFicheiro(),
                            java.time.Duration.ofHours(2)),
                    descarregarBtn);
            descarregar.getElement().setAttribute("download", true);
            descarregar.getStyle().set("text-decoration", "none");

            HorizontalLayout linha = new HorizontalLayout(ver, descarregar);
            linha.setWidthFull();
            linha.setFlexGrow(1, ver);
            linha.setAlignItems(FlexComponent.Alignment.CENTER);
            content.add(linha);
        });
        dialog.add(content);
        dialog.getFooter().add(new Button("Fechar", e -> dialog.close()));
        dialog.open();
    }

    private Component criarCardProximaMensalidade(List<Mensalidade> mensalidades, Studio studio) {
        Mensalidade proxima = mensalidades.stream()
                .filter(m -> m.getEstado() == EstadoMensalidade.FATURADO)
                .min(Comparator.comparingInt(Mensalidade::getAno)
                        .thenComparingInt(m -> m.getMes().getValue()))
                .orElse(null);

        VerticalLayout card = new VerticalLayout();
        card.setSpacing(false);
        card.setPadding(false);
        card.addClassNames("cf-card", "cf-accent-card", "cf-portal-block");

        Integer diaLimite = mensalidadeConfig.diaLimitePagamento(studio);

        if (proxima == null) {
            card.addClassName("cf-accent-success");
            H3 t = new H3("Sem mensalidades por pagar 🎉");
            t.addClassName("cf-card-title");
            t.getStyle().set("font-size", "var(--lumo-font-size-l)");
            Span nota = new Span(diaLimite != null
                    ? "As mensalidades vencem no dia " + diaLimite + " de cada mês."
                    : "Este estúdio não tem vencimento automático definido.");
            nota.addClassName("cf-card-meta");
            card.add(t, nota);
            return card;
        }

        LocalDate limite = mensalidadeConfig.dataLimite(proxima.getAno(), proxima.getMes(), studio);
        long dias = limite != null ? ChronoUnit.DAYS.between(LocalDate.now(), limite) : Long.MAX_VALUE;
        boolean divida = mensalidadeConfig.estadoEfetivo(proxima, studio) == EstadoMensalidade.EM_DIVIDA;
        double valorEfetivo = mensalidadeConfig.valorComMulta(proxima, studio);
        String tom = divida ? "error" : (limite != null && dias <= 7 ? "warning" : "success");
        card.addClassName("cf-accent-" + tom);

        Span label = new Span("PRÓXIMA MENSALIDADE");
        label.addClassName("cf-card-eyebrow");

        String periodo = capitalizar(proxima.getMes().getDisplayName(TextStyle.FULL, new Locale("pt")))
                + " " + proxima.getAno();
        Span titulo = new Span(periodo + " · " + String.format("%.2f €", valorEfetivo));
        titulo.addClassName("cf-card-title");

        Span venc = new Span(limite != null
                ? "Vence a " + limite.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "Sem data de vencimento automática definida");
        venc.addClassName("cf-card-meta");

        String estadoTxt;
        if (divida) {
            long atraso = ChronoUnit.DAYS.between(limite, LocalDate.now());
            estadoTxt = "⚠️ Em dívida há " + atraso + (atraso == 1 ? " dia" : " dias");
        } else if (limite == null) {
            estadoTxt = "Faturado";
        } else if (dias <= 0) {
            estadoTxt = "Vence hoje";
        } else if (dias <= 7) {
            estadoTxt = "Vence em " + dias + (dias == 1 ? " dia" : " dias");
        } else {
            estadoTxt = "Em dia";
        }
        Span badge = new Span(estadoTxt);
        badge.addClassNames("cf-pill", "cf-pill--" + tom);
        badge.getStyle().set("margin-top", "var(--lumo-space-s)");

        card.add(label, titulo, venc, badge);
        return card;
    }

    private Component criarCardPagamentoOnline(Aluno aluno, Studio studio, List<Mensalidade> pagaveis) {
        double total = pagaveis.stream().mapToDouble(m -> mensalidadeConfig.valorComMulta(m, studio)).sum();
        String periodos = pagaveis.stream()
                .map(m -> capitalizar(m.getMes().getDisplayName(TextStyle.SHORT, new Locale("pt"))) + " " + m.getAno())
                .distinct().collect(Collectors.joining(", "));

        Span label = new Span("PAGAMENTO ONLINE");
        label.addClassName("cf-card-eyebrow");
        Span titulo = new Span("Em aberto: " + String.format("%.2f €", total));
        titulo.addClassName("cf-card-title");
        Span detalhe = new Span(periodos);
        detalhe.addClassName("cf-card-meta");

        Button pagar = new Button("Pagar " + String.format("%.2f €", total) + " online", new Icon(VaadinIcon.CREDIT_CARD),
                e -> pagarMensalidades(aluno, studio, pagaveis));
        pagar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        pagar.getStyle().set("margin-top", "var(--lumo-space-s)");

        VerticalLayout card = new VerticalLayout(label, titulo, detalhe, pagar);
        card.setSpacing(false);
        card.setPadding(false);
        card.addClassNames("cf-card", "cf-accent-card", "cf-accent-info", "cf-portal-block");
        return card;
    }

    private void pagarMensalidades(Aluno aluno, Studio studio, List<Mensalidade> mensalidades) {
        try {
            PagamentoOnlineService.Checkout checkout = pagamentoOnlineService.criarPagamentoMensalidades(
                    aluno, studio, mensalidades, baseUrl + "/portal?pagamento=", baseUrl + "/api/mollie/webhook");
            UI.getCurrent().getPage().setLocation(checkout.checkoutUrl());
        } catch (Exception ex) {
            Notification.show("Não foi possível abrir o pagamento online: " + ex.getMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private static String capitalizar(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private VerticalLayout criarTabEventos(Aluno aluno, Studio studio,
            ConviteRepository conviteRepo, InscricaoEventoRepository inscricaoRepo) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.add(new H3("Próximos Eventos"));

        Set<Long> minhasTurmas = aluno.getTurmas() == null ? Set.of()
                : aluno.getTurmas().stream()
                        .map(AlunoTurma::getTurma).filter(Objects::nonNull)
                        .map(Turma::getId).collect(Collectors.toSet());

        LocalDate hoje = LocalDate.now();
        List<Convite> eventos = (studio != null ? conviteRepo.findAllByStudio(studio) : conviteRepo.findAll())
                .stream()
                .filter(c -> c.getData() != null && !c.getData().isBefore(hoje))
                .filter(c -> minhasTurmas.stream().anyMatch(tid -> {
                    StatusParticipacao st = c.getParticipacoes().get(tid);
                    return st != null && st != StatusParticipacao.NAO_VAI;
                }))
                .sorted(Comparator.comparing(Convite::getData)
                        .thenComparing(c -> c.getHora() != null ? c.getHora() : LocalTime.MIN))
                .toList();

        if (eventos.isEmpty()) {
            layout.add(ListaEventos.vazio("Não há eventos agendados para as tuas turmas."));
            return layout;
        }

        Map<Long, InscricaoEvento> inscricoes = inscricaoRepo.findByAluno(aluno).stream()
                .filter(i -> i.getConvite() != null)
                .collect(Collectors.toMap(i -> i.getConvite().getId(), i -> i, (a, b) -> a));

        for (Convite c : eventos) {
            InscricaoEvento existente = inscricoes.get(c.getId());
            Checkbox interesse = new Checkbox("Tenho interesse");
            interesse.setValue(existente != null && existente.isInteressado());
            interesse.addValueChangeListener(ev -> {
                InscricaoEvento i = inscricoes.computeIfAbsent(c.getId(), k -> new InscricaoEvento(aluno, c));
                i.setInteressado(ev.getValue());
                i.setStudio(studio != null ? studio : aluno.getStudio());
                i.setDataResposta(LocalDateTime.now());
                inscricaoRepo.save(i);
                Notification.show(Boolean.TRUE.equals(ev.getValue())
                        ? "Interesse registado!" : "Interesse removido.",
                        2500, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });
            layout.add(ListaEventos.card(c, null, interesse));
        }
        return layout;
    }

    private VerticalLayout criarTabMensalidades(Aluno aluno, List<Mensalidade> mensalidades,
            List<Mensalidade> pagaveis, Studio studio) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSizeFull();

        Grid<Mensalidade> grid = new Grid<>(Mensalidade.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(m -> m.getMes().getDisplayName(TextStyle.SHORT, new Locale("pt"))
                + " " + m.getAno()).setHeader("Período").setAutoWidth(true);
        grid.addColumn(m -> String.format("%.2f €", mensalidadeConfig.valorComMulta(m, studio)))
                .setHeader("Valor").setAutoWidth(true);
        grid.addComponentColumn(m -> {
            String[] cfg = switch (mensalidadeConfig.estadoEfetivo(m, studio)) {
                case PAGO -> new String[]{"success","Pago"};
                case FATURADO -> new String[]{"info","Faturado"};
                case POR_EMITIR -> new String[]{"warning","Por Emitir"};
                case EM_DIVIDA -> new String[]{"error","Em Dívida"};
            };
            Span b = new Span(cfg[1]);
            b.addClassNames("cf-pill", "cf-pill--" + cfg[0]);
            return b;
        }).setHeader("Estado").setAutoWidth(true);
        if (!pagaveis.isEmpty()) {
            // Paga o mês inteiro: nos modelos por pacote a mensalidade está repartida pelas turmas.
            Set<Long> idsPagaveis = pagaveis.stream().map(Mensalidade::getId).collect(Collectors.toSet());
            grid.addComponentColumn(m -> {
                if (!idsPagaveis.contains(m.getId())) return new Span();
                List<Mensalidade> doMes = pagaveis.stream()
                        .filter(p -> p.getAno() == m.getAno() && p.getMes() == m.getMes()).toList();
                Button pagar = new Button("Pagar", e -> pagarMensalidades(aluno, studio, doMes));
                pagar.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
                return pagar;
            }).setAutoWidth(true);
        }

        List<Mensalidade> ordenadas = mensalidades.stream()
                .sorted((a, b) -> {
                    if (a.getAno() != b.getAno()) return b.getAno() - a.getAno();
                    return b.getMes().getValue() - a.getMes().getValue();
                }).toList();
        grid.setItems(ordenadas);
        layout.add(new H3("Histórico de Mensalidades"), grid);
        layout.expand(grid);
        return layout;
    }

    private VerticalLayout criarTabAvaliacoes(Aluno aluno, AvaliacaoAlunoRepository avaliacaoRepo) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);

        List<AvaliacaoAluno> avaliacoes = avaliacaoRepo.findByAlunoOrderByDataAvaliacaoDesc(aluno);

        if (avaliacoes.isEmpty()) {
            Span vazio = new Span("Ainda não há avaliações registadas.");
            vazio.addClassName("cf-empty");
            layout.add(new H3("Avaliações"), vazio);
            return layout;
        }

        for (AvaliacaoAluno av : avaliacoes) {
            layout.add(criarCardAvaliacao(av));
        }
        layout.addComponentAtIndex(0, new H3("Avaliações"));
        return layout;
    }

    private VerticalLayout criarCardAvaliacao(AvaliacaoAluno av) {
        VerticalLayout card = new VerticalLayout();
        card.addClassName("cf-card");
        card.getStyle().set("margin-bottom", "var(--lumo-space-s)");
        card.setSpacing(false);

        Span periodo = new Span(av.getPeriodo() + " · " + av.getTurma().getDescricao());
        periodo.addClassNames("cf-strong", "cf-text-s");

        String nivelCor = switch (av.getNivel()) {
            case INICIANTE -> "#E67E22"; case INTERMEDIO -> "#1976D2";
            case AVANCADO -> "#7B1FA2"; case EXCELENTE -> "#27AE60";
        };
        Span nivel = new Span(av.getNivel().name());
        nivel.getStyle().set("background", nivelCor).set("color", "white")
                .set("margin-left", "8px");
        nivel.addClassName("cf-pill");

        HorizontalLayout cabecalho = new HorizontalLayout(periodo, nivel);
        cabecalho.setAlignItems(FlexComponent.Alignment.CENTER);

        // Competências
        if (av.getCompetencias() != null && !av.getCompetencias().isBlank()) {
            HorizontalLayout comps = new HorizontalLayout();
            comps.setSpacing(true);
            comps.getStyle().set("flex-wrap", "wrap").set("margin-top", "8px");
            AvaliacoesView.parseCompetencias(av.getCompetencias()).forEach((k, v) -> {
                Span s = new Span(k + ": " + "★".repeat(v) + "☆".repeat(5 - v));
                s.addClassName("cf-chip");
                comps.add(s);
            });
            card.add(cabecalho, comps);
        } else {
            card.add(cabecalho);
        }

        if (av.getObservacoes() != null && !av.getObservacoes().isBlank()) {
            Span obs = new Span("\"" + av.getObservacoes() + "\"");
            obs.addClassNames("cf-card-meta");
            obs.getStyle().set("font-style", "italic").set("margin-top", "6px");
            card.add(obs);
        }

        return card;
    }

    private VerticalLayout criarTabAulasAvulso(Aluno aluno, Studio studio,
            CompraCreditoService compraCreditoService, CheckinService checkinService) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(false);

        H4 tituloCreditos = new H4("Os teus créditos");
        tituloCreditos.getStyle().set("margin-bottom", "8px");
        layout.add(tituloCreditos);

        List<CompraCredito> compras = compraCreditoService.listarDoAluno(aluno);
        if (compras.isEmpty()) {
            layout.add(new Span("Ainda não compraste nenhuma aula avulsa/pack."));
        } else {
            for (CompraCredito c : compras) {
                Div card = new Div();
                card.addClassName("cf-card");
                card.getStyle().set("margin-bottom", "var(--lumo-space-s)");
                String validade = c.getValidadeAte() != null ? " · válido até " + c.getValidadeAte() : "";
                String estado = c.getMetodoPagamento() == MetodoPagamentoCredito.MANUAL
                        && c.getEstadoPagamento() == EstadoPagamentoCredito.PENDENTE
                        ? "Pagar no estúdio" : c.getEstadoPagamento().toString();
                card.add(new Span(c.getNomePack() + " — " + c.getCreditosRestantes() + "/" + c.getNumAulasComprado()
                        + " aula(s) restante(s)" + validade + " · " + estado));
                layout.add(card);
            }
        }

        Button comprarMais = new Button("Comprar mais aulas", VaadinIcon.PLUS.create(), e ->
                UI.getCurrent().navigate("aula-avulsa?studio=" + (studio != null ? studio.getSlug() : "")));
        comprarMais.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        comprarMais.getStyle().set("margin", "8px 0 20px 0");
        layout.add(comprarMais);

        H4 tituloCheckin = new H4("Checkin");
        tituloCheckin.getStyle().set("margin-bottom", "8px");
        layout.add(tituloCheckin);

        List<CheckinService.TurmaCheckin> disponiveis = checkinService.listarTurmasParaCheckin(aluno, studio, LocalDateTime.now());
        if (disponiveis.isEmpty()) {
            layout.add(new Span("Nenhuma aula tua dentro da janela de checkin neste momento."));
        } else {
            for (CheckinService.TurmaCheckin tc : disponiveis) {
                HorizontalLayout linha = new HorizontalLayout();
                linha.setAlignItems(FlexComponent.Alignment.CENTER);
                linha.setWidthFull();
                linha.getStyle().set("padding", "8px 0");
                Span nome = new Span(tc.turma.getDescricao());
                linha.add(nome);
                if (tc.jaFezCheckin) {
                    Span feito = new Span("✓ Presença registada");
                    feito.addClassNames("cf-tone-success", "cf-strong");
                    linha.add(feito);
                } else {
                    Button botaoCheckin = new Button("Fazer Checkin", ev -> {
                        try {
                            checkinService.registarCheckin(aluno, tc.turma, studio, MetodoRegistoPresenca.AUTO_ALUNO);
                            Notification.show("Checkin feito! Boa aula 🎉").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                            UI.getCurrent().getPage().reload();
                        } catch (IllegalStateException ex) {
                            Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
                        }
                    });
                    botaoCheckin.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
                    linha.add(botaoCheckin);
                }
                layout.add(linha);
            }
        }

        return layout;
    }

    private VerticalLayout criarTabContratos(Aluno aluno, ContratoDigitalRepository contratoRepo) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);

        List<ContratoDigital> contratos = contratoRepo.findByAlunoOrderByDataGeracaoDesc(aluno);

        if (contratos.isEmpty()) {
            Span vazio = new Span("Não existem contratos pendentes.");
            vazio.addClassName("cf-empty");
            layout.add(new H3("Contratos"), vazio);
            return layout;
        }

        Grid<ContratoDigital> grid = new Grid<>(ContratoDigital.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setHeight("280px");

        grid.addColumn(c -> c.getTipo() + " · " + c.getAnoLetivo()).setHeader("Contrato").setFlexGrow(1);
        grid.addColumn(c -> c.getDataGeracao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .setHeader("Gerado").setAutoWidth(true);
        grid.addComponentColumn(c -> {
            boolean assinado = "ASSINADO".equals(c.getEstado());
            Span b = new Span(assinado ? "✅ Assinado" : "⏳ Pendente");
            b.addClassNames("cf-semibold", assinado ? "cf-tone-success" : "cf-tone-warning");
            return b;
        }).setHeader("Estado").setAutoWidth(true);

        grid.addComponentColumn(c -> {
            if ("PENDENTE".equals(c.getEstado())) {
                Button assinar = new Button("Assinar", VaadinIcon.PENCIL.create(),
                        e -> UI.getCurrent().navigate("contrato/" + c.getId()));
                assinar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                return assinar;
            }
            return new Span(c.getDataAssinatura() != null
                    ? c.getDataAssinatura().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "");
        }).setHeader("Ação").setAutoWidth(true);

        grid.setItems(contratos);
        layout.add(new H3("Contratos"), grid);
        return layout;
    }
}
