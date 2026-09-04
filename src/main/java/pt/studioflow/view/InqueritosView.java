package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Inquerito;
import pt.studioflow.model.RespostaInquerito;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.InqueritoRepository;
import pt.studioflow.repository.RespostaInqueritoRepository;
import pt.studioflow.service.EmailService;
import pt.studioflow.util.InqueritoPerguntas;
import pt.studioflow.util.InqueritoPerguntas.Pergunta;
import pt.studioflow.util.InqueritoPerguntas.Tipo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Route(value = "inqueritos", layout = MainLayout.class)
@PageTitle("Inquéritos | CoreoFlow")
@RolesAllowed("ADMIN")
public class InqueritosView extends VerticalLayout {

    /** Base pública onde o InqueritosView expõe o link de resposta (ver ResponderInqueritoView). */
    private static final String BASE_URL_PUBLICA = "https://app.coreoflow.me/inquerito";

    // Perguntas sugeridas para um novo inquérito (o utilizador pode editar/remover/adicionar antes de criar)
    private static List<Pergunta> perguntasSugeridas() {
        return new ArrayList<>(List.of(
                new Pergunta("q1", "Como avalia as instalações?", Tipo.ESCALA),
                new Pergunta("q2", "Como avalia os professores?", Tipo.ESCALA),
                new Pergunta("q3", "Como avalia a organização do estúdio?", Tipo.ESCALA),
                new Pergunta("q4", "Como avalia a relação qualidade/preço?", Tipo.ESCALA),
                new Pergunta("q5", "Recomendaria o estúdio a amigos?", Tipo.ESCALA),
                new Pergunta("q6", "O que podemos melhorar?", Tipo.TEXTO)));
    }

    private final InqueritoRepository inqueritoRepo;
    private final RespostaInqueritoRepository respostaRepo;
    private final AlunoRepository alunoRepo;
    private final EmailService emailService;
    private Grid<Inquerito> grid;

    public InqueritosView(InqueritoRepository inqueritoRepo,
                          RespostaInqueritoRepository respostaRepo,
                          AlunoRepository alunoRepo,
                          EmailService emailService) {
        this.inqueritoRepo = inqueritoRepo;
        this.respostaRepo = respostaRepo;
        this.alunoRepo = alunoRepo;
        this.emailService = emailService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        add(ViewUtils.toolbar(ViewUtils.botaoNovo("Novo Inquérito", e -> abrirDialogFormulario(null))),
            criarGrid());
        atualizar();
    }

    private Grid<Inquerito> criarGrid() {
        grid = new Grid<>(Inquerito.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addComponentColumn(i -> {
            HorizontalLayout actions = new HorizontalLayout();
            if (i.getEstado() == Inquerito.Estado.RASCUNHO) {
                Button editar = new Button("Editar", VaadinIcon.EDIT.create(),
                        e -> abrirDialogFormulario(i));
                editar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                Button enviar = new Button("Enviar", VaadinIcon.PAPERPLANE.create(),
                        e -> confirmarEnvio(i));
                enviar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                enviar.getStyle().set("background-color", ViewUtils.corPrimaria());
                actions.add(editar, enviar);
            }
            if (i.getEstado() == Inquerito.Estado.ENVIADO) {
                Button link = new Button("Link", VaadinIcon.LINK.create(), e -> mostrarLink(i));
                link.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                Button fechar = new Button("Fechar", e -> {
                    i.setEstado(Inquerito.Estado.FECHADO);
                    i.setDataFecho(LocalDate.now());
                    inqueritoRepo.save(i);
                    atualizar();
                });
                fechar.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL);
                Button resultados = new Button("Resultados", VaadinIcon.BAR_CHART.create(),
                        e -> verResultados(i));
                resultados.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                actions.add(link, fechar, resultados);
            }
            if (i.getEstado() == Inquerito.Estado.FECHADO) {
                Button resultados = new Button("Resultados", VaadinIcon.BAR_CHART.create(),
                        e -> verResultados(i));
                resultados.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                actions.add(resultados);
            }
            Button del = new Button(VaadinIcon.TRASH.create(), e -> {
                inqueritoRepo.delete(i); atualizar();
            });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            actions.add(del);
            return actions;
        }).setHeader("Ações").setAutoWidth(true);

        grid.addColumn(Inquerito::getTitulo).setHeader("Título").setFlexGrow(2).setSortable(true);
        grid.addColumn(i -> i.getAnoLetivo() != null ? i.getAnoLetivo() : "—").setHeader("Ano").setAutoWidth(true);
        grid.addComponentColumn(i -> {
            String[] cfg = switch (i.getEstado()) {
                case RASCUNHO -> new String[]{"#fff3e0","#E67E22","Rascunho"};
                case ENVIADO  -> new String[]{"#e3f2fd","#1976D2","Enviado"};
                case FECHADO  -> new String[]{"#e8f5e9","#27AE60","Fechado"};
            };
            Span b = new Span(cfg[2]);
            b.getStyle().set("background",cfg[0]).set("color",cfg[1])
                    .set("padding","2px 8px").set("border-radius","10px")
                    .set("font-size","11px").set("font-weight","600");
            return b;
        }).setHeader("Estado").setAutoWidth(true);
        grid.addColumn(i -> i.getRespostasCount() + " respostas").setHeader("Respostas").setAutoWidth(true);
        grid.addColumn(i -> i.getDataEnvio() != null
                ? i.getDataEnvio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "—")
                .setHeader("Enviado em").setAutoWidth(true);

        return grid;
    }

    private void confirmarEnvio(Inquerito inquerito) {
        Studio studio = TenantContext.getCurrentStudio();
        List<Aluno> ativos = studio != null
                ? alunoRepo.findAllByStudio(studio).stream()
                        .filter(a -> a.getStatus() == Aluno.AlunoStatus.ATIVO).toList()
                : List.of();
        long comEmail = ativos.stream().filter(a -> a.getEmail() != null && !a.getEmail().isBlank()).count();

        ConfirmDialog cd = new ConfirmDialog();
        cd.setHeader("Enviar Inquérito?");
        cd.setText("Será enviado por email a " + comEmail + " de " + ativos.size() + " alunos ativos.");
        cd.setCancelable(true);
        cd.setConfirmText("Enviar");
        cd.addConfirmListener(e -> {
            // Enviar emails
            List<String> emails = ativos.stream()
                    .map(Aluno::getEmail)
                    .filter(em -> em != null && !em.isBlank())
                    .collect(Collectors.toList());
            if (!emails.isEmpty()) {
                try {
                    String link = linkPublico(inquerito);
                    String corpo = "Olá!\n\nGostaríamos de conhecer a sua opinião sobre "
                            + (studio != null ? studio.getNome() : "o nosso estúdio") + ".\n\n"
                            + "Por favor responda ao nosso inquérito de satisfação "
                            + inquerito.getTitulo() + ":\n\n"
                            + "<a href='" + link + "'>" + link + "</a>\n\n"
                            + "Obrigado pela sua participação!\n";
                    emailService.enviarEmailParaLista(null, emails, inquerito.getTitulo(), corpo);
                } catch (Exception ex) {
                    Notification.show("Erro ao enviar emails: " + ex.getMessage())
                            .addThemeVariants(NotificationVariant.LUMO_WARNING);
                }
            }
            inquerito.setEstado(Inquerito.Estado.ENVIADO);
            inquerito.setDataEnvio(LocalDate.now());
            inqueritoRepo.save(inquerito);
            atualizar();
            Notification.show("Inquérito enviado a " + comEmail + " alunos!")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        cd.open();
    }

    private String linkPublico(Inquerito inquerito) {
        return BASE_URL_PUBLICA + "?id=" + inquerito.getId();
    }

    private void mostrarLink(Inquerito inquerito) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Link do Inquérito");
        dialog.setWidth("440px");

        Span info = new Span("Partilha este link com os alunos (ex: WhatsApp) para responderem ao inquérito.");
        info.getStyle().set("font-size", "12px").set("color", "#666");

        TextField campo = new TextField();
        campo.setWidthFull();
        campo.setValue(linkPublico(inquerito));
        campo.setReadOnly(true);

        dialog.add(new VerticalLayout(info, campo));
        dialog.getFooter().add(new Button("Fechar", e -> dialog.close()));
        dialog.open();
    }

    private void verResultados(Inquerito inquerito) {
        List<RespostaInquerito> respostas = respostaRepo.findByInquerito(inquerito);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Resultados: " + inquerito.getTitulo());
        dialog.setWidth("600px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        if (respostas.isEmpty()) {
            content.add(new Span("Ainda não há respostas."));
        } else {
            // Calcular médias por pergunta (perguntas de escala)
            Map<String, List<Integer>> notasPorPergunta = respostas.stream()
                    .flatMap(r -> parseRespostas(r.getRespostas()).entrySet().stream())
                    .filter(e -> isNumeric(e.getValue()))
                    .collect(Collectors.groupingBy(
                            Map.Entry::getKey,
                            Collectors.mapping(e -> Integer.parseInt(e.getValue()), Collectors.toList())));

            content.add(new Span(respostas.size() + " resposta(s) recebida(s)"));
            content.add(new H3("Médias por Pergunta"));

            notasPorPergunta.forEach((pergunta, notas) -> {
                double media = notas.stream().mapToInt(i -> i).average().orElse(0);
                HorizontalLayout linha = new HorizontalLayout();
                linha.setWidthFull();
                linha.setAlignItems(FlexComponent.Alignment.CENTER);
                Span label = new Span(pergunta);
                label.setWidth("300px");
                Span nota = new Span(String.format("%.1f / 5  " + "★".repeat((int) Math.round(media)), media));
                nota.getStyle().set("color", "#F39C12").set("font-weight", "700");
                linha.add(label, nota);
                content.add(linha);
            });

            // Comentários texto
            List<String> comentarios = respostas.stream()
                    .flatMap(r -> parseRespostas(r.getRespostas()).entrySet().stream())
                    .filter(e -> !isNumeric(e.getValue()) && !e.getValue().isBlank())
                    .map(Map.Entry::getValue)
                    .toList();
            if (!comentarios.isEmpty()) {
                content.add(new H3("Comentários"));
                comentarios.forEach(c -> {
                    Span s = new Span("\"" + c + "\"");
                    s.getStyle().set("font-style","italic").set("color","#555")
                            .set("font-size","13px").set("display","block")
                            .set("padding","4px 0");
                    content.add(s);
                });
            }
        }

        dialog.add(new com.vaadin.flow.component.orderedlayout.Scroller(content));
        dialog.getFooter().add(new Button("Fechar", e -> dialog.close()));
        dialog.open();
    }

    private Map<String, String> parseRespostas(String json) {
        // Parse simples sem Jackson: {"q1":"4","q2":"5"}
        Map<String, String> map = new java.util.LinkedHashMap<>();
        if (json == null || json.isBlank()) return map;
        json = json.trim().replaceAll("[{}]","");
        for (String pair : json.split(",(?=\")")) {
            String[] kv = pair.split("\":\"?", 2);
            if (kv.length == 2) {
                String k = kv[0].replaceAll("\"","").trim();
                String v = kv[1].replaceAll("\"","").trim();
                map.put(k, v);
            }
        }
        return map;
    }

    private boolean isNumeric(String s) {
        try { Integer.parseInt(s.trim()); return true; } catch (Exception e) { return false; }
    }

    /** Uma linha editável do formulário de perguntas: texto + tipo de resposta. */
    private record PerguntaLinha(TextField texto, Select<Tipo> tipo) {
    }

    /**
     * Abre o formulário de criação (existente == null) ou edição (RASCUNHO) de um
     * inquérito, com editor de perguntas: adicionar, remover e escolher o tipo de
     * resposta (escala 1-5 ou texto livre) de cada uma.
     */
    private void abrirDialogFormulario(Inquerito existente) {
        boolean edicao = existente != null;
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(edicao ? "Editar Inquérito" : "Novo Inquérito de Satisfação");
        dialog.setWidth("560px");

        TextField titulo = new TextField("Título");
        titulo.setWidthFull();
        TextField anoLetivo = new TextField("Ano Letivo");
        anoLetivo.setWidthFull();

        if (edicao) {
            titulo.setValue(existente.getTitulo() != null ? existente.getTitulo() : "");
            anoLetivo.setValue(existente.getAnoLetivo() != null ? existente.getAnoLetivo() : "");
        } else {
            titulo.setValue("Inquérito de Satisfação " + LocalDate.now().getYear());
            int ano = LocalDate.now().getYear();
            anoLetivo.setValue((LocalDate.now().getMonthValue() >= 9 ? ano : ano - 1) + "/"
                    + (LocalDate.now().getMonthValue() >= 9 ? ano + 1 : ano));
        }

        Span perguntasLabel = new Span("Perguntas");
        perguntasLabel.getStyle().set("font-weight", "600").set("font-size", "13px");

        VerticalLayout listaPerguntas = new VerticalLayout();
        listaPerguntas.setPadding(false);
        listaPerguntas.setSpacing(true);
        listaPerguntas.setWidthFull();

        List<PerguntaLinha> linhas = new ArrayList<>();

        List<Pergunta> iniciais = edicao ? InqueritoPerguntas.parse(existente.getPerguntas()) : List.of();
        if (iniciais.isEmpty()) iniciais = perguntasSugeridas();
        for (Pergunta p : iniciais) {
            adicionarLinhaPergunta(listaPerguntas, linhas, p.texto(), p.tipo());
        }

        Button adicionar = new Button("+ Adicionar pergunta", e ->
                adicionarLinhaPergunta(listaPerguntas, linhas, "", Tipo.ESCALA));
        adicionar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button salvar = new Button(edicao ? "Guardar" : "Criar", e -> {
            if (titulo.isEmpty()) { Notification.show("Título obrigatório"); return; }
            List<Pergunta> perguntas = new ArrayList<>();
            int idx = 1;
            for (PerguntaLinha linha : linhas) {
                String texto = linha.texto().getValue().trim();
                if (texto.isBlank()) continue;
                perguntas.add(new Pergunta("q" + idx++, texto, linha.tipo().getValue()));
            }
            if (perguntas.isEmpty()) {
                Notification.show("Adiciona pelo menos uma pergunta.")
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
                return;
            }
            Inquerito i = edicao ? existente : new Inquerito();
            i.setTitulo(titulo.getValue().trim());
            i.setAnoLetivo(anoLetivo.getValue().trim());
            i.setPerguntas(InqueritoPerguntas.toJson(perguntas));
            if (!edicao) i.setStudio(TenantContext.getCurrentStudio());
            inqueritoRepo.save(i);
            atualizar();
            dialog.close();
            Notification.show(edicao ? "Inquérito atualizado!"
                            : "Inquérito criado! Edita e envia quando estiveres pronto.")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        salvar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout conteudo = new VerticalLayout(
                new FormLayout(titulo, anoLetivo), perguntasLabel, listaPerguntas, adicionar);
        conteudo.setPadding(false);
        conteudo.setSpacing(true);

        dialog.add(new com.vaadin.flow.component.orderedlayout.Scroller(conteudo));
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), salvar);
        dialog.open();
    }

    private void adicionarLinhaPergunta(VerticalLayout container, List<PerguntaLinha> linhas,
            String texto, Tipo tipo) {
        TextField campoTexto = new TextField();
        campoTexto.setPlaceholder("Escreve a pergunta...");
        campoTexto.setValue(texto);
        campoTexto.setWidthFull();

        Select<Tipo> campoTipo = new Select<>();
        campoTipo.setItems(Tipo.ESCALA, Tipo.TEXTO);
        campoTipo.setItemLabelGenerator(t -> t == Tipo.ESCALA ? "Escala (1-5)" : "Texto livre");
        campoTipo.setValue(tipo);
        campoTipo.setWidth("150px");

        PerguntaLinha linha = new PerguntaLinha(campoTexto, campoTipo);
        linhas.add(linha);

        HorizontalLayout row = new HorizontalLayout(campoTexto, campoTipo);
        row.setWidthFull();
        row.setFlexGrow(1, campoTexto);
        row.setAlignItems(FlexComponent.Alignment.CENTER);

        Button remover = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> {
            linhas.remove(linha);
            container.remove(row);
        });
        remover.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        row.add(remover);

        container.add(row);
    }

    private void atualizar() {
        Studio s = TenantContext.getCurrentStudio();
        grid.setItems(s != null ? inqueritoRepo.findByStudioOrderByDataCriacaoDesc(s)
                                 : inqueritoRepo.findAll());
    }
}
