package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Studio;
import pt.studioflow.model.User;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.UserRepository;

import java.util.List;

@Route(value = "utilizadores", layout = MainLayout.class)
@PageTitle("Utilizadores | CoreoFlow")
@RolesAllowed({"ADMIN", "SUPERADMIN"})
public class UserView extends VerticalLayout {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StudioRepository studioRepository;

    private Grid<User> grid;

    public UserView(UserRepository userRepository, PasswordEncoder passwordEncoder,
                    StudioRepository studioRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.studioRepository = studioRepository;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Gestão de Utilizadores");
        titulo.getStyle().set("margin-top", "0");
        add(titulo, botaoAdicionar(), criarGrid());

        atualizarGrid();
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
    }

    // ---------------- BOTÃO ADICIONAR ----------------

    private HorizontalLayout botaoAdicionar() {
        return ViewUtils.toolbar(ViewUtils.botaoNovo("Novo Utilizador", e -> abrirDialog(null)));
    }

    // ---------------- GRID ----------------

    private Grid<User> criarGrid() {
        grid = new Grid<>(User.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addColumn(User::getUsername)
                .setHeader("Username").setAutoWidth(true);

        grid.addColumn(User::getFirstName)
                .setHeader("Primeiro Nome").setAutoWidth(true);

        grid.addColumn(User::getEmail)
                .setHeader("E-mail").setAutoWidth(true);

        grid.addColumn(User::getRole)
                .setHeader("Perfil").setAutoWidth(true);

        grid.addColumn(u -> u.getStudio() != null ? u.getStudio().getNome() : "—")
                .setHeader("Estúdio").setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(user -> {
            Button editar = new Button(new Icon(VaadinIcon.EDIT));
            editar.getElement().setProperty("title", "Editar");
            editar.addClickListener(e -> abrirDialog(user));

            Button remover = new Button(new Icon(VaadinIcon.TRASH));
            remover.getElement().setProperty("title", "Remover");
            remover.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            remover.addClickListener(e -> {
                ConfirmDialog confirmDialog = new ConfirmDialog();
                confirmDialog.setHeader("Remover utilizador");
                confirmDialog.setText("Tem a certeza que quer remover \"" + user.getUsername() + "\"? Esta ação não pode ser desfeita.");
                confirmDialog.setConfirmText("Remover");
                confirmDialog.setConfirmButtonTheme("error primary");
                confirmDialog.setCancelable(true);
                confirmDialog.setCancelText("Cancelar");
                confirmDialog.addConfirmListener(ev -> {
                    userRepository.delete(user);
                    atualizarGrid();
                    Notification.show("Utilizador removido").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
                confirmDialog.open();
            });

            HorizontalLayout actions = new HorizontalLayout(editar, remover);
            actions.setSpacing(true);
            return actions;
        }).setHeader("Ações").setAutoWidth(true).setFlexGrow(0);

        return grid;
    }

    // ---------------- DATA ----------------

    private void atualizarGrid() {
        if (isSuperAdmin()) {
            grid.setItems(userRepository.findAll().stream()
                    .filter(u -> !"SUPERADMIN".equals(u.getRole()))
                    .toList());
        } else {
            Studio studio = TenantContext.getCurrentStudio();
            grid.setItems(studio != null ? userRepository.findAllByStudio(studio) : List.of());
        }
    }

    // ---------------- DIALOG ----------------

    private void abrirDialog(User user) {
        boolean novo = (user == null);
        User utilizador = novo ? new User() : user;
        boolean sa = isSuperAdmin();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(novo ? "Novo Utilizador" : "Editar Utilizador");
        dialog.setWidth("440px");
        dialog.setMaxWidth("100%");

        TextField username = new TextField("Username");
        username.setRequired(true);
        username.setWidthFull();
        username.setValue(utilizador.getUsername() != null ? utilizador.getUsername() : "");
        if (!novo) username.setReadOnly(true);

        TextField firstName = new TextField("Primeiro Nome");
        firstName.setWidthFull();
        firstName.setValue(utilizador.getFirstName() != null ? utilizador.getFirstName() : "");

        TextField email = new TextField("E-mail");
        email.setRequired(true);
        email.setPlaceholder("exemplo@estudio.pt");
        email.setWidthFull();
        email.setValue(utilizador.getEmail() != null ? utilizador.getEmail() : "");

        PasswordField password = new PasswordField("Password");
        password.setRequired(novo);
        password.setWidthFull();
        if (!novo) password.setPlaceholder("(Deixar em branco para manter)");

        ComboBox<String> role = new ComboBox<>("Perfil");
        role.setItems("ADMIN", "PROF", "ALUNO", "DELEG");
        role.setRequired(true);
        role.setWidthFull();
        role.setValue(utilizador.getRole());

        ComboBox<Studio> studioCombo = new ComboBox<>("Estúdio");
        studioCombo.setWidthFull();
        studioCombo.setRequired(true);
        studioCombo.setItemLabelGenerator(Studio::getNome);
        studioCombo.setPlaceholder("Selecionar estúdio...");

        FormLayout form;
        if (sa) {
            List<Studio> studios = studioRepository.findAll().stream()
                    .filter(Studio::isAtivo).toList();
            studioCombo.setItems(studios);
            if (utilizador.getStudio() != null) studioCombo.setValue(utilizador.getStudio());
            form = new FormLayout(username, firstName, email, password, role, studioCombo);
        } else {
            form = new FormLayout(username, firstName, email, password, role);
        }
        form.setWidthFull();

        Button guardar = new Button("Guardar", e -> {
            if (username.isEmpty() || email.isEmpty() || role.isEmpty() || (novo && password.isEmpty())) {
                Notification.show("Preenche todos os campos obrigatórios");
                return;
            }
            if (sa && studioCombo.getValue() == null) {
                Notification.show("Seleciona um estúdio");
                return;
            }

            utilizador.setUsername(username.getValue().trim());
            utilizador.setFirstName(firstName.getValue().trim());
            utilizador.setEmail(email.getValue().trim().toLowerCase());
            utilizador.setRole(role.getValue());
            utilizador.setStudio(sa ? studioCombo.getValue() : TenantContext.getCurrentStudio());

            if (novo || !password.isEmpty()) {
                utilizador.setPassword(passwordEncoder.encode(password.getValue()));
            }

            userRepository.save(utilizador);
            atualizarGrid();
            dialog.close();
            Notification.show("Utilizador guardado com sucesso").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelar = new Button("Cancelar", e -> dialog.close());
        cancelar.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout footerActions = new HorizontalLayout(cancelar, guardar);
        footerActions.getStyle().set("margin-left", "auto");

        dialog.add(form);
        dialog.getFooter().add(footerActions);
        dialog.open();
    }
}
