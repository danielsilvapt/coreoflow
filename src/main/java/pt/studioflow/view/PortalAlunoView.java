package pt.studioflow.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;
import pt.studioflow.service.AuthService;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Route(value = "portal", layout = MainLayout.class)
@PageTitle("Portal | CoreoFlow")
@RolesAllowed("ALUNO")
public class PortalAlunoView extends VerticalLayout {

    public PortalAlunoView(AuthService authService,
                            AlunoRepository alunoRepo,
                            MensalidadeRepository mensalidadeRepo,
                            PresencaRepository presencaRepo,
                            AvaliacaoAlunoRepository avaliacaoRepo,
                            ContratoDigitalRepository contratoRepo) {

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
                .filter(m -> m.getEstado() == EstadoMensalidade.EM_DIVIDA
                          || m.getEstado() == EstadoMensalidade.POR_EMITIR)
                .count();

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

        tabs.add("📅 Presenças", criarTabPresencas(presencas));
        tabs.add("💳 Mensalidades", criarTabMensalidades(mensalidades));
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

    private VerticalLayout criarTabPresencas(List<Presenca> presencas) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);

        // Últimas 20 presenças
        List<Presenca> ultimas = presencas.stream()
                .filter(p -> p.getData() != null)
                .sorted((a, b) -> b.getData().compareTo(a.getData()))
                .limit(20).toList();

        Grid<Presenca> grid = new Grid<>(Presenca.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setHeight("300px");

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

        grid.setItems(ultimas);
        layout.add(new H3("Últimas Presenças"), grid);
        return layout;
    }

    private VerticalLayout criarTabMensalidades(List<Mensalidade> mensalidades) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);

        Grid<Mensalidade> grid = new Grid<>(Mensalidade.class, false);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setHeight("300px");

        grid.addColumn(m -> m.getMes().getDisplayName(TextStyle.SHORT, new Locale("pt"))
                + " " + m.getAno()).setHeader("Período").setAutoWidth(true);
        grid.addColumn(m -> String.format("%.2f €", m.getValor())).setHeader("Valor").setAutoWidth(true);
        grid.addComponentColumn(m -> {
            String[] cfg = switch (m.getEstado()) {
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
