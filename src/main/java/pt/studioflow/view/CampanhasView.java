package pt.studioflow.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Campanha;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.CampanhaRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.EmailService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Route(value = "campanhas", layout = MainLayout.class)
@PageTitle("Campanhas | CoreoFlow")
@RolesAllowed("ADMIN")
public class CampanhasView extends VerticalLayout {

    private final CampanhaRepository campanhaRepo;
    private final AlunoRepository alunoRepo;
    private final TurmaRepository turmaRepo;
    private final EmailService emailService;

    private Grid<Campanha> grid;

    public CampanhasView(CampanhaRepository campanhaRepo, AlunoRepository alunoRepo,
                         TurmaRepository turmaRepo, EmailService emailService) {
        this.campanhaRepo = campanhaRepo;
        this.alunoRepo = alunoRepo;
        this.turmaRepo = turmaRepo;
        this.emailService = emailService;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Campanhas");
        titulo.getStyle().set("margin", "0 0 8px 0").set("padding", "20px 20px 0 20px");

        add(titulo, ViewUtils.toolbar(ViewUtils.botaoNovo("Nova Campanha", e -> abrirDialog(null))),
            criarGrid());
        atualizar();
    }

    private Grid<Campanha> criarGrid() {
        grid = new Grid<>(Campanha.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addComponentColumn(c -> {
            HorizontalLayout actions = new HorizontalLayout();
            if (c.getEstado() == Campanha.Estado.RASCUNHO) {
                Button editar = new Button(VaadinIcon.EDIT.create(), e -> abrirDialog(c));
                editar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                Button enviar = new Button("Enviar", VaadinIcon.PAPERPLANE.create(),
                        e -> confirmarEnvio(c));
                enviar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                enviar.getStyle().set("background-color", ViewUtils.corPrimaria());
                actions.add(editar, enviar);
            } else {
                Span dataEnvio = new Span(c.getDataEnvio() != null
                        ? c.getDataEnvio().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) : "");
                dataEnvio.getStyle().set("font-size", "11px").set("color", "#888");
                actions.add(dataEnvio);
            }
            Button del = new Button(VaadinIcon.TRASH.create(), e -> {
                campanhaRepo.delete(c); atualizar();
            });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            actions.add(del);
            return actions;
        }).setHeader("Ações").setAutoWidth(true);

        grid.addColumn(c -> c.getDataCriacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .setHeader("Data").setAutoWidth(true).setSortable(true);
        grid.addColumn(Campanha::getTitulo).setHeader("Título").setFlexGrow(2);

        grid.addComponentColumn(c -> {
            String[] cfg = c.getCanal() == Campanha.Canal.EMAIL
                    ? new String[]{"#e3f2fd","#1976D2","📧 Email"}
                    : new String[]{"#e8f5e9","#27AE60","💬 WhatsApp"};
            return badge(cfg[2], cfg[0], cfg[1]);
        }).setHeader("Canal").setAutoWidth(true);

        grid.addComponentColumn(c -> {
            String label = switch (c.getSegmento()) {
                case TODOS -> "Todos";
                case ATIVOS -> "Ativos";
                case INATIVOS -> "Inativos";
                case TURMA -> c.getTurmaAlvo() != null ? c.getTurmaAlvo().getDescricao() : "Turma";
                case ANIVERSARIANTES_MES -> "Aniversariantes";
            };
            return badge(label, "#f5f5f5", "#555");
        }).setHeader("Segmento").setAutoWidth(true);

        grid.addColumn(c -> c.getDestinatariosCount() > 0 ? c.getDestinatariosCount() + " dest." : "—")
                .setHeader("Enviados").setAutoWidth(true);

        grid.addComponentColumn(c -> {
            boolean enviada = c.getEstado() == Campanha.Estado.ENVIADA;
            return badge(enviada ? "Enviada" : "Rascunho",
                    enviada ? "#e8f5e9" : "#fff3e0",
                    enviada ? "#27AE60" : "#E67E22");
        }).setHeader("Estado").setAutoWidth(true);

        return grid;
    }

    private void confirmarEnvio(Campanha c) {
        List<Aluno> destinatarios = resolverDestinatarios(c);
        long comEmail = destinatarios.stream().filter(a -> a.getEmail() != null && !a.getEmail().isBlank()).count();
        long comTel = destinatarios.stream().filter(a -> a.getTelemovel() != null && !a.getTelemovel().isBlank()).count();

        ConfirmDialog cd = new ConfirmDialog();
        cd.setHeader("Confirmar Envio");
        String canal = c.getCanal() == Campanha.Canal.EMAIL ? "email" : "WhatsApp";
        long disponiveis = c.getCanal() == Campanha.Canal.EMAIL ? comEmail : comTel;
        cd.setText("Serão contactados " + disponiveis + " de " + destinatarios.size()
                + " destinatários por " + canal + ". Continuar?");
        cd.setCancelable(true);
        cd.setConfirmText("Enviar");
        cd.addConfirmListener(e -> executarEnvio(c, destinatarios));
        cd.open();
    }

    private void executarEnvio(Campanha c, List<Aluno> destinatarios) {
        int enviados = 0;

        if (c.getCanal() == Campanha.Canal.EMAIL) {
            List<String> emails = destinatarios.stream()
                    .map(Aluno::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .collect(Collectors.toList());
            if (!emails.isEmpty()) {
                try {
                    emailService.enviarEmailParaLista(null, emails, c.getTitulo(), c.getMensagem());
                    enviados = emails.size();
                } catch (Exception ex) {
                    Notification.show("Erro ao enviar email: " + ex.getMessage())
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
            }
        } else {
            // WhatsApp — abrir URLs em série (máx 10 para não bloquear browser)
            List<String> urls = new ArrayList<>();
            for (Aluno a : destinatarios) {
                if (a.getTelemovel() == null || a.getTelemovel().isBlank()) continue;
                String num = a.getTelemovel().replaceAll("\\D", "");
                if (num.length() == 9) num = "351" + num;
                urls.add("https://wa.me/" + num + "?text=" + c.getMensagem().replace(" ", "%20").replace("\n", "%0A"));
                if (urls.size() >= 10) break;
            }
            for (String url : urls) {
                UI.getCurrent().getPage().open(url, "_blank");
            }
            enviados = urls.size();
            if (destinatarios.size() > 10) {
                Notification.show("Foram abertas 10 janelas WhatsApp. Repete para os restantes destinatários.")
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
            }
        }

        c.setEstado(Campanha.Estado.ENVIADA);
        c.setDataEnvio(LocalDateTime.now());
        c.setDestinatariosCount(enviados);
        campanhaRepo.save(c);
        atualizar();
        Notification.show("Campanha enviada para " + enviados + " destinatários!")
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private List<Aluno> resolverDestinatarios(Campanha c) {
        Studio studio = TenantContext.getCurrentStudio();
        List<Aluno> todos = studio != null ? alunoRepo.findAllByStudio(studio) : alunoRepo.findAll();
        return switch (c.getSegmento()) {
            case TODOS -> todos;
            case ATIVOS -> todos.stream().filter(a -> a.getStatus() == Aluno.AlunoStatus.ATIVO).toList();
            case INATIVOS -> todos.stream().filter(a -> a.getStatus() == Aluno.AlunoStatus.INATIVO).toList();
            case TURMA -> c.getTurmaAlvo() != null
                    ? todos.stream().filter(a -> a.getTurmas().stream()
                        .anyMatch(at -> at.getTurma().getId().equals(c.getTurmaAlvo().getId()))).toList()
                    : todos;
            case ANIVERSARIANTES_MES -> todos.stream()
                    .filter(a -> a.getDataNascimento() != null
                            && a.getDataNascimento().getMonth() == LocalDate.now().getMonth())
                    .toList();
        };
    }

    private void abrirDialog(Campanha c) {
        boolean novo = c == null;
        Campanha campanha = novo ? new Campanha() : c;
        Studio studio = TenantContext.getCurrentStudio();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(novo ? "Nova Campanha" : "Editar Campanha");
        dialog.setWidth("560px");
        dialog.setMaxWidth("98vw");

        TextField titulo = new TextField("Título");
        titulo.setWidthFull();
        titulo.setValue(campanha.getTitulo() != null ? campanha.getTitulo() : "");

        ComboBox<Campanha.Canal> canal = new ComboBox<>("Canal");
        canal.setItems(Campanha.Canal.values());
        canal.setItemLabelGenerator(cn -> cn == Campanha.Canal.EMAIL ? "📧 Email" : "💬 WhatsApp");
        canal.setValue(campanha.getCanal());
        canal.setWidthFull();

        ComboBox<Campanha.Segmento> segmento = new ComboBox<>("Segmento");
        segmento.setItems(Campanha.Segmento.values());
        segmento.setItemLabelGenerator(s -> switch (s) {
            case TODOS -> "Todos os alunos";
            case ATIVOS -> "Apenas ativos";
            case INATIVOS -> "Apenas inativos";
            case TURMA -> "Turma específica";
            case ANIVERSARIANTES_MES -> "Aniversariantes do mês";
        });
        segmento.setValue(campanha.getSegmento());
        segmento.setWidthFull();

        ComboBox<Turma> turmaAlvo = new ComboBox<>("Turma");
        List<Turma> turmas = studio != null ? turmaRepo.findAllByStudio(studio) : turmaRepo.findAll();
        turmaAlvo.setItems(turmas);
        turmaAlvo.setItemLabelGenerator(Turma::getDescricao);
        turmaAlvo.setWidthFull();
        turmaAlvo.setVisible(campanha.getSegmento() == Campanha.Segmento.TURMA);
        if (campanha.getTurmaAlvo() != null) turmaAlvo.setValue(campanha.getTurmaAlvo());
        segmento.addValueChangeListener(e -> turmaAlvo.setVisible(e.getValue() == Campanha.Segmento.TURMA));

        TextArea mensagem = new TextArea("Mensagem");
        mensagem.setWidthFull();
        mensagem.setMinHeight("120px");
        mensagem.setValue(campanha.getMensagem() != null ? campanha.getMensagem() : "");

        // Preview de destinatários
        Span preview = new Span();
        preview.getStyle().set("font-size", "12px").set("color", "#666");
        Runnable atualizarPreview = () -> {
            campanha.setSegmento(segmento.getValue());
            campanha.setTurmaAlvo(turmaAlvo.getValue());
            int n = resolverDestinatarios(campanha).size();
            preview.setText("~" + n + " destinatários");
        };
        segmento.addValueChangeListener(e -> atualizarPreview.run());
        turmaAlvo.addValueChangeListener(e -> atualizarPreview.run());
        atualizarPreview.run();

        VerticalLayout content = new VerticalLayout(titulo, canal,
                new HorizontalLayout(segmento, turmaAlvo), mensagem, preview);
        content.setPadding(false);

        Button guardar = new Button("Guardar Rascunho", e -> {
            if (titulo.isEmpty() || mensagem.isEmpty()) {
                Notification.show("Título e mensagem são obrigatórios"); return;
            }
            campanha.setTitulo(titulo.getValue().trim());
            campanha.setCanal(canal.getValue());
            campanha.setSegmento(segmento.getValue());
            campanha.setTurmaAlvo(turmaAlvo.getValue());
            campanha.setMensagem(mensagem.getValue().trim());
            campanha.setStudio(studio);
            campanhaRepo.save(campanha);
            atualizar();
            dialog.close();
            Notification.show("Rascunho guardado").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(content);
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }

    private void atualizar() {
        Studio s = TenantContext.getCurrentStudio();
        grid.setItems(s != null ? campanhaRepo.findByStudioOrderByDataCriacaoDesc(s)
                                 : campanhaRepo.findAll());
    }

    private Span badge(String texto, String bg, String cor) {
        Span b = new Span(texto);
        b.getStyle().set("background", bg).set("color", cor)
                .set("padding", "2px 8px").set("border-radius", "10px")
                .set("font-size", "11px").set("font-weight", "600");
        return b;
    }
}
