package pt.studioflow.view;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import pt.studioflow.model.Aluno;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.TurmaRepository;

import java.util.List;

@Route(value = "admin/saude", layout = MainLayout.class)
@PageTitle("Painel de Saúde | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class PainelSaudeView extends VerticalLayout {

    public PainelSaudeView(StudioRepository studioRepository,
                           AlunoRepository alunoRepository,
                           MensalidadeRepository mensalidadeRepository,
                           TurmaRepository turmaRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 titulo = new H2("Painel de Saúde da Plataforma");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        List<Studio> studios = studioRepository.findAll().stream()
                .filter(Studio::isAtivo).toList();

        for (Studio studio : studios) {
            add(criarCardEstudio(studio, alunoRepository, mensalidadeRepository, turmaRepository));
        }

        if (studios.isEmpty()) {
            Span vazio = new Span("Nenhum estúdio ativo.");
            vazio.getStyle().set("color", "#888");
            add(vazio);
        }
    }

    private VerticalLayout criarCardEstudio(Studio studio,
                                             AlunoRepository alunoRepo,
                                             MensalidadeRepository mensRepo,
                                             TurmaRepository turmaRepo) {
        // Métricas
        long alunosPendentes = alunoRepo.findAllByStudio(studio).stream()
                .filter(a -> a.getStatus() == Aluno.AlunoStatus.PENDENTE
                          || a.getStatus() == Aluno.AlunoStatus.EXPERIMENTAL)
                .count();

        long mensalidadesPorEmitir = mensRepo.findAllByStudio(studio).stream()
                .filter(m -> m.getEstado() == EstadoMensalidade.POR_EMITIR)
                .count();

        long mensalidadesEmDivida = mensRepo.findAllByStudio(studio).stream()
                .filter(m -> m.getEstado() == EstadoMensalidade.EM_DIVIDA)
                .count();

        long turmasSemProf = turmaRepo.findAllByStudio(studio).stream()
                .filter(t -> t.getProfessor() == null && t.isAtivo())
                .count();

        // Semáforo geral
        boolean critico = mensalidadesEmDivida > 5 || turmasSemProf > 0;
        boolean atencao = alunosPendentes > 0 || mensalidadesPorEmitir > 3;
        String semaforo = critico ? "#E74C3C" : atencao ? "#F39C12" : "#27AE60";
        String semaforoLabel = critico ? "Crítico" : atencao ? "Atenção" : "OK";

        // Card
        VerticalLayout card = new VerticalLayout();
        card.setPadding(true);
        card.setSpacing(false);
        card.getStyle()
                .set("background", "white")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.07)")
                .set("border-left", "4px solid " + semaforo)
                .set("margin-bottom", "8px");

        // Header do card
        Span nomeStudio = new Span(studio.getNome());
        nomeStudio.getStyle().set("font-weight", "700").set("font-size", "16px");

        Span slug = new Span("@" + studio.getSlug());
        slug.getStyle().set("color", "#888").set("font-size", "12px").set("margin-left", "8px");

        Span badge = new Span(semaforoLabel);
        badge.getStyle()
                .set("background", semaforo).set("color", "white")
                .set("padding", "2px 10px").set("border-radius", "12px")
                .set("font-size", "11px").set("font-weight", "700")
                .set("margin-left", "auto");

        HorizontalLayout header = new HorizontalLayout(nomeStudio, slug, badge);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        header.getStyle().set("margin-bottom", "10px");

        // Métricas em linha
        HorizontalLayout metricas = new HorizontalLayout(
                criarMetrica("Pendentes", alunosPendentes, VaadinIcon.USER_CLOCK, "#E67E22", alunosPendentes > 0),
                criarMetrica("Por Emitir", mensalidadesPorEmitir, VaadinIcon.INVOICE, "#3498DB", mensalidadesPorEmitir > 0),
                criarMetrica("Em Dívida", mensalidadesEmDivida, VaadinIcon.WARNING, "#E74C3C", mensalidadesEmDivida > 0),
                criarMetrica("Turmas s/ Prof", turmasSemProf, VaadinIcon.ACADEMY_CAP, "#9B59B6", turmasSemProf > 0)
        );
        metricas.setSpacing(true);
        metricas.getStyle().set("flex-wrap", "wrap");

        card.add(header, metricas);
        return card;
    }

    private VerticalLayout criarMetrica(String label, long valor, VaadinIcon icone,
                                         String cor, boolean alerta) {
        Icon icon = icone.create();
        icon.setSize("16px");
        icon.setColor(alerta ? cor : "#bbb");

        Span valorSpan = new Span(String.valueOf(valor));
        valorSpan.getStyle()
                .set("font-size", "22px").set("font-weight", "700")
                .set("color", alerta ? cor : "#ccc");

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("font-size", "11px").set("color", "#888");

        VerticalLayout box = new VerticalLayout(icon, valorSpan, labelSpan);
        box.setAlignItems(FlexComponent.Alignment.CENTER);
        box.setSpacing(false);
        box.setPadding(true);
        box.getStyle()
                .set("min-width", "110px")
                .set("background", alerta ? "#fff8f0" : "#fafafa")
                .set("border-radius", "10px")
                .set("border", "1px solid " + (alerta ? cor + "44" : "#eee"));
        return box;
    }
}
