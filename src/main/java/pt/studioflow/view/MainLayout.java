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
import pt.studioflow.model.Lead;
import pt.studioflow.model.User;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.LeadRepository;
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
        private final pt.studioflow.service.SuporteService suporteService;
        private final LeadRepository leadRepository;
        private final pt.studioflow.repository.ContaPortalRepository contaPortalRepository;


        // Cores da Identidade Visual Renovada
        // Cores dinâmicas - definidas em runtime a partir do Studio
        private String getPrimaryColor() {
            Studio s = TenantContext.getCurrentStudio();
            return (s != null && s.getCorPrimaria() != null) ? s.getCorPrimaria() : "#2F6FD6";
        }
        private String getSecondaryColor() {
            Studio s = TenantContext.getCurrentStudio();
            return (s != null && s.getCorSecundaria() != null) ? s.getCorSecundaria() : "#2D3436";
        }

        @Autowired
        public MainLayout(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        AlunoRepository alunoRepository, MarcacaoSalaRepository marcacaoRepository,
                        TransferenciaRepository transferenciaRepository, AuthService authService,
                        BuildProperties buildProperties, EmailService emailService,
                        LeadRepository leadRepository,
                        pt.studioflow.repository.ContaPortalRepository contaPortalRepository,
                        pt.studioflow.service.SuporteService suporteService) {
                this.userRepository = userRepository;
                this.authService = authService;
                authService.initTenantContext(); // Inicializa o TenantContext para esta sessão
                this.passwordEncoder = passwordEncoder;
                this.alunoRepository = alunoRepository;
                this.marcacaoRepository = marcacaoRepository;
                this.transferenciaRepository = transferenciaRepository;
                this.buildProperties = buildProperties;
                this.emailService = emailService;
                this.leadRepository = leadRepository;
                this.contaPortalRepository = contaPortalRepository;
                this.suporteService = suporteService;

                injectGlobalStyles();
                createHeader();
                createDrawer();

                handleAutoRedirection();
        }

        private void injectGlobalStyles() {
                // O visual (loader, shell, navegação, modo escuro) vive no tema
                // src/main/frontend/themes/coreoflow/styles.css. Aqui só se passa a cor
                // do estúdio (--cf-brand), da qual o tema deriva a cor primária.
                String styles = "html { --cf-brand: " + getPrimaryColor() + "; }";

                UI.getCurrent().getElement().executeJs(
                                "const style = document.createElement('style');" +
                                                "style.textContent = $0;" +
                                                "document.head.appendChild(style);" +
                                                "if (localStorage.getItem('coreoflow-dark-mode') === 'true') {" +
                                                "  document.documentElement.setAttribute('theme', 'dark');" +
                                                "}",
                                styles);
        }

        private Button createDarkModeToggle() {
                Icon icon = VaadinIcon.MOON.create();

                Button toggle = new Button(icon);
                toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
                toggle.addClassNames("dark-mode-toggle", "cf-icon-btn");
                toggle.getElement().setAttribute("title", "Alternar modo escuro");
                toggle.getElement().setAttribute("aria-label", "Alternar modo escuro");
                toggle.addClickListener(e -> UI.getCurrent().getElement().executeJs(
                                "const html = document.documentElement;" +
                                                "if (html.getAttribute('theme') === 'dark') {" +
                                                "  html.removeAttribute('theme');" +
                                                "  localStorage.setItem('coreoflow-dark-mode', 'false');" +
                                                "} else {" +
                                                "  html.setAttribute('theme', 'dark');" +
                                                "  localStorage.setItem('coreoflow-dark-mode', 'true');" +
                                                "}"));
                return toggle;
        }

        private void createHeader() {
                DrawerToggle toggle = new DrawerToggle();

                // Branding: canto superior esquerdo mostra SEMPRE o logo CoreoFlow (como o
                // superadmin) - o logo do estúdio vai para a direita, junto ao menu de utilizador.
                Studio currentStudio = TenantContext.getCurrentStudio();
                Authentication authForLogo = SecurityContextHolder.getContext().getAuthentication();
                boolean saForLogo = authForLogo != null && authForLogo.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));

                Image logo = new Image("images/logo2-coreoflow.png", "CoreoFlow");
                logo.addClassName("cf-header-logo");
                logo.addClickListener(e -> {
                        if (saForLogo) UI.getCurrent().navigate(SuperAdminDashboardView.class);
                        else UI.getCurrent().navigate(DashboardView.class);
                });

                HorizontalLayout leftLayout = new HorizontalLayout(toggle, logo);
                leftLayout.setAlignItems(Alignment.CENTER);
                leftLayout.setSpacing(true);

                // Menu de Utilizador Premium
                User currentUser = authService.getCurrentUser().orElse(null);
                pt.studioflow.model.ContaPortal contaPortal = currentUser == null
                        ? authService.getContaPortalLogado().orElse(null) : null;
                String username;
                if (currentUser != null) {
                        username = currentUser.getFirstName() != null && !currentUser.getFirstName().isBlank()
                                ? currentUser.getFirstName() : currentUser.getUsername();
                } else if (contaPortal != null) {
                        username = contaPortal.getNome() != null && !contaPortal.getNome().isBlank()
                                ? contaPortal.getNome() : contaPortal.getEmail();
                } else {
                        username = "Utilizador";
                }

                MenuBar userMenu = new MenuBar();
                userMenu.addThemeVariants(MenuBarVariant.LUMO_TERTIARY);

                HorizontalLayout userBadge = new HorizontalLayout();
                userBadge.addClassName("user-badge");
                userBadge.setAlignItems(Alignment.CENTER);
                userBadge.setSpacing(false);

                Span userAvatar = new Span(VaadinIcon.USER.create());
                userAvatar.addClassName("cf-user-avatar");

                Span nameSpan = new Span(username);
                nameSpan.addClassName("user-name");

                userBadge.add(userAvatar, nameSpan, VaadinIcon.CHEVRON_DOWN_SMALL.create());

                MenuItem rootItem = userMenu.addItem(userBadge);
                SubMenu subMenu = rootItem.getSubMenu();

                User finalUser = currentUser;
                pt.studioflow.model.ContaPortal finalConta = contaPortal;
                subMenu.addItem(criarItemDropdown(VaadinIcon.KEY, "Segurança"), e -> {
                                if (finalUser != null) abrirDialogAlterarPassword(finalUser);
                                else if (finalConta != null) abrirDialogAlterarPasswordPortal(finalConta);
                });
                subMenu.addItem(criarItemDropdown(VaadinIcon.HEADSET, "Suporte remoto"),
                                e -> abrirDialogSuporte(finalUser));
                subMenu.add(new Div()); // Spacer
                subMenu.addItem(criarItemDropdown(VaadinIcon.POWER_OFF, "Terminar Sessão"), e -> performLogout());

                // Sininho de notificações (apenas para não-SUPERADMIN e não-ALUNO —
                // NotificacoesView é @RolesAllowed ADMIN/PROF)
                Authentication authForBell = SecurityContextHolder.getContext().getAuthentication();
                boolean isSuperAdmin = authForBell != null && authForBell.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN"));
                boolean isAlunoHeader = authForBell != null && authForBell.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ALUNO"));

                Button darkModeToggle = createDarkModeToggle();

                // Logo do estúdio no canto superior direito, ao lado da seta do menu de utilizador.
                Image studioLogo = null;
                if (currentStudio != null && currentStudio.getLogoPath() != null
                                && !currentStudio.getLogoPath().isBlank()) {
                        studioLogo = new Image(
                                        pt.studioflow.util.LogoUrl.comVersao(currentStudio.getLogoPath()),
                                        currentStudio.getNome() != null ? currentStudio.getNome() : "Estúdio");
                        studioLogo.addClassName("cf-studio-logo");
                        studioLogo.getElement().setAttribute("title", currentStudio.getNome());
                        studioLogo.addClickListener(e -> UI.getCurrent().navigate(DashboardView.class));
                }

                HorizontalLayout rightLayout;
                if (!isSuperAdmin && !isAlunoHeader) {
                        Icon bellIcon = VaadinIcon.BELL.create();

                        Button btnNotificacoes = new Button(bellIcon);
                        btnNotificacoes.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
                        btnNotificacoes.addClassNames("notif-btn", "cf-icon-btn");
                        btnNotificacoes.getElement().setAttribute("title", "Notificações");
                        btnNotificacoes.getElement().setAttribute("aria-label", "Notificações");
                        btnNotificacoes.addClickListener(e -> UI.getCurrent().navigate(NotificacoesView.class));
                        rightLayout = studioLogo != null
                                        ? new HorizontalLayout(darkModeToggle, btnNotificacoes, studioLogo, userMenu)
                                        : new HorizontalLayout(darkModeToggle, btnNotificacoes, userMenu);
                } else {
                        rightLayout = studioLogo != null
                                        ? new HorizontalLayout(darkModeToggle, studioLogo, userMenu)
                                        : new HorizontalLayout(darkModeToggle, userMenu);
                }
                rightLayout.setAlignItems(Alignment.CENTER);
                rightLayout.setSpacing(false);
                rightLayout.addClassName("cf-header-actions");

                HorizontalLayout headerContent = new HorizontalLayout(leftLayout, rightLayout);
                headerContent.addClassName("app-header");
                headerContent.setWidthFull();
                headerContent.setAlignItems(Alignment.CENTER);
                headerContent.setJustifyContentMode(
                                com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.BETWEEN);

                addToNavbar(headerContent);

                // Aviso global configurável pelo superadmin: faixa por baixo do menu
                // (showRouterLayoutContent) e/ou popup uma vez por sessão.
                try {
                        pt.studioflow.model.ConfiguracaoPlataforma cfg = suporteService.getConfig();
                        String texto = cfg.getAvisoGlobal() != null ? cfg.getAvisoGlobal().trim() : "";
                        if (!texto.isBlank()) {
                                if (cfg.mostrarAvisoBanner()) {
                                        this.avisoGlobalTexto = texto;
                                }
                                if (cfg.mostrarAvisoPopup()) {
                                        mostrarPopupAvisoUmaVez(texto);
                                }
                        }
                } catch (Exception ignore) {
                        // não bloquear o arranque da UI por causa do aviso
                }
        }

        private void mostrarPopupAvisoUmaVez(String texto) {
                com.vaadin.flow.server.VaadinSession sessao = com.vaadin.flow.server.VaadinSession.getCurrent();
                if (sessao == null) {
                        return;
                }
                Object jaVisto = sessao.getAttribute("avisoGlobalVisto");
                if (Boolean.TRUE.equals(jaVisto)) {
                        return;
                }
                sessao.setAttribute("avisoGlobalVisto", Boolean.TRUE);

                Dialog d = new Dialog();
                d.setHeaderTitle("📢  Aviso");
                d.setWidth("440px");
                com.vaadin.flow.component.html.Span msg = new com.vaadin.flow.component.html.Span(texto);
                msg.addClassName("cf-pre-wrap");
                d.add(msg);
                Button ok = new Button("Entendido", e -> d.close());
                ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                d.getFooter().add(ok);
                d.open();
        }

        private String avisoGlobalTexto;

        @Override
        public void showRouterLayoutContent(com.vaadin.flow.component.HasElement content) {
                if (avisoGlobalTexto == null || avisoGlobalTexto.isBlank() || content == null) {
                        super.showRouterLayoutContent(content);
                        return;
                }
                Span aviso = new Span("📢  " + avisoGlobalTexto);
                aviso.addClassName("cf-aviso-global");

                com.vaadin.flow.component.html.Div wrap = new com.vaadin.flow.component.html.Div();
                wrap.setSizeFull();
                wrap.addClassName("cf-aviso-wrap");
                wrap.add(aviso);
                content.getElement().getStyle().set("flex", "1 1 auto").set("min-height", "0")
                                .set("height", "auto");
                wrap.getElement().appendChild(content.getElement());
                super.showRouterLayoutContent(wrap);
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
                drawerContent.addClassName("drawer-panel");
                drawerContent.setPadding(false);
                drawerContent.setSpacing(false);
                drawerContent.setSizeFull();

                // O segredo está aqui: um Scroller para permitir ver todos os itens se a lista
                // for longa
                com.vaadin.flow.component.orderedlayout.Scroller scroller = new com.vaadin.flow.component.orderedlayout.Scroller();
                scroller.setSizeFull();
                scroller.setScrollDirection(com.vaadin.flow.component.orderedlayout.Scroller.ScrollDirection.VERTICAL);

                Tabs tabs = new Tabs();
                tabs.setOrientation(Tabs.Orientation.VERTICAL);
                tabs.setWidthFull();
                tabs.addClassName("cf-nav");

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
                        long pedidosPorResolver = suporteService.porResolver();
                        tabs.add(criarTab("Suporte", VaadinIcon.HEADSET, "#16A085", SuporteView.class,
                                        pedidosPorResolver > 0 ? String.valueOf(pedidosPorResolver) : null));
                        tabs.add(criarTab("Acessos", VaadinIcon.EYE, "#8E44AD", AcessosView.class, null));
                        tabs.add(criarTab("Logs & Erros", VaadinIcon.FILE_TEXT_O, "#E74C3C", LogsView.class, null));
                        tabs.add(criarTab("Configurações", VaadinIcon.COG, "#607D8B", ConfiguracoesPlataformaView.class, null));
                        tabs.add(criarHeaderMenu("Comercial"));
                        long leadsAtivos = leadRepository.findAll().stream()
                                        .filter(l -> l.getEstado() != Lead.EstadoLead.GANHO && l.getEstado() != Lead.EstadoLead.PERDIDO)
                                        .count();
                        tabs.add(criarTab("Leads / Prospects", VaadinIcon.PHONE, "#009688", LeadsView.class,
                                        leadsAtivos > 0 ? String.valueOf(leadsAtivos) : null));
                        tabs.add(criarHeaderMenu("Monetização"));
                        tabs.add(criarTab("Planos", VaadinIcon.PACKAGE, "#FF6F00", PlanosSubscricaoView.class, null));
                        tabs.add(criarTab("Billing", VaadinIcon.INVOICE, "#C62828", BillingView.class, null));
                        tabs.add(criarTab("Previsão", VaadinIcon.TRENDING_UP, "#2E7D32", PrevisaoReceitaView.class, null));
                }

                // Helper: verifica módulo do estúdio atual
                pt.studioflow.model.Studio studioParaModulos = pt.studioflow.config.TenantContext.getCurrentStudio();

                boolean iaAtiva;
                try {
                        iaAtiva = suporteService.getConfig().isTreinadorIaAtivo();
                } catch (Exception e) {
                        iaAtiva = false;
                }

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
                        String emailUser = userRepository.findByPrincipalName(usernameLogado)
                                        .map(pt.studioflow.model.User::getEmail)
                                        .orElse("");

                        // 3. Contar as transferências pendentes especificamente para quem está com a
                        // sessão iniciada
                        long pendentesTransf = 0;

                        Studio studioAtual = pt.studioflow.config.TenantContext.getCurrentStudio();
                        // Multi-tenant: só as transferências do estúdio atual (findAll só p/ superadmin sem estúdio).
                        java.util.List<pt.studioflow.model.Transferencia> transfsStudio = studioAtual != null
                                        ? transferenciaRepository.findAllByStudio(studioAtual)
                                        : transferenciaRepository.findAll();
                        if (studioAtual != null && emailUser.equalsIgnoreCase(studioAtual.getEmailAssinante1())) { // Assinante 1
                                pendentesTransf = transfsStudio.stream()
                                                .filter(t -> t.getAssinadoPor1() == null &&
                                                                ("AGUARDA_ASSINATURAS".equals(t.getEstado())
                                                                                || "AGUARDA_UMA_ASSINATURA"
                                                                                                .equals(t.getEstado())))
                                                .count();
                        } else if (studioAtual != null && emailUser.equalsIgnoreCase(studioAtual.getEmailAssinante2())) { // Assinante 2
                                pendentesTransf = transfsStudio.stream()
                                                .filter(t -> t.getAssinadoPor2() == null &&
                                                                ("AGUARDA_ASSINATURAS".equals(t.getEstado())
                                                                                || "AGUARDA_UMA_ASSINATURA"
                                                                                                .equals(t.getEstado())))
                                                .count();
                        } else if (studioAtual == null || emailUser.equalsIgnoreCase(studioAtual.getEmailCriadorTransferencias()) || usernameLogado.contains("admin")) { // Admin
                                                                                                                   // /
                                                                                                                   // Admin
                                pendentesTransf = transfsStudio.stream()
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
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.EVENTOS))
                                tabs.add(criarTab("Convocar Alunos", VaadinIcon.PAPERPLANE, "#F1C40F", ConvitesProfessorView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.NOTIFICACOES))
                                tabs.add(criarTab("Notificações", VaadinIcon.BELL, "#b71c1c", NotificacoesView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.RELATORIOS))
                                tabs.add(criarTab("Relatórios", VaadinIcon.CHART, "#607D8B", RelatoriosView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.AVALIACOES))
                                tabs.add(criarTab("Avaliações", VaadinIcon.MEDAL, "#8E44AD", AvaliacoesView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.CAMPANHAS))
                                tabs.add(criarTab("Campanhas", VaadinIcon.ENVELOPE, "#E91E63", CampanhasView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.CONTRATOS))
                                tabs.add(criarTab("Contratos", VaadinIcon.FILE_TEXT, "#607D8B", ContratosView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.PLANO_AULAS))
                                tabs.add(criarTab("Plano de Aulas", VaadinIcon.CALENDAR, "#00897B", PlanoAulasView.class, null));
                        tabs.add(criarTab("Horário", VaadinIcon.CALENDAR_CLOCK, "#0e7490", HorarioView.class, null));
                        tabs.add(criarTab("Aulas Planeadas", VaadinIcon.CLIPBOARD_CHECK, "#1d4ed8", AulasPlaneadasView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.VIDEOS_AULA))
                                tabs.add(criarTab("Vídeos das Aulas", VaadinIcon.PLAY_CIRCLE, "#D32F2F", VideosAulaProfessorView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.LOJA))
                                tabs.add(criarTab("Loja", VaadinIcon.SHOP, "#FF6F00", LojaView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.SUBSIDIOS))
                                tabs.add(criarTab("Subsídios", VaadinIcon.PIGGY_BANK, "#5E35B1", SubsidiosView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.REFERENCIAS))
                                tabs.add(criarTab("Referências", VaadinIcon.HANDSHAKE, "#00838F", ReferenciasView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.INQUERITOS))
                                tabs.add(criarTab("Inquéritos", VaadinIcon.CLIPBOARD_TEXT, "#F4511E", InqueritosView.class, null));
                        tabs.add(criarTab("Previsão de Receita", VaadinIcon.TRENDING_UP, "#2E7D32", PrevisaoReceitaView.class, null));
                        if (iaAtiva && (studioParaModulos == null
                                        || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.TREINADOR_IA)))
                                tabs.add(criarTab("Treinador IA", VaadinIcon.MAGIC, "#8B5CF6", TreinadorDancaView.class, null));

                        tabs.add(criarHeaderMenu("Configurações"));
                        tabs.add(criarSubTab("Utilizadores", VaadinIcon.SHIELD, UserView.class));
                        tabs.add(criarSubTab("Acessos ao Portal", VaadinIcon.KEY, ContasPortalView.class));
                        tabs.add(criarSubTab("Professores", VaadinIcon.ACADEMY_CAP, ProfessorView.class));
                        tabs.add(criarSubTab("Salas", VaadinIcon.HOME, SalaView.class));
                        tabs.add(criarSubTab("Modalidades", VaadinIcon.BOOK, ModalidadeView.class));
                        tabs.add(criarSubTab("Turmas", VaadinIcon.GROUP, TurmaView.class));
                        tabs.add(criarSubTab("Preçário", VaadinIcon.EURO, PrecarioView.class));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.AULAS_AVULSO))
                                tabs.add(criarSubTab("Packs de Aula Avulsa", VaadinIcon.TICKET, PackAulaView.class));
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
                        tabs.add(criarTab("Planeamento de Aulas", VaadinIcon.CLIPBOARD_CHECK, "#1d4ed8", PlaneamentoAulasView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.AVALIACOES))
                                tabs.add(criarTab("Avaliações", VaadinIcon.MEDAL, "#8E44AD", AvaliacoesView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.VIDEOS_AULA))
                                tabs.add(criarTab("Vídeos das Aulas", VaadinIcon.PLAY_CIRCLE, "#D32F2F", VideosAulaProfessorView.class, null));
                        if (studioParaModulos == null || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.EVENTOS))
                                tabs.add(criarTab("Eventos", VaadinIcon.STAR, "#F1C40F", ConvitesProfessorView.class, null));
                        if (iaAtiva && (studioParaModulos == null
                                        || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.TREINADOR_IA)))
                                tabs.add(criarTab("Treinador IA", VaadinIcon.MAGIC, "#8B5CF6", TreinadorDancaView.class, null));
                }

                // --- 4. ALUNO ---
                if (isAluno) {
                        tabs.add(criarHeaderMenu("Área do Aluno"));
                        tabs.add(criarTab("Portal", VaadinIcon.DASHBOARD, "#4A90E2", PortalAlunoView.class, null));
                        tabs.add(criarTab("Vídeos das Aulas", VaadinIcon.PLAY_CIRCLE, "#D32F2F", AlunoVideosView.class,
                                        null));
                        if (iaAtiva && (studioParaModulos == null
                                        || studioParaModulos.hasModulo(pt.studioflow.model.StudioModulo.TREINADOR_IA)))
                                tabs.add(criarTab("Treinador IA", VaadinIcon.MAGIC, "#8B5CF6", TreinadorDancaView.class, null));
                }

                scroller.setContent(tabs);

                // Seletor de filho (um email de encarregado pode ter vários alunos).
                if (isAluno) {
                        java.util.List<Aluno> filhos = authService.getAlunosDoPortal();
                        if (filhos.size() > 1) {
                                com.vaadin.flow.component.combobox.ComboBox<Aluno> seletor =
                                                new com.vaadin.flow.component.combobox.ComboBox<>();
                                seletor.setItems(filhos);
                                seletor.setItemLabelGenerator(a -> a.getNomeCompleto() != null
                                                ? a.getNomeCompleto() : ("Aluno " + a.getId()));
                                Aluno selAtual = authService.getAlunoSelecionado();
                                if (selAtual != null) seletor.setValue(selAtual);
                                seletor.setWidthFull();
                                seletor.addValueChangeListener(ev -> {
                                        if (ev.getValue() != null) {
                                                authService.setAlunoSelecionado(ev.getValue().getId());
                                                UI.getCurrent().getPage().reload();
                                        }
                                });
                                Div wrapper = new Div(seletor);
                                wrapper.addClassName("cf-drawer-selector");
                                drawerContent.addComponentAsFirst(wrapper);
                        }
                }

                // Footer de branding — fixo no fundo do drawer
                Span brandLine1 = new Span("CoreoFlow");
                brandLine1.addClassName("brand-line1");

                String appVersion = buildProperties.getVersion().replace("-SNAPSHOT", "");
                Span brandLine2 = new Span("v" + appVersion + " · by Daniel Silva");
                brandLine2.addClassName("brand-line2");

                com.vaadin.flow.component.html.Div brandingFooter = new com.vaadin.flow.component.html.Div(brandLine1, brandLine2);
                brandingFooter.addClassName("branding-footer");

                // Terminar sessão — sempre visível no fundo do drawer (no telemóvel
                // o menu de utilizador no topo pode não caber).
                Button btnLogoutDrawer = new Button("Terminar Sessão", VaadinIcon.POWER_OFF.create(),
                                e -> performLogout());
                btnLogoutDrawer.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                btnLogoutDrawer.setWidthFull();
                btnLogoutDrawer.addClassName("cf-drawer-logout-btn");
                com.vaadin.flow.component.html.Div logoutWrap = new com.vaadin.flow.component.html.Div(btnLogoutDrawer);
                logoutWrap.addClassName("cf-drawer-logout");

                drawerContent.add(scroller, logoutWrap, brandingFooter);
                addToDrawer(drawerContent);
        }

        private Tab criarTab(String texto, VaadinIcon icone, String cor,
                        Class<? extends com.vaadin.flow.component.Component> view, String badge) {
                // Ícones monocromáticos (cor dada pelo tema). O parâmetro "cor" fica apenas
                // por compatibilidade com as chamadas existentes.
                Icon icon = icone.create();

                Span label = new Span(texto);
                label.addClassName("nav-label");

                HorizontalLayout layout = new HorizontalLayout(icon, label);
                layout.setAlignItems(Alignment.CENTER);
                layout.setSpacing(false);
                layout.addClassName("nav-item");

                if (badge != null && !badge.equals("0")) {
                        Span b = new Span(badge);
                        b.addClassName("cf-nav-badge");
                        layout.add(b);
                }

                RouterLink link = new RouterLink();
                link.setRoute(view);
                link.add(layout);
                link.addClassName("cf-nav-link");
                // Item ativo = atributo "highlight" (estilizado no tema). Rota exata ou sub-rota,
                // para "turma" não acender também em "turmas-alunos", "horario" em "horario-salas", etc.
                link.setHighlightCondition((l, ev) -> {
                        String path = ev.getLocation().getPath();
                        String href = l.getHref();
                        return path.equals(href) || path.startsWith(href + "/");
                });

                return new Tab(link);
        }

        private Tab criarSubTab(String texto, VaadinIcon icone,
                        Class<? extends com.vaadin.flow.component.Component> view) {
                Tab tab = criarTab(texto, icone, null, view, null);
                tab.addClassName("cf-nav-sub");
                return tab;
        }

        private HorizontalLayout criarItemDropdown(VaadinIcon vaadinIcon, String label) {
                Icon icon = vaadinIcon.create();
                Span text = new Span(label);
                HorizontalLayout item = new HorizontalLayout(icon, text);
                item.setSpacing(false);
                item.addClassName("cf-menu-item");
                return item;
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
                                                UI.getCurrent().navigate(DashboardView.class);
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
                        String studioNome = TenantContext.getCurrentStudio() != null
                                ? TenantContext.getCurrentStudio().getNome() : "—";
                        String nomeUtilizador = user != null
                                ? user.getFirstName() + " (" + user.getUsername() + ")" : "—";
                        // Nunca falha para o utilizador: grava sempre em BD e tenta o email.
                        String emailUtilizador = user != null ? user.getEmail() : null;
                        pt.studioflow.model.PedidoSuporte p = suporteService.registarPedido(studioNome,
                                nomeUtilizador, emailUtilizador, tipo.getValue(), assunto.getValue(),
                                descricao.getValue());
                        Notification.show(p.isEmailEnviado()
                                ? "Pedido de suporte enviado com sucesso!"
                                : "Pedido registado. Vamos analisá-lo em breve.",
                                3500, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        dialog.close();
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

        private void abrirDialogAlterarPasswordPortal(pt.studioflow.model.ContaPortal conta) {
                if (conta == null)
                        return;
                Dialog dialog = new Dialog();
                dialog.setHeaderTitle("Segurança da Conta");

                PasswordField pw = new PasswordField("Nova Password");
                pw.setWidthFull();

                Button save = new Button("Atualizar", e -> {
                        if (pw.getValue() == null || pw.getValue().length() < 6) {
                                Notification.show("A password tem de ter pelo menos 6 caracteres.")
                                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                                return;
                        }
                        conta.setPasswordHash(passwordEncoder.encode(pw.getValue()));
                        contaPortalRepository.save(conta);
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
                Tab tab = new Tab(span);
                tab.addClassName("cf-nav-section");
                tab.setEnabled(false);
                return tab;
        }
}
