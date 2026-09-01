package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;
import pt.studioflow.service.EmailService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Route(value = "convites-professor", layout = MainLayout.class)
@UIScope
@RolesAllowed({ "DELEG", "PROF" })
public class ConvitesProfessorView extends VerticalLayout {

    private final ConviteRepository conviteRepository;
    private final TurmaRepository turmaRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;
    private final ProfessorRepository professorRepository;
    private final UserRepository userRepository;
    private final InscricaoEventoRepository inscricaoRepository;
    private final EmailService emailService;

    private Grid<Aluno> gridAlunos;
    private ComboBox<Convite> comboConvites;
    private ComboBox<Turma> comboTurmas;
    private Professor professorLogado;
    private List<Turma> minhasTurmas = new ArrayList<>();

    public ConvitesProfessorView(ConviteRepository conviteRepository, TurmaRepository turmaRepository,
            AlunoTurmaRepository alunoTurmaRepository, ProfessorRepository professorRepository,
            UserRepository userRepository, InscricaoEventoRepository inscricaoRepository,
            EmailService emailService) {

        this.conviteRepository = conviteRepository;
        this.turmaRepository = turmaRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
        this.professorRepository = professorRepository;
        this.userRepository = userRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.emailService = emailService;

        setSizeFull();
        setPadding(true);

        // 1. Identificar Permissões
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isDelegado = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_DELEG") || a.getAuthority().equals("DELEG"));

        String login = auth.getName();

        // Inicializar a lista base de turmas dependendo do papel
        pt.studioflow.model.Studio _studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        if (isDelegado) {
            this.minhasTurmas = _studio != null ? turmaRepository.findAllByStudio(_studio) : turmaRepository.findAll();
        } else {
            String firstName = userRepository.findByPrincipalName(login).map(User::getFirstName).orElse("");
            (_studio != null ? professorRepository.findAllByStudio(_studio) : professorRepository.findAll()).stream()
                    .filter(p -> p.getNome().toLowerCase().contains(firstName.toLowerCase()))
                    .findFirst().ifPresent(p -> {
                        this.professorLogado = p;
                        this.minhasTurmas = turmaRepository.findByProfessor(p);
                    });
        }

        H2 titulo = new H2("Convocatória de Alunos para Eventos");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        // 2. Filtros
        HorizontalLayout filtros = new HorizontalLayout();
        comboConvites = new ComboBox<>("Selecionar Evento");

        // FILTRO DE CONVITES: Só mostra eventos que tenham pelo menos uma turma que VAI
        // participar
        List<Convite> convitesFiltrados = conviteRepository.findAllAtivos().stream()
                .filter(c -> minhasTurmas.stream()
                        .anyMatch(t -> c.getParticipacoes().get(t.getId()) != null &&
                                c.getParticipacoes().get(t.getId()) != StatusParticipacao.NAO_VAI))
                .collect(Collectors.toList());

        comboConvites.setItems(convitesFiltrados);
        comboConvites.setItemLabelGenerator(Convite::getEvento);
        comboConvites.setWidth("300px");

        comboTurmas = new ComboBox<>(isDelegado ? "Turmas Participantes" : "Minha Turma");
        comboTurmas.setItemLabelGenerator(Turma::getDescricao);
        comboTurmas.setEnabled(false);

        filtros.add(comboConvites, comboTurmas);
        add(filtros);

        // 3. Grid de Alunos
        gridAlunos = new Grid<>(Aluno.class, false);
        gridAlunos.setSelectionMode(Grid.SelectionMode.MULTI);
        gridAlunos.addColumn(Aluno::getNomeCompleto).setHeader("Nome do Aluno").setSortable(true);

        gridAlunos.addComponentColumn(aluno -> {
            InscricaoEvento inscricao = inscricaoRepository.findByAlunoAndConvite(aluno, comboConvites.getValue())
                    .orElse(null);

            String texto = "Não Convocado";
            String theme = "badge";

            if (inscricao != null) {
                if (inscricao.isConfirmado()) {
                    texto = "Confirmado";
                    theme = "badge success";
                } else {
                    texto = "Convocado / Pendente";
                    theme = "badge contrast";
                }
            }

            Span badge = new Span(texto);
            badge.getElement().getThemeList().add(theme);
            return badge;
        }).setHeader("Estado da Convocatória");

        add(gridAlunos);

        // 4. Listener Reforçado para a ComboBox de Turmas
        comboConvites.addValueChangeListener(e -> {
            Convite selecionado = e.getValue();
            if (selecionado != null) {
                // FILTRO CRÍTICO: Remove qualquer turma que tenha estado NAO_VAI no mapa de
                // participações
                List<Turma> turmasQueParticipam = minhasTurmas.stream()
                        .filter(t -> {
                            StatusParticipacao status = selecionado.getParticipacoes().get(t.getId());
                            // Só incluímos se o status NÃO for NAO_VAI (e opcionalmente se houver uma
                            // decisão tomada)
                            return status != null && status != StatusParticipacao.NAO_VAI;
                        })
                        .collect(Collectors.toList());

                comboTurmas.setItems(turmasQueParticipam);
                comboTurmas.setEnabled(true);
            } else {
                comboTurmas.setEnabled(false);
                comboTurmas.clear();
            }
            atualizarGrid();
        });

        comboTurmas.addValueChangeListener(e -> atualizarGrid());

        // 5. Botão de Envio
        Button btnEnviarConvocatoria = new Button("Enviar Convocatória por Email", VaadinIcon.PAPERPLANE.create(),
                e -> processarConvocatoria());
        btnEnviarConvocatoria.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(btnEnviarConvocatoria);
    }

    private void atualizarGrid() {
        if (comboTurmas.getValue() != null && comboConvites.getValue() != null) {
            List<Aluno> alunosDaTurma = alunoTurmaRepository.findByTurma(comboTurmas.getValue())
                    .stream().map(AlunoTurma::getAluno).collect(Collectors.toList());
            gridAlunos.setItems(alunosDaTurma);

            gridAlunos.deselectAll();
            for (Aluno a : alunosDaTurma) {
                if (inscricaoRepository.findByAlunoAndConvite(a, comboConvites.getValue()).isPresent()) {
                    gridAlunos.select(a);
                }
            }
        } else {
            gridAlunos.setItems(new ArrayList<>());
        }
    }

    @Transactional
    private void processarConvocatoria() {
        Convite convite = comboConvites.getValue();
        Turma turma = comboTurmas.getValue();
        Set<Aluno> selecionados = gridAlunos.getSelectedItems();

        if (convite == null || turma == null || selecionados.isEmpty()) {
            Notification n = Notification.show("Selecione o Evento, a Turma e os Alunos!");
            n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        try {
            int enviados = 0;
            for (Aluno aluno : selecionados) {
                InscricaoEvento inscricao = inscricaoRepository.findByAlunoAndConvite(aluno, convite)
                        .orElse(new InscricaoEvento(aluno, convite));
                inscricaoRepository.save(inscricao);
                emailService.enviarConvocatoria(aluno, convite);
                enviados++;
            }

            convite.getParticipacoes().put(turma.getId(), StatusParticipacao.CONFIRMADO);
            conviteRepository.save(convite);

            Notification.show("Sucesso! Convocatória enviada para " + enviados + " alunos.")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        } catch (Exception ex) {
            Notification.show("Erro ao enviar convocatória: " + ex.getMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
