package pt.studioflow.view;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.repository.UserRepository;

import java.util.List;

@Route(value = "admin/dashboard", layout = MainLayout.class)
@PageTitle("Dashboard | SuperAdmin")
@RolesAllowed("SUPERADMIN")
public class SuperAdminDashboardView extends VerticalLayout {

    public SuperAdminDashboardView(StudioRepository studioRepository,
                                   UserRepository userRepository,
                                   AlunoRepository alunoRepository,
                                   TurmaRepository turmaRepository) {

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        List<Studio> studios = studioRepository.findAll();

        // ---- Cards de resumo ----
        long totalStudios = studios.size();
        long totalAtivos = studios.stream().filter(Studio::isAtivo).count();
        long totalUsers = studios.stream()
                .mapToLong(s -> userRepository.findAllByStudio(s).size())
                .sum();
        long totalAlunos = studios.stream()
                .mapToLong(s -> alunoRepository.findAllByStudio(s).size())
                .sum();
        long totalTurmas = studios.stream()
                .mapToLong(s -> turmaRepository.findAllByStudio(s).size())
                .sum();

        H2 titulo = new H2("Visão Geral da Plataforma");
        titulo.getStyle().set("margin-top", "0").set("margin-bottom", "8px");

        HorizontalLayout cards = new HorizontalLayout(
                criarCard("Estúdios", String.valueOf(totalStudios), VaadinIcon.BUILDING, "#4A90E2"),
                criarCard("Ativos", String.valueOf(totalAtivos), VaadinIcon.CHECK_CIRCLE, "#27AE60"),
                criarCard("Utilizadores", String.valueOf(totalUsers), VaadinIcon.USERS, "#7B61FF"),
                criarCard("Alunos", String.valueOf(totalAlunos), VaadinIcon.USER_HEART, "#E67E22"),
                criarCard("Turmas", String.valueOf(totalTurmas), VaadinIcon.GROUP, "#E91E63")
        );
        cards.setWidthFull();
        cards.setSpacing(true);

        // ---- Grid por estúdio ----
        record StudioStats(Studio studio, int users, int alunos, int turmas) {}

        List<StudioStats> rows = studios.stream().map(s -> new StudioStats(
                s,
                userRepository.findAllByStudio(s).size(),
                alunoRepository.findAllByStudio(s).size(),
                turmaRepository.findAllByStudio(s).size()
        )).toList();

        Grid<StudioStats> grid = new Grid<>();
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);

        grid.addColumn(st -> st.studio().getNome())
                .setHeader("Estúdio").setAutoWidth(true).setSortable(true);

        grid.addColumn(st -> st.studio().getSlug())
                .setHeader("Slug").setAutoWidth(true);

        grid.addComponentColumn(st -> {
            boolean ativo = st.studio().isAtivo();
            Span badge = new Span(ativo ? "Ativo" : "Inativo");
            badge.getStyle()
                    .set("background", ativo ? "#e8f5e9" : "#fce4ec")
                    .set("color", ativo ? "#2e7d32" : "#c62828")
                    .set("padding", "2px 10px")
                    .set("border-radius", "12px")
                    .set("font-size", "12px")
                    .set("font-weight", "600");
            return badge;
        }).setHeader("Estado").setAutoWidth(true).setSortable(true);

        grid.addColumn(st -> st.studio().getEmailContacto())
                .setHeader("Email Contacto").setAutoWidth(true);

        grid.addColumn(StudioStats::users)
                .setHeader("Utilizadores").setAutoWidth(true).setSortable(true);

        grid.addColumn(StudioStats::alunos)
                .setHeader("Alunos").setAutoWidth(true).setSortable(true);

        grid.addColumn(StudioStats::turmas)
                .setHeader("Turmas").setAutoWidth(true).setSortable(true);

        grid.setItems(rows);

        add(titulo, cards, grid);
        expand(grid);
    }

    private VerticalLayout criarCard(String label, String valor, VaadinIcon icone, String cor) {
        Icon icon = icone.create();
        icon.setSize("28px");
        icon.setColor(cor);

        Span valorSpan = new Span(valor);
        valorSpan.getStyle()
                .set("font-size", "32px")
                .set("font-weight", "700")
                .set("color", cor)
                .set("line-height", "1");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("font-size", "12px")
                .set("color", "#666")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.8px");

        VerticalLayout card = new VerticalLayout(icon, valorSpan, labelSpan);
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.setSpacing(false);
        card.setPadding(true);
        card.getStyle()
                .set("background", "white")
                .set("border-radius", "16px")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.08)")
                .set("border", "1px solid #f0f0f0")
                .set("min-width", "140px")
                .set("flex", "1");
        return card;
    }
}
