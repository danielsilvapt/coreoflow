package pt.studioflow.view;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Presenca;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.PresencaRepository;

@Route(value = "crm", layout = MainLayout.class)
@PageTitle("CRM - Relacionamento | CoreoFlow")
@RolesAllowed({ "ADMIN" })
public class CRMView extends VerticalLayout {

    private final AlunoRepository alunoRepository;
    private final PresencaRepository presencaRepository;
    private final VerticalLayout containerConteudo = new VerticalLayout();

    public CRMView(AlunoRepository alunoRepository, PresencaRepository presencaRepository) {
        this.alunoRepository = alunoRepository;
        this.presencaRepository = presencaRepository;

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Centro de Relacionamento (CRM)");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        // Configuração das Abas
        Tab tabAniversarios = new Tab(VaadinIcon.GIFT.create(), new Span(" Aniversários"));
        Tab tabRisco = new Tab(VaadinIcon.LINE_CHART.create(), new Span(" Recuperação de Alunos"));
        Tabs tabs = new Tabs(tabAniversarios, tabRisco);
        tabs.setWidthFull();

        tabs.addSelectedChangeListener(event -> {
            containerConteudo.removeAll();
            if (event.getSelectedTab().equals(tabAniversarios)) {
                mostrarAniversarios();
            } else {
                mostrarAlunosEmRisco();
            }
        });

        containerConteudo.setSizeFull();
        containerConteudo.setPadding(false);

        VerticalLayout body = new VerticalLayout(tabs, containerConteudo);
        body.setSizeFull();
        body.setPadding(true);
        body.setSpacing(false);
        body.expand(containerConteudo);
        add(body);
        expand(body);
        
        // Iniciar na primeira aba
        mostrarAniversarios();
    }


    private void mostrarAniversarios() {
        containerConteudo.add(new H3("Aniversariantes de " + 
            LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, new Locale("pt"))));

        Grid<Aluno> grid = new Grid<>(Aluno.class, false);
        pt.studioflow.model.Studio _s = pt.studioflow.config.TenantContext.getCurrentStudio();
        List<Aluno> lista = (_s != null ? alunoRepository.findAllByStudio(_s) : alunoRepository.findAll()).stream()
                .filter(a -> a.getDataNascimento() != null && a.getDataNascimento().getMonth() == LocalDate.now().getMonth())
                .sorted(Comparator.comparingInt(a -> a.getDataNascimento().getDayOfMonth()))
                .collect(Collectors.toList());

        grid.setItems(lista);

        grid.addComponentColumn(a -> {
            Button whatsappBtn = new Button("Parabéns", new Icon(VaadinIcon.CHAT));
            whatsappBtn.getStyle().set("color", "#25D366");
            whatsappBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
            whatsappBtn.addClickListener(e -> {
                String mensagem = "Olá " + a.getNomeCompleto().split(" ")[0] + "! A CoreoFlow deseja-te um dia de aniversário fantástico, com muita dança e alegria!";
                abrirWhatsApp(a.getTelemovel(), mensagem);
            });
            return whatsappBtn;
        }).setHeader("Ação");

        grid.addColumn(Aluno::getNomeCompleto).setHeader("Aluno").setAutoWidth(true);
        grid.addColumn(a -> a.getDataNascimento().getDayOfMonth() + " de " +
                a.getDataNascimento().getMonth().getDisplayName(TextStyle.FULL, new Locale("pt")))
                .setHeader("Dia").setSortable(true);
        grid.addColumn(Aluno::getTelemovel).setHeader("Contacto");

        grid.setSizeFull();
        containerConteudo.add(grid);
    }

    private void mostrarAlunosEmRisco() {
        containerConteudo.add(new H3("Alunos Ausentes (> 15 dias)"));

        LocalDate limite = LocalDate.now().minusDays(15);
        pt.studioflow.model.Studio _sRisco = pt.studioflow.config.TenantContext.getCurrentStudio();
        List<Presenca> todasPresencas = _sRisco != null
                ? presencaRepository.findAllByAlunoStudio(_sRisco)
                : presencaRepository.findAll();

        List<Aluno> listaRisco = (_sRisco != null ? alunoRepository.findAllByStudio(_sRisco) : alunoRepository.findAll()).stream()
                .filter(Aluno::isAtivo)
                .filter(a -> {
                    Optional<LocalDate> ultima = todasPresencas.stream()
                            .filter(p -> p.getAluno().getId().equals(a.getId()))
                            .map(Presenca::getData).max(LocalDate::compareTo);
                    return ultima.map(d -> d.isBefore(limite)).orElse(true);
                }).collect(Collectors.toList());

        Grid<Aluno> grid = new Grid<>(Aluno.class, false);
        grid.setItems(listaRisco);

        grid.addComponentColumn(a -> {
            Button recuperarBtn = new Button("Contactar", new Icon(VaadinIcon.CHAT));
            recuperarBtn.getStyle().set("color", "#25D366");
            recuperarBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            recuperarBtn.addClickListener(e -> {
                String mensagem = "Olá " + a.getNomeCompleto().split(" ")[0] + "! Temos sentido a tua falta nas aulas da CoreoFlow. Está tudo bem contigo? Esperamos ver-te em breve!";
                abrirWhatsApp(a.getTelemovel(), mensagem);
            });
            return recuperarBtn;
        }).setHeader("Recuperação");

        grid.addColumn(Aluno::getNomeCompleto).setHeader("Aluno").setAutoWidth(true);
        grid.addColumn(a -> {
            return todasPresencas.stream()
                    .filter(p -> p.getAluno().getId().equals(a.getId()))
                    .map(Presenca::getData).max(LocalDate::compareTo)
                    .map(Object::toString).orElse("Nunca apareceu");
        }).setHeader("Última Aula").setSortable(true);

        grid.setSizeFull();
        containerConteudo.add(grid);
    }

    private void abrirWhatsApp(String telemovel, String mensagem) {
        if (telemovel == null || telemovel.isEmpty()) return;
        String encoded = java.net.URLEncoder.encode(mensagem, java.nio.charset.StandardCharsets.UTF_8);
        String url = "https://wa.me/351" + telemovel.replaceAll("[^0-9]", "") + "?text=" + encoded;
        com.vaadin.flow.component.UI.getCurrent().getPage().open(url, "_blank");
    }
}
