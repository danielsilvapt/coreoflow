package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.beans.factory.annotation.Value;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.ContaPortal;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.ContaPortalRepository;
import pt.studioflow.service.EmailService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gestão dos acessos ao portal do aluno/encarregado. Lista os emails dos
 * alunos do estúdio e permite enviar/reenviar o convite onde o destinatário
 * define a password. Um email dá acesso a todos os alunos com esse email.
 */
@Route(value = "contas-portal", layout = MainLayout.class)
@PageTitle("Acessos ao Portal | CoreoFlow")
@RolesAllowed("ADMIN")
public class ContasPortalView extends VerticalLayout {

    private final AlunoRepository alunoRepository;
    private final ContaPortalRepository contaPortalRepository;
    private final EmailService emailService;
    private final String baseUrl;

    private final Grid<Linha> grid = new Grid<>(Linha.class, false);
    private final TextField filtro = new TextField();

    public ContasPortalView(AlunoRepository alunoRepository, ContaPortalRepository contaPortalRepository,
            EmailService emailService, @Value("${app.base-url:https://app.coreoflow.me}") String baseUrl) {
        this.alunoRepository = alunoRepository;
        this.contaPortalRepository = contaPortalRepository;
        this.emailService = emailService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Acessos ao Portal");
        titulo.getStyle().set("margin-top", "0");

        filtro.setPlaceholder("Filtrar por email...");
        filtro.setClearButtonVisible(true);
        filtro.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        filtro.addValueChangeListener(e -> atualizar());
        filtro.setPrefixComponent(VaadinIcon.SEARCH.create());
        filtro.setWidth("280px");

        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.addColumn(Linha::email).setHeader("Email").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(Linha::nomes).setHeader("Alunos").setAutoWidth(true).setFlexGrow(1);
        grid.addComponentColumn(this::estadoBadge).setHeader("Estado").setAutoWidth(true);
        grid.addComponentColumn(this::acoes).setHeader("Ações").setAutoWidth(true).setFlexGrow(0);

        add(titulo, filtro, grid);
        atualizar();
    }

    private Span estadoBadge(Linha l) {
        String texto;
        String cor;
        if (l.conta == null) {
            texto = "Sem acesso";
            cor = "#95a5a6";
        } else if (l.conta.isAtivo()) {
            texto = "Ativo";
            cor = "#27AE60";
        } else {
            texto = "Convite enviado";
            cor = "#E67E22";
        }
        Span s = new Span(texto);
        s.getStyle().set("color", cor).set("font-weight", "600");
        return s;
    }

    private Button acoes(Linha l) {
        boolean ativo = l.conta != null && l.conta.isAtivo();
        Button b = new Button(ativo ? "Repor password" : "Enviar convite",
                VaadinIcon.ENVELOPE.create(), e -> enviarConvite(l));
        b.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return b;
    }

    private void enviarConvite(Linha l) {
        Studio studio = TenantContext.getCurrentStudio();
        if (studio == null) {
            Notification.show("Sem estúdio ativo na sessão.").addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        ContaPortal conta = l.conta != null ? l.conta
                : contaPortalRepository.findByEmailIgnoreCase(l.email()).orElseGet(ContaPortal::new);
        conta.setEmail(l.email());
        conta.setNome(l.nomes());
        conta.setStudio(studio);
        conta.setTokenAtivacao(UUID.randomUUID().toString().replace("-", ""));
        conta.setTokenExpiraEm(LocalDateTime.now().plusDays(7));
        // Numa reposição de password mantém-se ativo=true (a password antiga continua
        // a funcionar até definir a nova); num primeiro convite fica false.
        contaPortalRepository.save(conta);

        emailService.enviarConvitePortal(conta.getEmail(), studio.getNome(),
                baseUrl + "/portal-ativar?token=" + conta.getTokenAtivacao());

        Notification.show("Convite enviado para " + conta.getEmail())
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        atualizar();
    }

    private void atualizar() {
        Studio studio = TenantContext.getCurrentStudio();
        List<Aluno> alunos = studio != null ? alunoRepository.findAllByStudio(studio) : List.of();

        Map<String, List<Aluno>> porEmail = alunos.stream()
                .filter(a -> a.getEmail() != null && !a.getEmail().isBlank())
                .collect(Collectors.groupingBy(a -> a.getEmail().trim().toLowerCase(Locale.ROOT)));

        String f = filtro.getValue() == null ? "" : filtro.getValue().trim().toLowerCase(Locale.ROOT);

        List<Linha> linhas = porEmail.entrySet().stream()
                .filter(en -> f.isEmpty() || en.getKey().contains(f))
                .map(en -> {
                    String nomes = en.getValue().stream()
                            .map(Aluno::getNomeCompleto)
                            .filter(java.util.Objects::nonNull)
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .collect(Collectors.joining(", "));
                    Optional<ContaPortal> conta = contaPortalRepository.findByEmailIgnoreCase(en.getKey());
                    return new Linha(en.getKey(), nomes, conta.orElse(null));
                })
                .sorted(Comparator.comparing(Linha::email))
                .collect(Collectors.toList());

        grid.setItems(linhas);
    }

    private record Linha(String email, String nomes, ContaPortal conta) {
    }
}
