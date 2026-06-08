package pt.studioflow.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.User;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.MarcacaoSalaRepository;
import pt.studioflow.repository.TransferenciaRepository;
import pt.studioflow.repository.UserRepository;
import pt.studioflow.service.AuthService;
import pt.studioflow.service.EmailService;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Studio;
import org.springframework.boot.info.BuildProperties;

import java.util.Optional;

@Route("")
@PermitAll
public class MainLayout extends AppLayout {

        private final UserRepository userRepository;
        private final AuthService authService;
        private final AlunoRepository alunoRepository;
        private final MarcacaoSalaRepository marcacaoRepository;
        private final PasswordEncoder passwordEncoder;
        private final TransferenciaRepository transferenciaRepository;
        private final BuildProperties buildProperties;
        private final EmailService emailService;


        // Cores da Identidade Visual Renovada
        // Cores dinâmicas - definidas em runtime a partir do Studio
        private String getPrimaryColor() {
            Studio s = TenantContext.getCurrentStudio();
            return (s != null && s.getCorPrimaria() != null) ? s.getCorPrimaria() : "#4A90E2";
        }
        private String getSecondaryColor() {
            Studio s = TenantContext.getCurrentStudio();
            return (s != null && s.getCorSecundaria() != null) ? s.getCorSecundaria() : "#2D3436";
        }
        private static final String BG_GRADIENT = "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)";

        @Autowired
        public MainLayout(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        AlunoRepository alunoRepository, MarcacaoSalaRepository marcacaoRepository,
                        TransferenciaRepository transferenciaRepository, AuthService authService,
                        BuildProperties buildProperties, EmailService emailService) {
                this.userRepository = userRepository;
                this.authService = authService;
                authService.initTenantContext(); // Inicializa o TenantContext para esta sessão
                this.passwordEncoder = passwordEncoder;
                this.alunoRepository = alunoRepository;
                this.marcacaoRepository = marcacaoRepository;
                this.transferenciaRepository = transferenciaRepository;
                this.buildProperties = buildProperties;
                this.emailService = emailService;

                injectGlobalStyles();
                createHeader();
                createDrawer();

                handleAutoRedirection();
        }

        private void injectGlobalStyles() {
                String styles = "html { --lumo-primary-color: " + getPrimaryColor() + "; }" +
                                ".v-loading-indicator { background-color: rgba(255, 93, 19, 0.4); pointer-events: all; }"
                                +
                                ".v-loading-indicator::after { border-top-color: " + getPrimaryColor() + " !important; }" +
                                "vaadin-app-layout { background: " + BG_GRADIENT + " !important; }" +
                                "vaadin-tab[selected] { color: " + getPrimaryColor() + " !important; font-weight: 700; }" +
                                ".drawer-content { background: rgba(255, 255, 255, 0.8) !important; backdrop-filter: blur(10px); }"
                                +
                                ".nav-item:hover { background: rgba(255, 93, 19, 0.1) !important; transform: translateX(5px); transition: all 0.2s; }";

                UI.getCurrent().getElement().executeJs(
                                "const style = document.createElement('style');" +
                                                "style.textContent = $0;" +
                                                "document.head.appendChild(style);",
                                styles);
        }

        private void createHeader() {
                DrawerToggle toggle = new DrawerToggle();
                toggle.getStyle().set("color", getPrimaryColor());

                // Branding dinâmico - usa logo do estúdio atual ou logo CoreoFlow
                Studio currentStudio = TenantContext.getCurrentStudio();
                Authentication authForLogo = SecurityContextHolder.getContext().getAuthentication();
                boolean saForLogo = authForLogo != null && authForLogo.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));

                String logoSrc;
                String studioNome;
                if (saForLogo || currentStudio == null) {
                        logoSrc = "images/logo2-coreoflow.png";
                        studioNome = "CoreoFlow";
                } else if (currentStudio.getLogoPath() != null && !currentStudio.getLogoPath().isBlank()) {
                        logoSrc = normalizarLogoPath(currentStudio.getLogoPath());
                        studioNome = currentStudio.getNome();
                } else {
                        logoSrc = "images/logo2-coreoflow.png";
                        studioNome = currentStudio.getNome();
                }

                Image logo = new Image(logoSrc, studioNome);
                logo.setHeight("52px");
                logo.getStyle().set("cursor", "pointer");
                logo.addClickListener(e -> {
                        if (saForLogo) UI.getCurrent().navigate(SuperAdminDashboardView.class);
                        else UI.getCurrent().navigate(DashboardView.class);
                });

                HorizontalLayout leftLayout = new HorizontalLayout(toggle, logo);
                leftLayout.setAlignItems(Alignment.CENTER);
                leftLayout.setSpacing(true);

                // Menu de Utilizador Premium
                User currentUser = authService.getCurrentUser().orElse(null);
                String username = (currentUser != null && currentUser.getFirstName() != null && !currentUser.getFirstName().isBlank())
                        ? currentUser.getFirstName()
                        : (currentUser != null ? currentUser.getUsername() : "Utilizador");

                MenuBar userMenu = new MenuBar();
                userMenu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY);

                HorizontalLayout userBadge = new HorizontalLayout();
                userBadge.setAlignItems(Alignment.CENTER);
                userBadge.getStyle()
                                .set("background", "white")
                                .set("padding", "4px 12px")
                                .set("border-radius", "25px")
                                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)")
                                .set("border", "1px solid #eee");

                Icon userIcon = VaadinIcon.USER_CHECK.create();
                userIcon.setColor(getPrimaryColor());
                userIcon.setSize("18px");

                Span nameSpan = new Span(username);
                nameSpan.getStyle().set("font-weight", "600").set("color", getSecondaryColor());

                userBadge.add(userIcon, nameSpan, VaadinIcon.CHEVRON_DOWN_SMALL.create());

                MenuItem rootItem = userMenu.addItem(userBadge);
                SubMenu subMenu = rootItem.getSubMenu();

                User finalUser = currentUser;
                subMenu.addItem(criarItemDropdown(VaadinIcon.KEY, "Segurança"),
                                e -> abrirDialogAlterarPassword(finalUser));
                subMenu.addItem(criarItemDropdown(VaadinIcon.HEADSET, "Suporte remoto"),
                                e -> abrirDialogSuporte(finalUser));
                subMenu.add(new Div()); // Spacer
                subMenu.addItem(criarItemDropdown(VaadinIcon.POWER_OFF, "Terminar Sessão"), e -> performLogout());

                // Sininho de notificações (apenas para não-SUPERADMIN)
                Authentication authForBell = SecurityContextHolder.getContext().getAuthentication();
                boolean isSuperAdmin = authForBell != null && authForBell.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));

                HorizontalLayout rightLayout;
                if (!isSuperAdmin) {
                        Icon bellIcon = VaadinIcon.BELL.create();
                        bellIcon.setSize("22px");
                        bellIcon.setColor(getPrimaryColor());

                        Button btnNotificacoes = new Button(bellIcon);
                        btnNotificacoes.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
                        btnNotificacoes.getStyle()
                                        .set("border-radius", "50%")
                                        .set("width", "40px")
                                        .set("height", "40px")
                                        .set("min-width", "40px")
                                        .set("background", "white")
                                        .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)")
                                        .set("border", "1px solid #eee")
                                        .set("cursor", "pointer");
                        btnNotificacoes.getElement().setAttribute("title", "Notificações");
                        btnNotificacoes.addClickListener(e -> UI.getCurrent().navigate(NotificacoesView.class));
                        rightLayout = new HorizontalLayout(btnNotificacoes, userMenu);
                } else {
                        rightLayout = new HorizontalLayout(userMenu);
                }
                rightLayout.setAlignItems(Alignment.CENTER);
                rightLayout.setSpacing(true);

                HorizontalLayout headerContent = new HorizontalLayout(leftLayout, rightLayout);
                headerContent.setWidthFull();
                headerContent.setAlignItems(Alignment.CENTER);
                headerContent.setJustifyContentMode(
                                com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.BETWEEN);
                headerContent.getStyle()
                                .set("padding", "0 20px")
                                .set("background", "rgba(255, 255, 255, 0.9)")
                                .set("backdrop-filter", "blur(5px)")
                                .set("border-bottom", "1px solid #e0e0e0");

                addToNavbar(headerContent);
        }

        private void createDrawer() {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken)
                        return;

                boolean isSA = hasRole(auth, "ROLE_SUPERADMIN");
                boolean isAdmin = hasRole(auth, "ROLE_ADMIN");
                boolean isProf = hasRole(auth, "ROLE_PROF");
                boolean isDelegado = hasRole(auth, "ROLE_DELEG");
                boolean isAluno = hasRole(auth, "ROLE_ALUNO");

                // Layout principal do Drawer
                VerticalLayout drawerContent = new VerticalLayout();
                drawerContent.setPadding(false);
                drawerContent.setSpacing(false);
                drawerContent.setSizeFull();
                drawerContent.getStyle().set("background", "rgba(255, 255, 255, 0.9)");

                // O segredo está aqui: um Scroller para permitir ver todos os itens se a lista
                // for longa
                com.vaadin.flow.component.orderedlayout.Scroller scroller = new com.vaadin.flow.component.orderedlayout.Scroller();
                scroller.setSizeFull();
                scroller.setScrollDirection(com.vaadin.flow.component.orderedlayout.Scroller.ScrollDirection.VERTICAL);

                Tabs tabs = new Tabs();
                tabs.setOrientation(Tabs.Orientation.VERTICAL);
                tabs.setWidthFull();
                tabs.getStyle().set("box-shadow", "none");

                // --- CONTADORES ---
                pt.studioflow.model.Studio _studioMain = pt.studioflow.config.TenantContext.getCurrentStudio();
                String pendentesInsc = String.valueOf((_studioMain != null
                                ? alunoRepository.findAllByStudio(_studioMain)
                                : alunoRepository.findAll()).stream()
                                .filter(a -> a.getStatus() == Aluno.AlunoStatus.PENDENTE
                                                || a.getStatus() == Aluno.AlunoStatus.EXPERIMENTAL)
                                .count());

                String pendentesSala = String.valueOf((_studioMain != null
                                ? marcacaoRepository.findAllByStudio(_studioMain)
                                : marcacaoRepository.findAll()).stream()
                                .filter(m -> "PENDENTE".equalsIgnoreCase(m.getStatus()))
                                .count());

                // --- SUPERADMIN: menu exclusivo da plataforma ---
                if (isSA) {
                        tabs.add(criarHeaderMenu("Plataforma"));
                        tabs.add(criarTab("Dashboard", VaadinIcon.CHART_GRID, "#4A90E2", SuperAdminDashboardView.class, null));
                        tabs.add(criarTab("Saúde", VaadinIcon.HEALTH_CARD, "#E74C3C", PainelSaudeView.class, null));
                        tabs.add(criarTab("Estúdios", VaadinIcon.GLOBE, "#7B61FF", StudioAdminView.class, null));
                        tabs.add(criarTab("Utilizadores", VaadinIcon.SHIELD, "#27AE60", UserView.class, null));
                        tabs.add(criarHeaderMenu("Monetização"));
                        tabs.add(criarTab("Planos", VaadinIcon.PACKAGE, "#FF6F00", PlanosSubscricaoView.class, null));
                        tabs.add(criarTab("Billing", VaadinIcon.INVOICE, "#C62828", BillingView.class, null));
                        tabs.add(criarTab("Previsão", VaadinIcon.TRENDING_UP, "#2E7D32", PrevisaoReceitaView.class, null));
                }

                // Helper: verifica módulo do estúdio atual
                pt.studioflow.model.Studio studioParaModulos = pt.studioflow.config.TenantContext.getCurrentStudio();

                // --- 1. PRINCIPAL (apenas não-SA) ---
                if (!isAluno && !isDelegado && !isSA) {
                        tabs.add(criarTab("Dashboard", VaadinIcon.CHART_GRID, "#4A90E2", DashboardView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.MAPA_SALAS))
                                tabs.add(criarTab("Mapa de Salas", VaadinIcon.TABLE, "#7B61FF", SalaScheduleView.class,
                                        isAdmin ? pendentesSala : null));
                }

                if (!isSA && (isAdmin || isProf || isDelegado)) {
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.COMUNICACAO))
                                tabs.add(criarTab("Comunicação", VaadinIcon.CHAT, "#27AE60", TurmaComunicacaoView.class, null));
                }

                // --- 2. ADMINISTRAÇÃO ---
                if (isAdmin) {
                        tabs.add(criarHeaderMenu("Gestão Operacional"));
                        tabs.add(criarTab("Alunos", VaadinIcon.USERS, "#1ABC9C", AlunoView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.SOCIOS))
                                tabs.add(criarTab("Sócios", VaadinIcon.CREDIT_CARD, "#1ABC9C", SocioView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.LISTA_ESPERA))
                                tabs.add(criarTab("Lista de Espera", VaadinIcon.CLOCK, "#00ACC1", ListaEsperaView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.INSCRICOES))
                        tabs.add(criarTab("Validar Inscrições", VaadinIcon.USER_CHECK, getPrimaryColor(),
                                        ValidacaoInscricoesView.class, pendentesInsc));

                        // 1. Obter o utilizador logado (username)
                        String usernameLogado = com.vaadin.flow.server.VaadinServletRequest.getCurrent()
                                        .getHttpServletRequest().getUserPrincipal().getName();

                        // 2. Descobrir o email real associado na DB (tal como fizemos na View)
                        String emailUser = userRepository.findByUsername(usernameLogado)
                                        .map(pt.studioflow.model.User::getEmail)
                                        .orElse("");

                        // 3. Contar as transferências pendentes especificamente para quem está com a
                        // sessão iniciada
                        long pendentesTransf = 0;

                        Studio studioAtual = pt.studioflow.config.TenantContext.getCurrentStudio();
                        if (studioAtual != null && emailUser.equalsIgnoreCase(studioAtual.getEmailAssinante1())) { // Assinante 1
                                pendentesTransf = transferenciaRepository.findAll().stream()
                                                .filter(t -> t.getAssinadoPor1() == null &&
                                                                ("AGUARDA_ASSINATURAS".equals(t.getEstado())
                                                                                || "AGUARDA_UMA_ASSINATURA"
                                                                                                .equals(t.getEstado())))
                                                .count();
                        } else if (studioAtual != null && emailUser.equalsIgnoreCase(studioAtual.getEmailAssinante2())) { // Assinante 2
                                pendentesTransf = transferenciaRepository.findAll().stream()
                                                .filter(t -> t.getAssinadoPor2() == null &&
                                                                ("AGUARDA_ASSINATURAS".equals(t.getEstado())
                                                                                || "AGUARDA_UMA_ASSINATURA"
                                                                                                .equals(t.getEstado())))
                                                .count();
                        } else if (studioAtual == null || emailUser.equalsIgnoreCase(studioAtual.getEmailCriadorTransferencias()) || usernameLogado.contains("admin")) { // Admin
                                                                                                                   // /
                                                                                                                   // Admin
                                pendentesTransf = transferenciaRepository.findAll().stream()
                                                .filter(t -> "AGUARDA_PAGAMENTO".equals(t.getEstado()))
                                                .count();
                        }

                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.TRANSFERENCIAS))
                                tabs.add(criarTab("Transferências", VaadinIcon.INSTITUTION, "#E67E22", TransferenciasView.class,
                                        pendentesTransf > 0 ? String.valueOf(pendentesTransf) : null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.FINANCEIRO))
                                tabs.add(criarTab("Financeiro", VaadinIcon.EURO, "#E67E22", FinanceiroView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.ANIVERSARIOS))
                                tabs.add(criarTab("Aniversários", VaadinIcon.GIFT, "#E91E63", CRMView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.MENSALIDADES))
                                tabs.add(criarTab("Mensalidades", VaadinIcon.WALLET, "#E74C3C", MensalidadeView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.PRESENCAS))
                                tabs.add(criarTab("Presenças", VaadinIcon.TASKS, "#2980B9", PresencasView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.REGISTO_HORAS))
                                tabs.add(criarTab("Registo de Horas", VaadinIcon.CLOCK, "#3F51B5", RegistoHorasView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.EVENTOS))
                                tabs.add(criarTab("Eventos", VaadinIcon.STAR, "#F1C40F", ConvitesView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.NOTIFICACOES))
                                tabs.add(criarTab("Notificações", VaadinIcon.BELL, "#b71c1c", NotificacoesView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.RELATORIOS))
                                tabs.add(criarTab("Relatórios", VaadinIcon.CHART, "#607D8B", RelatoriosView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.AVALIACOES))
                                tabs.add(criarTab("Avaliações", VaadinIcon.STAR, "#F1C40F", AvaliacoesView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.CAMPANHAS))
                                tabs.add(criarTab("Campanhas", VaadinIcon.ENVELOPE, "#E91E63", CampanhasView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.CONTRATOS))
                                tabs.add(criarTab("Contratos", VaadinIcon.FILE_TEXT, "#607D8B", ContratosView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.PLANO_AULAS))
                                tabs.add(criarTab("Plano de Aulas", VaadinIcon.CALENDAR, "#00897B", PlanoAulasView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.LOJA))
                                tabs.add(criarTab("Loja", VaadinIcon.SHOP, "#FF6F00", LojaView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.SUBSIDIOS))
                                tabs.add(criarTab("Subsídios", VaadinIcon.PIGGY_BANK, "#5E35B1", SubsidiosView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.REFERENCIAS))
                                tabs.add(criarTab("Referências", VaadinIcon.HANDSHAKE, "#00838F", ReferenciasView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.INQUERITOS))
                                tabs.add(criarTab("Inquéritos", VaadinIcon.CLIPBOARD_TEXT, "#F4511E", InqueritosView.class, null));
                        tabs.add(criarTab("Previsão de Receita", VaadinIcon.TRENDING_UP, "#2E7D32", PrevisaoReceitaView.class, null));

                        tabs.add(criarHeaderMenu("Configurações"));
                        tabs.add(criarSubTab("Utilizadores", VaadinIcon.SHIELD, UserView.class));
                        tabs.add(criarSubTab("Professores", VaadinIcon.ACADEMY_CAP, ProfessorView.class));
                        tabs.add(criarSubTab("Salas", VaadinIcon.HOME, SalaView.class));
                        tabs.add(criarSubTab("Modalidades", VaadinIcon.BOOK, ModalidadeView.class));
                        tabs.add(criarSubTab("Turmas", VaadinIcon.GROUP, TurmaView.class));
                        tabs.add(criarSubTab("Alunos-Turmas", VaadinIcon.CLIPBOARD_TEXT, TurmaAlunosView.class));

                }

                // --- 3. ÁREA DOCENTE ---
                else if (isProf || isDelegado) {
                        tabs.add(criarHeaderMenu("Painel do Professor"));
                        if (isProf && !isDelegado) {
                                if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.PRESENCAS))
                                        tabs.add(criarTab("Presenças", VaadinIcon.CHECK_SQUARE, "#27AE60", PresencasView.class, null));
                                if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.REGISTO_HORAS))
                                        tabs.add(criarTab("Registo de Horas", VaadinIcon.CLOCK, "#3F51B5", RegistoHorasView.class, null));
                        }
                        tabs.add(criarTab("Meus Alunos", VaadinIcon.USERS, "#1ABC9C", AlunosProfessorView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.EVENTOS))
                                tabs.add(criarTab("Eventos", VaadinIcon.STAR, "#F1C40F", ConvitesProfessorView.class, null));
                }

                // --- 4. ALUNO ---
                if (isAluno) {
                        tabs.add(criarHeaderMenu("Área do Aluno"));
                        tabs.add(criarTab("Vídeos das Aulas", VaadinIcon.PLAY_CIRCLE, "#D32F2F", AlunoVideosView.class,
                                        null));
                }

                scroller.setContent(tabs);

                // Footer de branding — fixo no fundo do drawer
                Span brandLine1 = new Span("CoreoFlow");
                brandLine1.getStyle()
                        .set("font-weight", "700")
                        .set("font-size", "12px")
                        .set("color", "#2B3A6B")
                        .set("letter-spacing", "0.5px");

                String appVersion = buildProperties.getVersion().replace("-SNAPSHOT", "");
                Span brandLine2 = new Span("v" + appVersion + " · by Daniel Silva");
                brandLine2.getStyle()
                        .set("font-size", "10px")
                        .set("color", "#95a5a6")
                        .set("letter-spacing", "0.3px");

                com.vaadin.flow.component.html.Div brandingFooter = new com.vaadin.flow.component.html.Div(brandLine1, brandLine2);
                brandingFooter.getStyle()
                        .set("display", "flex")
                        .set("flex-direction", "column")
                        .set("align-items", "center")
                        .set("padding", "12px 0 14px 0")
                        .set("border-top", "1px solid #e0e0e0")
                        .set("margin-top", "auto")
                        .set("width", "100%")
                        .set("background", "rgba(255,255,255,0.95)");

                drawerContent.add(scroller, brandingFooter);
                addToDrawer(drawerContent);
        }

        private Tab criarTab(String texto, VaadinIcon icone, String cor,
                        Class<? extends com.vaadin.flow.component.Component> view, String badge) {
                Icon icon = icone.create();
                icon.setSize("20px");
                icon.setColor(cor);

                Span label = new Span(texto);
                label.getStyle().set("font-weight", "500").set("margin-left", "12px");

                HorizontalLayout layout = new HorizontalLayout(icon, label);
                layout.setAlignItems(Alignment.CENTER);
                layout.addClassName("nav-item");
                layout.getStyle().set("padding", "10px 15px").set("border-radius", "12px");

                if (badge != null && !badge.equals("0")) {
                        Span b = new Span(badge);
                        b.getStyle()
                                        .set("background", getPrimaryColor())
                                        .set("color", "white")
                                        .set("font-size", "10px")
                                        .set("padding", "2px 8px")
                                        .set("border-radius", "10px")
                                        .set("margin-left", "auto");
                        layout.add(b);
                }

                RouterLink link = new RouterLink();
                link.setRoute(view);
                link.add(layout);
                link.getStyle().set("text-decoration", "none").set("color", "inherit").set("width", "100%");

                return new Tab(link);
        }

        private Tab criarSubTab(String texto, VaadinIcon icone,
                        Class<? extends com.vaadin.flow.component.Component> view) {
                Tab tab = criarTab(texto, icone, "#7f8c8d", view, null);
                tab.getStyle().set("padding-left", "30px").set("font-size", "0.9em");
                return tab;
        }

        private HorizontalLayout criarItemDropdown(VaadinIcon vaadinIcon, String label) {
                Icon icon = vaadinIcon.create();
                icon.setSize("16px");
                icon.setColor(getSecondaryColor());
                Span text = new Span(label);
                text.getStyle().set("font-size", "14px").set("margin-left", "10px");
                return new HorizontalLayout(icon, text);
        }

        private void performLogout() {
                UI.getCurrent().getElement().executeJs(
                                "const form = document.createElement('form'); form.method = 'post'; form.action = '/logout';"
                                                +
                                                "document.body.appendChild(form); form.submit();");
        }

        private boolean hasRole(Authentication auth, String role) {
                return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
        }

        private void handleAutoRedirection() {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                        // Apenas redireciona se estiver na raiz
                        UI.getCurrent().getPage().fetchCurrentURL(url -> {
                                if (url.getPath().equals("/")) {
                                        if (hasRole(auth, "ROLE_SUPERADMIN"))
                                                UI.getCurrent().navigate(SuperAdminDashboardView.class);
                                        else if (hasRole(auth, "ROLE_ADMIN"))
                                                UI.getCurrent().navigate(DashboardView.class);
                                        else if (hasRole(auth, "ROLE_PROF"))
                                                UI.getCurrent().navigate(PresencasView.class);
                                        else if (hasRole(auth, "ROLE_ALUNO"))
                                                UI.getCurrent().navigate(PortalAlunoView.class);
                                }
                        });
                }
        }

        private void abrirDialogSuporte(User user) {
                Dialog dialog = new Dialog();
                dialog.setHeaderTitle("Suporte remoto");
                dialog.setWidth("480px");

                com.vaadin.flow.component.textfield.TextField assunto =
                        new com.vaadin.flow.component.textfield.TextField("Assunto");
                assunto.setWidthFull();
                assunto.setPlaceholder("Descreve brevemente o problema...");

                com.vaadin.flow.component.combobox.ComboBox<String> tipo =
                        new com.vaadin.flow.component.combobox.ComboBox<>("Tipo de problema");
                tipo.setItems("Bug / Erro", "Dúvida de utilização", "Pedido de melhoria", "Problema de acesso", "Outro");
                tipo.setWidthFull();

                com.vaadin.flow.component.textfield.TextArea descricao =
                        new com.vaadin.flow.component.textfield.TextArea("Descrição");
                descricao.setWidthFull();
                descricao.setMinHeight("120px");
                descricao.setPlaceholder("Descreve o problema com o máximo de detalhe possível...");

                com.vaadin.flow.component.formlayout.FormLayout form =
                        new com.vaadin.flow.component.formlayout.FormLayout(tipo, assunto, descricao);
                form.setResponsiveSteps(new com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep("0", 1));
                form.setColspan(descricao, 1);

                Button enviar = new Button("Enviar", e -> {
                        if (tipo.isEmpty() || assunto.getValue().isBlank() || descricao.getValue().isBlank()) {
                                Notification.show("Preenche todos os campos.", 3000, Notification.Position.MIDDLE)
                                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                                return;
                        }
                        try {
                                String studioNome = TenantContext.getCurrentStudio() != null
                                        ? TenantContext.getCurrentStudio().getNome() : "—";
                                String nomeUtilizador = user != null ? user.getFirstName() + " (" + user.getUsername() + ")" : "—";
                                emailService.enviarEmailSuporte(studioNome, nomeUtilizador,
                                        tipo.getValue(), assunto.getValue(), descricao.getValue());
                                Notification.show("Pedido de suporte enviado com sucesso!", 3000, Notification.Position.BOTTOM_CENTER)
                                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                                dialog.close();
                        } catch (Exception ex) {
                                Notification.show("Erro ao enviar o email. Tenta novamente.", 4000, Notification.Position.MIDDLE)
                                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                        }
                });
                enviar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

                Button cancelar = new Button("Cancelar", e -> dialog.close());
                dialog.add(new VerticalLayout(form));
                dialog.getFooter().add(cancelar, enviar);
                dialog.open();
        }

        private void abrirDialogAlterarPassword(User user) {
                if (user == null)
                        return;
                Dialog dialog = new Dialog();
                dialog.setHeaderTitle("Segurança da Conta");

                PasswordField pw = new PasswordField("Nova Password");
                pw.setWidthFull();

                Button save = new Button("Atualizar", e -> {
                        user.setPassword(passwordEncoder.encode(pw.getValue()));
                        userRepository.save(user);
                        Notification.show("Password alterada!").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        dialog.close();
                });
                save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

                VerticalLayout content = new VerticalLayout(
                                new Span("Escolha uma senha forte para proteger os seus dados."), pw);
                dialog.add(content);
                dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), save);
                dialog.open();
        }

        /**
         * Normaliza o caminho do logo para URL relativa válida.
         * Corrige caminhos guardados erroneamente como "src/main/resources/static/..."
         */
        private static String normalizarLogoPath(String path) {
            if (path == null || path.isBlank()) return "images/logo-coreoflow.png";
            // Remover prefixos de classpath que não são URLs válidas
            String p = path
                .replaceFirst("^src/main/resources/static/", "")
                .replaceFirst("^static/", "")
                .replaceFirst("^/", "");
            return p;
        }

        private Tab criarHeaderMenu(String titulo) {
                Span span = new Span(titulo);
                // Estilo para titulos de seccao
                span.getStyle()
                                .set("color", "#95a5a6")
                                .set("font-size", "11px")
                                .set("font-weight", "800")
                                .set("margin-top", "20px")
                                .set("letter-spacing", "0.05em")
                                .set("text-transform", "uppercase")
                                .set("pointer-events", "none");

                Tab tab = new Tab(span);
                tab.setEnabled(false);
                return tab;
        }
}
