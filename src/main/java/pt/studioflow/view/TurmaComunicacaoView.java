package pt.studioflow.view;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Turma;
import pt.studioflow.model.User;
import pt.studioflow.repository.ProfessorRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.UserRepository;
import pt.studioflow.service.EmailService;
import pt.studioflow.service.TurmaService;

@PageTitle("Comunicação | CoreoFlow")
@Route(value = "comunicacao", layout = MainLayout.class)
@RolesAllowed({ "ADMIN", "PROF", "DELEG" })
public class TurmaComunicacaoView extends VerticalLayout {

    private final TurmaRepository turmaRepository;
    private final TurmaService turmaService;
    private final EmailService emailService;
    private final ProfessorRepository professorRepository;
    private final UserRepository userRepository;

    private final Div containerCards = new Div();

    private final Set<Aluno> selecionados = new HashSet<>();
    private final List<Checkbox> listaCheckboxes = new ArrayList<>();

    private ComboBox<Turma> turmaCombo;

    public TurmaComunicacaoView(TurmaRepository turmaRepository,
            TurmaService turmaService,
            EmailService emailService,
            ProfessorRepository professorRepository,
            UserRepository userRepository) {
        this.turmaRepository = turmaRepository;
        this.turmaService = turmaService;
        this.emailService = emailService;
        this.professorRepository = professorRepository;
        this.userRepository = userRepository;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Centro de Comunicação");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);
        configurarCabecalho();

        containerCards.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(180px, 1fr))")
                .set("gap", "20px")
                .set("width", "100%");

        add(containerCards);
    }


    private void configurarCabecalho() {
        turmaCombo = new ComboBox<>("Selecione a Turma");

        // --- LÓGICA DE FILTRAGEM ---
        carregarTurmasPorPermissao();
        // ---------------------------

        turmaCombo.setItemLabelGenerator(Turma::getDescricao);
        turmaCombo.setWidth("300px");
        turmaCombo.addValueChangeListener(e -> atualizarGaleria(e.getValue()));

        Button btnSelecionarTodos = new Button("Selecionar Todos", new Icon(VaadinIcon.CHECK_SQUARE_O),
                e -> setTodos(true));
        btnSelecionarTodos.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button btnLimpar = new Button("Limpar", new Icon(VaadinIcon.ERASER), e -> setTodos(false));
        btnLimpar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

        Button btnEmail = new Button("E-mail Selecionados", new Icon(VaadinIcon.PAPERPLANE),
                e -> abrirModalEmail());
        btnEmail.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button btnZapGrupo = new Button("Abrir Grupo WhatsApp", new Icon(VaadinIcon.CHAT), e -> entrarNoGrupo());
        btnZapGrupo.addThemeVariants(ButtonVariant.LUMO_SUCCESS);

        HorizontalLayout toolbar = new HorizontalLayout(turmaCombo, btnSelecionarTodos, btnLimpar, btnEmail,
                btnZapGrupo);
        toolbar.setAlignItems(Alignment.BASELINE);
        toolbar.setFlexGrow(1, turmaCombo);

        add(toolbar, new Hr());
    }

    private void carregarTurmasPorPermissao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isDelegado = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DELEG"));

        pt.studioflow.model.Studio studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        if (isAdmin || isDelegado) {
            turmaCombo.setItems(studio != null ? turmaRepository.findAllByStudio(studio) : turmaRepository.findAllComplete());

        } else {
            // Regra: firstName do User logado == primeiro nome do Professor
            String login = auth.getName();
            String firstNameLogado = userRepository.findByUsername(login).map(User::getFirstName).orElse("");

            // Procuramos todos os professores e filtramos em memória ou via Query
            // Aqui buscamos o professor cujo primeiro nome coincide com o login
            List<Professor> todosProfessores = studio != null ? professorRepository.findAllByStudio(studio) : professorRepository.findAll();

            Professor professorCorrespondente = todosProfessores.stream()
                    .filter(p -> {
                        String primeiroNomeProf = p.getNome().split(" ")[0];
                        return primeiroNomeProf.equalsIgnoreCase(firstNameLogado);
                    })
                    .findFirst()
                    .orElse(null);

            if (professorCorrespondente != null) {
                List<Turma> turmasDoProf = turmaRepository.findByProfessor(professorCorrespondente);
                turmaCombo.setItems(turmasDoProf);

                // Se só tiver uma turma, seleciona logo
                if (turmasDoProf.size() == 1) {
                    turmaCombo.setValue(turmasDoProf.get(0));
                }

            } else {
                Notification.show("Professor não vinculado ao utilizador logado.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }

        }
    }

    private void setTodos(boolean valor) {
        if (listaCheckboxes.isEmpty()) {
            Notification.show("Não existem alunos para selecionar.");
            return;
        }
        listaCheckboxes.forEach(cb -> cb.setValue(valor));
    }

    private void atualizarGaleria(Turma turma) {
        containerCards.removeAll();
        selecionados.clear();
        listaCheckboxes.clear();

        if (turma == null)
            return;

        List<Aluno> alunos = turmaService.getAlunosDaTurma(turma);
        for (Aluno aluno : alunos) {
            VerticalLayout card = new VerticalLayout();
            card.getStyle()
                    .set("border", "1px solid #e0e0e0")
                    .set("border-radius", "12px")
                    .set("background", "#f9f9f9")
                    .set("box-shadow", "0 2px 5px rgba(0,0,0,0.1)");

            Div avatar = new Div();

            // Supondo que aluno.getFoto() retorne um byte[] (ex: do banco de dados)
            byte[] fotoBytes = aluno.getFoto();

            if (fotoBytes != null && fotoBytes.length > 0) {
                // Cria o recurso de stream a partir dos bytes da foto
                StreamResource resource = new StreamResource("avatar-" + aluno.getId() + ".png",
                        () -> new ByteArrayInputStream(fotoBytes));

                Image image = new Image(resource, "Foto de " + aluno.getNomeCompleto());

                // Ajusta a imagem para cobrir o espaço do avatar perfeitamente
                image.getStyle()
                        .set("width", "100%")
                        .set("height", "100%")
                        .set("object-fit", "cover") // Recorta a imagem proporcionalmente se não for quadrada
                        .set("width", "80px")
                        .set("height", "80px")
                        .set("border-radius", "50%")
                        .set("object-fit", "cover");

                avatar.add(image);
            } else {
                // Se não houver foto, usa o ícone padrão que você já tinha configurado
                Icon defaultIcon = new Icon(VaadinIcon.USER);
                defaultIcon.getStyle()
                        .set("font-size", "40px") // Ajusta o tamanho do ícone para o container de 80px
                        .set("color", "#666");

                avatar.add(defaultIcon);
            }

            Span nome = new Span(aluno.getNomeCompleto());
            nome.getStyle().set("font-size", "0.9em").set("text-align", "center").set("height", "40px");

            Checkbox check = new Checkbox("Selecionar");
            listaCheckboxes.add(check);

            check.addValueChangeListener(v -> {
                if (v.getValue()) {
                    selecionados.add(aluno);
                    card.getStyle().set("border", "2px solid #2ecc71").set("background", "#ebfaf0");
                } else {
                    selecionados.remove(aluno);
                    card.getStyle().set("border", "1px solid #e0e0e0").set("background", "#f9f9f9");
                }
            });

            card.add(avatar, nome, check);
            card.setAlignItems(Alignment.CENTER);
            containerCards.add(card);
        }
    }

    private void entrarNoGrupo() {
        Turma t = turmaCombo.getValue();
        if (t != null && t.getWhatsappGroupLink() != null && !t.getWhatsappGroupLink().isBlank()) {
            UI.getCurrent().getPage().open(t.getWhatsappGroupLink(), "_blank");
        } else {
            Notification.show("Esta turma não tem um link de grupo configurado.", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void abrirModalEmail() {
        if (selecionados.isEmpty()) {
            Notification.show("Selecione os alunos nos cards primeiro!");
            return;
        }

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Compor Mensagem");
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        TextField assunto = new TextField("Assunto");
        assunto.setWidthFull();
        TextArea mensagem = new TextArea("Corpo do E-mail");
        mensagem.setPlaceholder("Escreva aqui a sua mensagem para " + selecionados.size() + " alunos...");
        mensagem.setWidthFull();
        mensagem.setHeight("250px");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        String login = auth.getName();
        String firstNameLogado = userRepository.findByUsername(login).map(User::getFirstName).orElse("");

        // Procuramos todos os professores e filtramos em memória ou via Query
        // Aqui buscamos o professor cujo primeiro nome coincide com o login
        pt.studioflow.model.Studio _studioC = pt.studioflow.config.TenantContext.getCurrentStudio();
        List<Professor> todosProfessores = _studioC != null ? professorRepository.findAllByStudio(_studioC) : professorRepository.findAll();

        Professor professorCorrespondente = todosProfessores.stream()
                .filter(p -> {
                    String primeiroNomeProf = p.getNome().split(" ")[0];
                    return primeiroNomeProf.equalsIgnoreCase(firstNameLogado);
                })
                .findFirst()
                .orElse(null);

        mensagem.setValue("\n\n\n\n\nCumprimentos, \n\n"
                + (professorCorrespondente != null && professorCorrespondente.getNome() != null
                        ? professorCorrespondente.getNome()
                        : ""));

        Button enviar = new Button("Enviar Agora", ev -> {
            List<String> emails = selecionados.stream()
                    .map(Aluno::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .collect(Collectors.toList());

            if (emails.isEmpty()) {
                Notification.show("Nenhum dos alunos selecionados tem e-mail registado.");
                return;
            }

            try {
                emailService.enviarEmailParaLista(professorCorrespondente, emails, assunto.getValue(),
                        mensagem.getValue());
                dialog.close();
                Notification.show("E-mail enviado com sucesso para " + (emails.size() - 3) + " alunos!")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Excepti