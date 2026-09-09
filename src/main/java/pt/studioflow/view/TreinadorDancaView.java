package pt.studioflow.view;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.PlanoDanca;
import pt.studioflow.model.User;
import pt.studioflow.service.AuthService;
import pt.studioflow.service.TreinadorDancaService;
import pt.studioflow.service.TreinadorDancaService.MensagemChat;
import pt.studioflow.service.TreinadorDancaService.Passo;
import pt.studioflow.service.TreinadorDancaService.PlanoConteudo;
import pt.studioflow.service.YoutubeService;

/**
 * Treinador de Dança IA: gera planos de aprendizagem progressivos (GROQ),
 * com vídeos de tutoriais do YouTube por passo e um chat com o treinador.
 * Disponível para professores e alunos do estúdio.
 */
@Route(value = "treinador-ia", layout = MainLayout.class)
@PageTitle("Treinador IA | CoreoFlow")
@RolesAllowed({ "ADMIN", "PROF", "DELEG", "ALUNO" })
public class TreinadorDancaView extends VerticalLayout {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TreinadorDancaService service;
    private final AuthService authService;

    private final String email;
    private final String nome;
    private final boolean podeVerTodos;

    private final ComboBox<String> estilo = new ComboBox<>("Estilo de dança");
    private final ComboBox<String> nivel = new ComboBox<>("Nível");
    private final TextField objetivo = new TextField("Objetivo (opcional)");
    private final TextField paraQuem = new TextField("Para quem (opcional)");
    private final ComboBox<String> filtro = new ComboBox<>();
    private final VerticalLayout listaPlanos = new VerticalLayout();
    private final VerticalLayout detalhe = new VerticalLayout();

    public TreinadorDancaView(TreinadorDancaService service, AuthService authService) {
        this.service = service;
        this.authService = authService;

        User u = authService.getCurrentUser().orElse(null);
        this.email = u != null && u.getEmail() != null ? u.getEmail()
                : (u != null ? u.getUsername() : "—");
        this.nome = u != null && u.getFirstName() != null && !u.getFirstName().isBlank()
                ? u.getFirstName() : email;
        this.podeVerTodos = temPapel("ADMIN") || temPapel("PROF") || temPapel("DELEG");

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Treinador de Dança IA");
        titulo.getStyle().set("margin", "0");
        Span sub = new Span("Planos de aprendizagem passo a passo, com tutoriais e um treinador para tirar dúvidas.");
        sub.getStyle().set("color", "#6b7280").set("font-size", "13px");
        add(titulo, sub);

        if (!service.disponivel()) {
            add(avisoIndisponivel());
            return;
        }

        add(cartaoGerar());

        HorizontalLayout corpo = new HorizontalLayout();
        corpo.setSizeFull();
        corpo.setSpacing(true);

        VerticalLayout col1 = new VerticalLayout();
        col1.setPadding(false);
        col1.setWidth("340px");
        col1.setMinWidth("300px");
        col1.getStyle().set("flex", "0 0 auto");
        filtro.setItems(podeVerTodos ? List.of("Os meus planos", "Todos do estúdio") : List.of("Os meus planos"));
        filtro.setValue("Os meus planos");
        filtro.setWidthFull();
        filtro.addValueChangeListener(e -> recarregarLista());
        H3 h = new H3("Planos");
        h.getStyle().set("margin", "4px 0");
        listaPlanos.setPadding(false);
        listaPlanos.setSpacing(true);
        Scroller sc = new Scroller(listaPlanos);
        sc.setSizeFull();
        col1.add(h, filtro, sc);
        col1.expand(sc);

        detalhe.setPadding(false);
        detalhe.setSpacing(true);
        detalhe.setSizeFull();
        Scroller scD = new Scroller(detalhe);
        scD.setSizeFull();

        corpo.add(col1, scD);
        corpo.expand(scD);
        add(corpo);
        expand(corpo);

        recarregarLista();
        detalhe.add(vazio("Escolhe um plano à esquerda ou gera um novo."));
    }

    // ---------- Cartão de geração ----------

    private Component cartaoGerar() {
        estilo.setAllowCustomValue(true);
        estilo.setItems("Kizomba", "Semba", "Salsa", "Bachata", "Hip-Hop", "Contemporâneo",
                "Ballet", "Jazz", "Danças de Salão", "Afro", "Dancehall", "Tango");
        estilo.setPlaceholder("escolhe ou escreve");
        estilo.addCustomValueSetListener(e -> estilo.setValue(e.getDetail()));
        nivel.setItems("Iniciante", "Intermédio", "Avançado");
        nivel.setValue("Iniciante");
        objetivo.setPlaceholder("ex.: soltar a anca, preparar uma coreografia");
        objetivo.setWidthFull();
        paraQuem.setPlaceholder("ex.: eu, turma de sábado, Maria S.");

        HorizontalLayout campos = new HorizontalLayout(estilo, nivel, paraQuem);
        campos.getStyle().set("flex-wrap", "wrap");

        Button gerar = new Button("Gerar plano", VaadinIcon.MAGIC.create(), e -> gerar());
        gerar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout card = card();
        card.add(campos, objetivo, gerar);
        return card;
    }

    private void gerar() {
        if (estilo.getValue() == null || estilo.getValue().isBlank()) {
            Notification.show("Indica o estilo de dança.");
            return;
        }
        ProgressBar pb = new ProgressBar();
        pb.setIndeterminate(true);
        detalhe.removeAll();
        detalhe.add(new Span("A criar o plano com a IA… (pode demorar alguns segundos)"), pb);
        try {
            PlanoDanca p = service.criarPlano(estilo.getValue().trim(),
                    nivel.getValue() == null ? "Iniciante" : nivel.getValue(),
                    objetivo.getValue(), paraQuem.getValue(), email, nome);
            recarregarLista();
            abrir(p.getId());
            Notification.show("Plano criado.").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (Exception ex) {
            detalhe.removeAll();
            detalhe.add(vazio("Não foi possível gerar o plano: " + ex.getMessage()));
            Notification.show("Falha ao gerar: " + ex.getMessage(), 6000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    // ---------- Lista ----------

    private void recarregarLista() {
        listaPlanos.removeAll();
        List<PlanoDanca> planos = service.listar(
                podeVerTodos && "Todos do estúdio".equals(filtro.getValue()), email);
        if (planos.isEmpty()) {
            listaPlanos.add(vazio("Ainda não há planos."));
            return;
        }
        for (PlanoDanca p : planos) {
            listaPlanos.add(cartaoPlano(p));
        }
    }

    private Component cartaoPlano(PlanoDanca p) {
        Span t = new Span(p.getEstilo() + " · " + p.getNivel());
        t.getStyle().set("font-weight", "700");
        Span m = new Span((p.getParaQuem() != null && !p.getParaQuem().isBlank() ? p.getParaQuem() + " · " : "")
                + (p.getDataCriacao() != null ? p.getDataCriacao().format(DT) : ""));
        m.getStyle().set("font-size", "11px").set("color", "#6b7280");
        VerticalLayout c = new VerticalLayout(t, m);
        c.setPadding(false);
        c.setSpacing(false);
        c.getStyle().set("padding", "10px 12px").set("background", "white")
                .set("border-radius", "10px").set("box-shadow", "0 1px 4px rgba(0,0,0,0.08)")
                .set("cursor", "pointer");
        c.addClickListener(e -> abrir(p.getId()));
        return c;
    }

    // ---------- Detalhe ----------

    private void abrir(Long planoId) {
        detalhe.removeAll();
        PlanoDanca p = service.obter(planoId);
        if (p == null) {
            detalhe.add(vazio("Plano não encontrado."));
            return;
        }
        PlanoConteudo c = service.conteudo(p);

        HorizontalLayout topo = new HorizontalLayout();
        topo.setWidthFull();
        topo.setAlignItems(FlexComponent.Alignment.CENTER);
        H3 h = new H3(p.getEstilo() + " · " + p.getNivel()
                + (c.duracaoSemanas() > 0 ? "  ·  " + c.duracaoSemanas() + " semanas" : ""));
        h.getStyle().set("margin", "0");
        topo.add(h);
        topo.expand(h);
        Button apagar = new Button("Apagar", VaadinIcon.TRASH.create(), e -> confirmarApagar(p));
        apagar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        topo.add(apagar);
        detalhe.add(topo);

        if (p.getObjetivo() != null && !p.getObjetivo().isBlank()) {
            Span o = new Span("🎯 " + p.getObjetivo());
            o.getStyle().set("color", "#374151").set("font-size", "13px");
            detalhe.add(o);
        }
        if (!c.resumo().isBlank()) {
            Paragraph r = new Paragraph(c.resumo());
            r.getStyle().set("margin", "2px 0");
            detalhe.add(r);
        }

        // progresso
        long dominados = 0;
        for (int i = 0; i < c.passos().size(); i++) {
            if (p.passoDominado(i)) {
                dominados++;
            }
        }
        if (!c.passos().isEmpty()) {
            ProgressBar pb = new ProgressBar(0, c.passos().size(), dominados);
            Span lbl = new Span("Progresso: " + dominados + " / " + c.passos().size() + " passos");
            lbl.getStyle().set("font-size", "12px").set("color", "#6b7280");
            detalhe.add(lbl, pb);
        }

        if (!c.aquecimento().isEmpty()) {
            detalhe.add(bloco("Aquecimento", listaSimples(c.aquecimento())));
        }

        VerticalLayout passos = new VerticalLayout();
        passos.setPadding(false);
        passos.setSpacing(true);
        for (int i = 0; i < c.passos().size(); i++) {
            passos.add(cartaoPasso(p, i, c.passos().get(i)));
        }
        detalhe.add(bloco("Passos", passos));

        if (!c.combinacoes().isEmpty()) {
            VerticalLayout cb = new VerticalLayout();
            cb.setPadding(false);
            cb.setSpacing(false);
            c.combinacoes().forEach(x -> {
                Span n = new Span("• " + x.nome());
                n.getStyle().set("font-weight", "600");
                Paragraph d = new Paragraph(x.descricao());
                d.getStyle().set("margin", "0 0 6px 12px").set("font-size", "13px");
                cb.add(n, d);
            });
            detalhe.add(bloco("Combinações", cb));
        }

        if (!c.praticaSemanal().isEmpty()) {
            detalhe.add(bloco("Plano de prática", listaSimples(c.praticaSemanal())));
        }

        detalhe.add(painelChat(p));
    }

    private Component cartaoPasso(PlanoDanca p, int idx, Passo passo) {
        Checkbox dom = new Checkbox(passo.nome());
        dom.setValue(p.passoDominado(idx));
        dom.getStyle().set("font-weight", "700");
        dom.addValueChangeListener(e -> {
            service.marcarPasso(p.getId(), idx, e.getValue());
            abrir(p.getId());
        });

        Details d = new Details();
        d.setSummary(dom);
        d.setWidthFull();
        d.getElement().getStyle().set("background", "white").set("border-radius", "10px")
                .set("box-shadow", "0 1px 4px rgba(0,0,0,0.08)").set("padding", "4px 8px");

        boolean[] carregado = { false };
        d.addOpenedChangeListener(e -> {
            if (e.isOpened() && !carregado[0]) {
                carregado[0] = true;
                d.add(conteudoPasso(p, idx, passo));
            }
        });
        return d;
    }

    private Component conteudoPasso(PlanoDanca plano, int idx, Passo passo) {
        VerticalLayout v = new VerticalLayout();
        v.setPadding(false);
        v.setSpacing(false);
        if (!passo.descricao().isBlank()) {
            v.add(paragrafo(passo.descricao()));
        }
        if (!passo.errosComuns().isBlank()) {
            v.add(linhaIcone("⚠️ Erros comuns: " + passo.errosComuns()));
        }
        if (!passo.dica().isBlank()) {
            v.add(linhaIcone("💡 Dica: " + passo.dica()));
        }
        List<YoutubeService.Video> vids = passo.videos();
        if (vids != null && !vids.isEmpty()) {
            HorizontalLayout tuts = new HorizontalLayout();
            tuts.getStyle().set("flex-wrap", "wrap").set("gap", "10px").set("margin-top", "6px");
            for (YoutubeService.Video vid : vids) {
                tuts.add(cartaoVideo(vid));
            }
            v.add(tuts);
        } else if (!passo.pesquisaYoutube().isBlank()) {
            Anchor a = new Anchor("https://www.youtube.com/results?search_query="
                    + java.net.URLEncoder.encode(passo.pesquisaYoutube(), java.nio.charset.StandardCharsets.UTF_8),
                    "🔎 Procurar tutoriais no YouTube");
            a.setTarget("_blank");
            a.getStyle().set("font-size", "13px");
            v.add(a);
        }

        Button avatar = new Button("Ver com avatar 3D", VaadinIcon.USER.create(),
                e -> abrirAvatar(plano, idx, passo));
        avatar.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        Button praticar = new Button("Praticar com a câmara", VaadinIcon.CAMERA.create(),
                e -> abrirPratica(plano, idx, passo));
        praticar.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout acoes = new HorizontalLayout(avatar, praticar);
        acoes.getStyle().set("margin-top", "8px").set("flex-wrap", "wrap");
        v.add(acoes);
        return v;
    }

    // ---------- Avatar 3D ----------

    private String avatarContId;

    private void abrirAvatar(PlanoDanca plano, int idx, Passo passo) {
        avatarContId = "cfava-" + Long.toHexString(System.nanoTime());

        com.vaadin.flow.component.dialog.Dialog dlg = new com.vaadin.flow.component.dialog.Dialog();
        dlg.setHeaderTitle("Avatar: " + passo.nome());
        dlg.setWidth("660px");
        dlg.setDraggable(true);
        dlg.setResizable(true);

        com.vaadin.flow.component.html.Div cont = new com.vaadin.flow.component.html.Div();
        cont.setId(avatarContId);
        cont.setWidthFull();
        cont.setMinHeight("440px");
        dlg.add(cont);

        Button fechar = new Button("Fechar", e -> {
            getElement().executeJs("window.cfAvatar && window.cfAvatar.parar()");
            dlg.close();
        });
        dlg.getFooter().add(fechar);
        dlg.addDialogCloseActionListener(e -> {
            getElement().executeJs("window.cfAvatar && window.cfAvatar.parar()");
            dlg.close();
        });
        dlg.open();

        String estiloPlano = plano.getEstilo() != null ? plano.getEstilo() : "";
        getElement().executeJs(scriptAvatar());
        getElement().executeJs(
                "setTimeout(function(){ window.cfAvatar && window.cfAvatar.iniciar($0, {estilo: $1}); }, 60);",
                avatarContId, estiloPlano);

        // Narração gerada em segundo plano (não bloqueia a UI).
        final com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        final String cid = avatarContId;
        String ctx;
        try {
            ctx = service.contextoPassoPratica(plano.getId(), idx);
        } catch (Exception e) {
            ctx = passo.nome() + ". " + passo.descricao();
        }
        final String fctx = ctx;
        if (ui != null) {
            ui.setPollInterval(1200);
        }
        Thread t = new Thread(() -> {
            String texto;
            try {
                texto = service.narrarPasso(fctx);
            } catch (Exception e) {
                texto = passo.descricao() != null && !passo.descricao().isBlank()
                        ? passo.descricao()
                        : "Segue o robô e mantém o tempo.";
            }
            final String f = texto;
            try {
                if (ui != null) {
                    ui.access(() -> {
                        getElement().executeJs("window.cfAvatar && window.cfAvatar.narrar($0, $1)", cid, f);
                        ui.setPollInterval(-1);
                    });
                }
            } catch (Exception ignore) {
                // diálogo já fechado
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static String scriptAvatarCache;

    private static synchronized String scriptAvatar() {
        if (scriptAvatarCache == null) {
            try (var in = TreinadorDancaView.class.getResourceAsStream("/js/treinador-avatar.js")) {
                scriptAvatarCache = in == null ? ""
                        : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                scriptAvatarCache = "";
            }
        }
        return scriptAvatarCache;
    }

    // ---------- Modo prática (câmara + pose) ----------

    private Long praticaPlanoId;
    private int praticaPassoIdx;
    private String praticaContId;

    private void abrirPratica(PlanoDanca plano, int idx, Passo passo) {
        praticaPlanoId = plano.getId();
        praticaPassoIdx = idx;
        praticaContId = "cfprat-" + Long.toHexString(System.nanoTime());

        com.vaadin.flow.component.dialog.Dialog dlg = new com.vaadin.flow.component.dialog.Dialog();
        dlg.setHeaderTitle("Praticar: " + passo.nome());
        dlg.setWidth("640px");
        dlg.setDraggable(true);
        dlg.setResizable(true);

        Span nota = new Span("A câmara é processada só no teu dispositivo — nada é gravado nem enviado. "
                + "Só as métricas resumidas vão para o treinador.");
        nota.getStyle().set("font-size", "12px").set("color", "#6b7280");

        com.vaadin.flow.component.html.Div cont = new com.vaadin.flow.component.html.Div();
        cont.setId(praticaContId);
        cont.setMinHeight("420px");
        cont.setWidthFull();

        dlg.add(new VerticalLayout(nota, cont));

        Button fechar = new Button("Terminar", e -> {
            getElement().executeJs("window.cfPratica && window.cfPratica.parar()");
            dlg.close();
        });
        dlg.getFooter().add(fechar);
        dlg.addDialogCloseActionListener(e -> {
            getElement().executeJs("window.cfPratica && window.cfPratica.parar()");
            dlg.close();
        });
        dlg.open();

        String estiloPlano = plano.getEstilo() != null ? plano.getEstilo() : "";
        getElement().executeJs(scriptPratica());
        getElement().executeJs(
                "setTimeout(function(){ window.cfPratica && window.cfPratica.iniciar($0, $1, {estilo: $2}); }, 60);",
                praticaContId, getElement(), estiloPlano);
    }

    @com.vaadin.flow.component.ClientCallable
    public void analisarPratica(String metricasJson) {
        final com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        final String cid = praticaContId;
        String ctx;
        try {
            ctx = service.contextoPassoPratica(praticaPlanoId, praticaPassoIdx);
        } catch (Exception e) {
            ctx = "";
        }
        final String fctx = ctx;

        // O pedido à IA corre fora do thread da UI (pode demorar alguns segundos);
        // ativa-se polling para a resposta chegar sem precisar de @Push.
        if (ui != null) {
            ui.setPollInterval(1200);
        }
        Thread t = new Thread(() -> {
            String texto;
            try {
                texto = service.analisarMovimento(fctx, metricasJson);
            } catch (Exception e) {
                texto = "Não foi possível analisar agora: "
                        + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            final String f = texto;
            try {
                if (ui != null) {
                    ui.access(() -> {
                        getElement().executeJs("window.cfPratica && window.cfPratica.mostrar($0, $1)", cid, f);
                        ui.setPollInterval(-1);
                    });
                }
            } catch (Exception ignore) {
                // UI já fechada — nada a fazer
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static String scriptCache;

    private static synchronized String scriptPratica() {
        if (scriptCache == null) {
            try (var in = TreinadorDancaView.class.getResourceAsStream("/js/treinador-pratica.js")) {
                scriptCache = in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                scriptCache = "";
            }
        }
        return scriptCache;
    }

    private Component cartaoVideo(YoutubeService.Video v) {
        com.vaadin.flow.component.html.Div thumb = new com.vaadin.flow.component.html.Div();
        thumb.getStyle().set("position", "relative").set("width", "200px").set("border-radius", "8px")
                .set("overflow", "hidden");
        Image img = new Image(v.thumbnail(), v.titulo());
        img.setWidth("200px");
        img.getStyle().set("display", "block");
        Span play = new Span("▶");
        play.getStyle().set("position", "absolute").set("left", "0").set("top", "0").set("right", "0")
                .set("bottom", "0").set("display", "flex").set("align-items", "center")
                .set("justify-content", "center").set("font-size", "32px").set("color", "white")
                .set("text-shadow", "0 2px 10px rgba(0,0,0,0.7)");
        thumb.add(img, play);

        Span t = new Span(v.titulo());
        t.getStyle().set("font-size", "12px").set("max-width", "200px").set("display", "block");
        Span ch = new Span(v.canal());
        ch.getStyle().set("font-size", "11px").set("color", "#6b7280");
        VerticalLayout box = new VerticalLayout(thumb, t, ch);
        box.setPadding(false);
        box.setSpacing(false);
        box.setWidth("200px");
        box.getStyle().set("cursor", "pointer");
        box.addClickListener(e -> abrirVideo(v));
        return box;
    }

    private void abrirVideo(YoutubeService.Video v) {
        com.vaadin.flow.component.dialog.Dialog d = new com.vaadin.flow.component.dialog.Dialog();
        d.setHeaderTitle(v.titulo());
        d.setDraggable(true);
        d.setResizable(true);

        com.vaadin.flow.component.html.IFrame frame = new com.vaadin.flow.component.html.IFrame(
                "https://www.youtube.com/embed/" + v.id() + "?autoplay=1&rel=0&modestbranding=1");
        frame.setSizeFull();
        frame.getStyle().set("border", "0").set("display", "block");
        frame.getElement().setAttribute("allow",
                "autoplay; encrypted-media; picture-in-picture; fullscreen");
        frame.getElement().setAttribute("allowfullscreen", true);

        com.vaadin.flow.component.html.Div wrap = new com.vaadin.flow.component.html.Div(frame);
        wrap.setSizeFull();
        wrap.getStyle().set("background", "#000");
        d.add(wrap);

        String largura = "780px";
        String altura = "480px";
        d.setWidth(largura);
        d.setHeight(altura);

        boolean[] max = { false };
        Button expandir = new Button(VaadinIcon.EXPAND_FULL.create());
        expandir.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        expandir.getElement().setAttribute("title", "Aumentar / reduzir");
        expandir.addClickListener(e -> {
            max[0] = !max[0];
            d.setWidth(max[0] ? "96vw" : largura);
            d.setHeight(max[0] ? "92vh" : altura);
            expandir.setIcon((max[0] ? VaadinIcon.COMPRESS : VaadinIcon.EXPAND_FULL).create());
        });

        Anchor abrirYt = new Anchor(v.urlWatch(), "YouTube ↗");
        abrirYt.setTarget("_blank");
        abrirYt.getStyle().set("font-size", "12px").set("align-self", "center").set("margin-right", "4px");

        d.getHeader().add(abrirYt, expandir);
        d.getFooter().add(new Button("Fechar", e -> d.close()));
        d.open();
    }

    // ---------- Chat ----------

    private Component painelChat(PlanoDanca p) {
        VerticalLayout wrap = new VerticalLayout();
        wrap.setPadding(false);
        wrap.setSpacing(false);

        VerticalLayout mensagens = new VerticalLayout();
        mensagens.setPadding(false);
        mensagens.setSpacing(false);
        mensagens.getStyle().set("max-height", "280px").set("overflow-y", "auto")
                .set("background", "#f9fafb").set("border-radius", "10px").set("padding", "10px");
        for (MensagemChat m : service.historicoChat(p)) {
            mensagens.add(balao(m));
        }
        if (mensagens.getComponentCount() == 0) {
            mensagens.add(vazio("Pergunta ao treinador sobre qualquer passo do plano."));
        }

        TextField campo = new TextField();
        campo.setPlaceholder("Escreve a tua pergunta…");
        campo.setWidthFull();
        Button enviar = new Button(VaadinIcon.PAPERPLANE.create());
        enviar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Runnable acao = () -> {
            String q = campo.getValue() == null ? "" : campo.getValue().trim();
            if (q.isEmpty()) {
                return;
            }
            campo.clear();
            campo.setEnabled(false);
            enviar.setEnabled(false);
            try {
                service.responder(p.getId(), q);
            } catch (Exception ex) {
                Notification.show("Falha: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
            campo.setEnabled(true);
            enviar.setEnabled(true);
            mensagens.removeAll();
            for (MensagemChat m : service.historicoChat(p)) {
                mensagens.add(balao(m));
            }
            campo.focus();
        };
        enviar.addClickListener(e -> acao.run());
        campo.addKeyPressListener(com.vaadin.flow.component.Key.ENTER, e -> acao.run());
        HorizontalLayout entrada = new HorizontalLayout(campo, enviar);
        entrada.setWidthFull();
        entrada.expand(campo);

        wrap.add(mensagens, entrada);
        return bloco("Treinador", wrap);
    }

    private Component balao(MensagemChat m) {
        boolean treinador = "treinador".equals(m.autor());
        Span s = new Span(m.texto());
        s.getStyle().set("display", "inline-block").set("padding", "7px 11px")
                .set("border-radius", "12px").set("margin", "4px 0").set("font-size", "13px")
                .set("white-space", "pre-wrap").set("max-width", "85%")
                .set("background", treinador ? "#e0f2fe" : "#ffffff")
                .set("border", "1px solid " + (treinador ? "#bae6fd" : "#e5e7eb"));
        VerticalLayout row = new VerticalLayout(s);
        row.setPadding(false);
        row.setSpacing(false);
        row.setAlignItems(treinador ? FlexComponent.Alignment.START : FlexComponent.Alignment.END);
        return row;
    }

    // ---------- helpers ----------

    private void confirmarApagar(PlanoDanca p) {
        ConfirmDialog cd = new ConfirmDialog();
        cd.setHeader("Apagar plano");
        cd.setText("Apagar o plano de " + p.getEstilo() + " (" + p.getNivel() + ")?");
        cd.setCancelable(true);
        cd.setConfirmText("Apagar");
        cd.setConfirmButtonTheme("error primary");
        cd.addConfirmListener(e -> {
            service.apagar(p.getId());
            recarregarLista();
            detalhe.removeAll();
            detalhe.add(vazio("Plano apagado."));
        });
        cd.open();
    }

    private boolean temPapel(String role) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> {
            String v = a.getAuthority();
            return v.equals(role) || v.equals("ROLE_" + role);
        });
    }

    private VerticalLayout card() {
        VerticalLayout v = new VerticalLayout();
        v.setPadding(true);
        v.setSpacing(true);
        v.setWidthFull();
        v.getStyle().set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.07)");
        return v;
    }

    private Component bloco(String titulo, Component conteudo) {
        H3 h = new H3(titulo);
        h.getStyle().set("margin", "10px 0 4px 0").set("font-size", "15px");
        VerticalLayout v = new VerticalLayout(h, conteudo);
        v.setPadding(false);
        v.setSpacing(false);
        return v;
    }

    private Component listaSimples(List<String> itens) {
        VerticalLayout v = new VerticalLayout();
        v.setPadding(false);
        v.setSpacing(false);
        itens.forEach(i -> v.add(paragrafo("• " + i)));
        return v;
    }

    private Paragraph paragrafo(String txt) {
        Paragraph p = new Paragraph(txt);
        p.getStyle().set("margin", "2px 0").set("font-size", "13px");
        return p;
    }

    private Component linhaIcone(String txt) {
        Span s = new Span(txt);
        s.getStyle().set("font-size", "13px").set("color", "#374151").set("display", "block")
                .set("margin", "2px 0");
        return s;
    }

    private Component vazio(String txt) {
        Span s = new Span(txt);
        s.getStyle().set("color", "#9ca3af").set("font-size", "13px").set("padding", "8px");
        return s;
    }

    private Component avisoIndisponivel() {
        VerticalLayout v = card();
        Span s = new Span("O Treinador IA ainda não está ativo. O superadmin pode ligá-lo em "
                + "Configurações da Plataforma (chave GROQ e, opcionalmente, chave do YouTube).");
        s.getStyle().set("color", "#6b7280");
        v.add(s);
        return v;
    }
}
