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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;
import pt.studioflow.service.AuthService;
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
public class PortalAlunoView extends VerticalLayout {

    private final MensalidadeConfig mensalidadeConfig;

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
                            InscricaoEventoRepository inscricaoRepo) {

        this.mensalidadeConfig = mensalidadeConfig;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

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
        tabs.getStyle().set("padding", "0 16px");

        tabs.add("📅 Presenças", criarTabPresencas(presencas, videoAulaRepo, storageService));
        tabs.add("💳 Mensalidades", criarTabMensalidades(mensalidades, studio));
        if (studio == null || studio.hasModulo(StudioModulo.EVENTOS)) {
            tabs.add("🎭 Eventos", criarTabEventos(aluno, studio, conviteRepo, inscricaoRepo));
        }
        tabs.add("⭐ Avaliações", criarTabAvaliacoes(aluno, avaliacaoRepo));
        tabs.add("📄 Contratos", criarTabContratos(aluno, contratoRepo));

        add(tabs);
        expand(tabs);
    }

    private VerticalLayout criarHeader(Aluno aluno, Studio studio) {
        VerticalLayout header = new VerticalLayout();
        header.getStyle()
                .set("background", ViewUtils.corPrimaria())
                .set("padding", "20px 24px 16px");
        header.setSpacing(false);

        String primeiroNome = aluno.getNomeCompleto() != null
                ? aluno.getNomeCompleto().split(" ")[0] : "Aluno";

        H2 saudacao = new H2("Olá, " + primeiroNome + "! 👋");
        saudacao.getStyle().set("color", "white").set("margin", "0");

        Span sub = new Span((studio != null ? studio.getNome() : "CoreoFlow")
                + " · " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy",
                new Locale("pt"))));
        sub.getStyle().set("color", "rgba(255,255,255,0.8)").set("font-size", "13px");

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
        cards.getStyle().set("padding", "16px 16px 0");
        return cards;
    }

    private VerticalLayout card(String label, String valor, VaadinIcon icone, String cor) {
        Icon icon = icone.create();
        icon.setSize("24px");
        icon.setColor(cor);
        Span v = new Span(valor);
        v.getStyle().set("font-size", "28px").set("font-weight", "700").set("color", cor);
        Span l = new Span(label);
        l.getStyle().set("font-size", "11px").set("color", "#888").set("text-transform", "uppercase");
        VerticalLayout c = new VerticalLayout(icon, v, l);
        c.setAlignItems(FlexComponent.Alignment.CENTER);
        c.setSpacing(false);
        c.setPadding(true);
        c.getStyle()
                .set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)").set("flex", "1");
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
            s.getStyle().set("color", presente ? "#27AE60" : "#E74C3C").set("font-weight", "600");
            return s;
        }).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(p -> {
            if (p.getTurma() == null) return new Span("");
            List<VideoAula> videos = videosPorTurmaData.get(p.getTurma().getId() + "|" + p.getData());
            if (videos == null || videos.isEmpty()) return new Span("");
            Button verVideo = new Button("Vídeo", VaadinIcon.PLAY_CIRCLE.create(),
                    e -> mostrarVideosDaAula(p.getTurma(), p.getData(), videos, storageService));
            verVideo.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            verVideo.getStyle().set("color", "#D32F2F");
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
        card.setPadding(true);
        card.getStyle()
                .set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)")
                .set("margin", "16px 16px 0");

        int diaLimite = mensalidadeConfig.diaLimitePagamento(studio);

        if (proxima == null) {
            card.getStyle().set("border-left", "5px solid #27AE60");
            H3 t = new H3("Sem mensalidades por pagar 🎉");
            t.getStyle().set("margin", "0").set("font-size", "16px");
            Span nota = new Span("As mensalidades vencem no dia " + diaLimite + " de cada mês.");
            nota.getStyle().set("color", "#888").set("font-size", "13px");
            card.add(t, nota);
            return card;
        }

        LocalDate limite = mensalidadeConfig.dataLimite(proxima.getAno(), proxima.getMes(), studio);
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), limite);
        boolean divida = mensalidadeConfig.estadoEfetivo(proxima, studio) == EstadoMensalidade.EM_DIVIDA;
        String cor = divida ? "#E74C3C" : (dias <= 7 ? "#E67E22" : "#27AE60");
        card.getStyle().set("border-left", "5px solid " + cor);

        Span label = new Span("PRÓXIMA MENSALIDADE");
        label.getStyle().set("font-size", "11px").set("color", "#888")
                .set("font-weight", "700").set("letter-spacing", "0.05em");

        String periodo = capitalizar(proxima.getMes().getDisplayName(TextStyle.FULL, new Locale("pt")))
                + " " + proxima.getAno();
        Span titulo = new Span(periodo + " · " + String.format("%.2f €", proxima.getValor()));
        titulo.getStyle().set("font-size", "20px").set("font-weight", "700").set("color", "#2D3436");

        Span venc = new Span("Vence a " + limite.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        venc.getStyle().set("color", "#555").set("font-size", "13px");

        String estadoTxt;
        if (divida) {
            long atraso = ChronoUnit.DAYS.between(limite, LocalDate.now());
            estadoTxt = "⚠️ Em dívida há " + atraso + (atraso == 1 ? " dia" : " dias");
        } else if (dias <= 0) {
            estadoTxt = "Vence hoje";
        } else if (dias <= 7) {
            estadoTxt = "Vence em " + dias + (dias == 1 ? " dia" : " dias");
        } else {
            estadoTxt = "Em dia";
        }
        Span badge = new Span(estadoTxt);
        badge.getStyle().set("background", cor).set("color", "white")
                .set("padding", "3px 10px").set("border-radius", "12px")
                .set("font-size", "12px").set("font-weight", "700")
                .set("margin-top", "6px").set("width", "fit-content");

        card.add(label, titulo, venc, badge);
        return card;
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

    private VerticalLayout criarTabMensalidades(List<Mensalidade> mensalidades, Studio studio) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSizeFull();

        Grid<Mensalidade> grid = new Grid<>(Mensalidade.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(m -> m.getMes().getDisplayName(TextStyle.SHORT, new Locale("pt"))
                + " " + m.getAno()).setHeader("Período").setAutoWidth(true);
        grid.addColumn(m -> String.format("%.2f €", m.getValor())).setHeader("Valor").setAutoWidth(true);
        grid.addComponentColumn(m -> {
            String[] cfg = switch (mensalidadeConfig.estadoEfetivo(m, studio)) {
                case PAGO -> new String[]{"#e8f5e9","#27AE60","Pago"};
                case FATURADO -> new String[]{"#e3f2fd","#1976D2","Faturado"};
                case POR_EMITIR -> new String[]{"#fff3e0","#E67E22","Por Emitir"};
                case EM_DIVIDA -> new String[]{"#fce4ec","#C62828","Em Dívida"};
            };
            Span b = new Span(cfg[2]);
            b.getStyle().set("background",cfg[0]).set("color",cfg[1])
                    .set("padding","2px 8px").set("border-radius","10px")
                    .set("font-size","11px").set("font-weight","600");
            return b;
        }).setHeader("Estado").setAutoWidth(true);

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
            vazio.getStyle().set("color", "#888");
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
        card.getStyle()
                .set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.07)")
                .set("padding", "16px").set("margin-bottom", "8px");
        card.setSpacing(false);

        Span periodo = new Span(av.getPeriodo() + " · " + av.getTurma().getDescricao());
        periodo.getStyle().set("font-weight", "700").set("font-size", "14px");

        String nivelCor = switch (av.getNivel()) {
            case INICIANTE -> "#E67E22"; case INTERMEDIO -> "#1976D2";
            case AVANCADO -> "#7B1FA2"; case EXCELENTE -> "#27AE60";
        };
        Span nivel = new Span(av.getNivel().name());
        nivel.getStyle().set("background", nivelCor).set("color", "white")
                .set("padding", "2px 8px").set("border-radius", "10px")
                .set("font-size", "11px").set("font-weight", "700").set("margin-left", "8px");

        HorizontalLayout cabecalho = new HorizontalLayout(periodo, nivel);
        cabecalho.setAlignItems(FlexComponent.Alignment.CENTER);

        // Competências
        if (av.getCompetencias() != null && !av.getCompetencias().isBlank()) {
            HorizontalLayout comps = new HorizontalLayout();
            comps.setSpacing(true);
            comps.getStyle().set("flex-wrap", "wrap").set("margin-top", "8px");
            AvaliacoesView.parseCompetencias(av.getCompetencias()).forEach((k, v) -> {
                Span s = new Span(k + ": " + "★".repeat(v) + "☆".repeat(5 - v));
                s.getStyle().set("font-size", "12px").set("background", "#f5f5f5")
                        .set("padding", "3px 8px").set("border-radius", "8px");
                comps.add(s);
            });
            card.add(cabecalho, comps);
        } else {
            card.add(cabecalho);
        }

        if (av.getObservacoes() != null && !av.getObservacoes().isBlank()) {
            Span obs = new Span("\"" + av.getObservacoes() + "\"");
            obs.getStyle().set("font-style", "italic").set("color", "#666")
                    .set("font-size", "13px").set("margin-top", "6px");
            card.add(obs);
        }

        return card;
    }

    private VerticalLayout criarTabContratos(Aluno aluno, ContratoDigitalRepository contratoRepo) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);

        List<ContratoDigital> contratos = contratoRepo.findByAlunoOrderByDataGeracaoDesc(aluno);

        if (contratos.isEmpty()) {
            Span vazio = new Span("Não existem contratos pendentes.");
            vazio.getStyle().set("color", "#888");
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
            b.getStyle().set("color", assinado ? "#27AE60" : "#E67E22").set("font-weight", "600");
            return b;
        }).setHeader("Estado").setAutoWidth(true);

        grid.addComponentColumn(c -> {
            if ("PENDENTE".equals(c.getEstado())) {
                Button assinar = new Button("Assinar", VaadinIcon.PENCIL.create(),
                        e -> UI.getCurrent().navigate("contrato/" + c.getId()));
                assinar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                assinar.getStyle().set("background-color", ViewUtils.corPrimaria());
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
