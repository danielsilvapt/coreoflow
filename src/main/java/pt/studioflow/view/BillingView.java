package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.FaturaStudio;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.FaturaStudioRepository;
import pt.studioflow.repository.StudioRepository;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

@Route(value = "admin/billing", layout = MainLayout.class)
@PageTitle("Billing | CoreoFlow")
@RolesAllowed("SUPERADMIN")
public class BillingView extends VerticalLayout {

    private final FaturaStudioRepository faturaRepo;
    private final StudioRepository studioRepo;
    private final AlunoRepository alunoRepo;
    private Grid<FaturaStudio> grid;

    public BillingView(FaturaStudioRepository faturaRepo,
                       StudioRepository studioRepo,
                       AlunoRepository alunoRepo) {
        this.faturaRepo = faturaRepo;
        this.studioRepo = studioRepo;
        this.alunoRepo = alunoRepo;

        setSizeFull();
        setPadding(true);

        H2 titulo = new H2("Billing dos Estúdios");
        titulo.getStyle().set("margin-top", "0");

        // Resumo rápido
        List<FaturaStudio> pendentes = faturaRepo.findByEstadoOrderByDataVencimentoAsc(FaturaStudio.Estado.PENDENTE);
        double totalPendente = pendentes.stream().mapToDouble(FaturaStudio::getValor).sum();

        HorizontalLayout resumo = new HorizontalLayout();
        resumo.setWidthFull();
        resumo.getStyle().set("flex-wrap", "wrap").set("gap", "12px").set("margin-bottom", "16px");
        resumo.add(
            resumoCard("Faturas Pendentes", String.valueOf(pendentes.size()), "#E67E22"),
            resumoCard("Valor Pendente", String.format("%.2f €", totalPendente), "#E74C3C"),
            resumoCard("Estúdios com Plano",
                String.valueOf(studioRepo.findAll().stream().filter(s -> s.getPlano() != null).count()),
                "#1976D2")
        );

        Button gerarMes = new Button("Gerar Faturas do Mês Atual", VaadinIcon.INVOICE.create(),
                e -> gerarFaturasMesAtual());
        gerarMes.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        gerarMes.getStyle().set("background-color", "#2B3A6B");

        add(titulo, resumo, gerarMes, new H3("Histórico de Faturas"), criarGrid());
        atualizar();
    }

    private Grid<FaturaStudio> criarGrid() {
        grid = new Grid<>(FaturaStudio.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addComponentColumn(f -> {
            if (f.getEstado() == FaturaStudio.Estado.PENDENTE || f.getEstado() == FaturaStudio.Estado.VENCIDA) {
                Button pagar = new Button("Marcar Paga", e -> {
                    f.setEstado(FaturaStudio.Estado.PAGA);
                    f.setDataPagamento(LocalDate.now());
                    faturaRepo.save(f);
                    atualizar();
                    Notification.show("Fatura marcada como paga")
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                });
                pagar.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
                return pagar;
            }
            return new Span(f.getDataPagamento() != null
                    ? f.getDataPagamento().toString() : "");
        }).setHeader("Ação").setAutoWidth(true);

        grid.addColumn(f -> f.getStudio().getNome()).setHeader("Estúdio").setFlexGrow(1).setSortable(true);
        grid.addColumn(f -> f.getPlano() != null ? f.getPlano().getNome() : "—").setHeader("Plano").setAutoWidth(true);
        grid.addColumn(f -> Month.of(f.getMes()).getDisplayName(TextStyle.SHORT, new Locale("pt"))
                + " " + f.getAno()).setHeader("Período").setAutoWidth(true).setSortable(true);
        grid.addColumn(f -> String.format("%.2f €", f.getValor())).setHeader("Valor").setAutoWidth(true);
        grid.addColumn(f -> f.getAlunosAtivos() + " alunos").setHeader("Alunos").setAutoWidth(true);

        grid.addComponentColumn(f -> {
            String[] cfg = switch (f.getEstado()) {
                case PENDENTE -> new String[]{"#fff3e0","#E67E22","Pendente"};
                case PAGA     -> new String[]{"#e8f5e9","#27AE60","Paga"};
                case VENCIDA  -> new String[]{"#fce4ec","#C62828","Vencida"};
            };
            Span b = new Span(cfg[2]);
            b.getStyle().set("background",cfg[0]).set("color",cfg[1])
                    .set("padding","2px 8px").set("border-radius","10px")
                    .set("font-size","11px").set("font-weight","600");
            return b;
        }).setHeader("Estado").setAutoWidth(true);

        return grid;
    }

    private void gerarFaturasMesAtual() {
        int ano = LocalDate.now().getYear();
        int mes = LocalDate.now().getMonthValue();
        int geradas = 0;

        for (Studio studio : studioRepo.findAll().stream().filter(Studio::isAtivo).toList()) {
            if (studio.getPlano() == null) continue;
            // Não gerar se já existe para este mês
            if (faturaRepo.findByStudioAndAnoAndMes(studio, ano, mes).isPresent()) continue;

            int alunosAtivos = (int) alunoRepo.findAllByStudio(studio).stream()
                    .filter(a -> a.getStatus() == Aluno.AlunoStatus.ATIVO).count();

            FaturaStudio fatura = new FaturaStudio();
            fatura.setStudio(studio);
            fatura.setPlano(studio.getPlano());
            fatura.setAno(ano);
            fatura.setMes(mes);
            fatura.setValor(studio.getPlano().getPrecoMensal());
            fatura.setAlunosAtivos(alunosAtivos);
            fatura.setDataEmissao(LocalDate.now());
            fatura.setDataVencimento(LocalDate.now().plusDays(15));
            faturaRepo.save(fatura);
            geradas++;
        }

        atualizar();
        Notification.show(geradas > 0
                ? "Geradas " + geradas + " faturas para " + Month.of(mes).getDisplayName(TextStyle.FULL, new Locale("pt")) + " " + ano
                : "Todos os estúdios com plano já têm fatura para este mês")
                .addThemeVariants(geradas > 0 ? NotificationVariant.LUMO_SUCCESS : NotificationVariant.LUMO_CONTRAST);
    }

    private void atualizar() {
        grid.setItems(faturaRepo.findAllByOrderByAnoDescMesDesc());
    }

    private VerticalLayout resumoCard(String label, String valor, String cor) {
        Span v = new Span(valor);
        v.getStyle().set("font-size", "24px").set("font-weight", "700").set("color", cor);
        Span l = new Span(label);
        l.getStyle().set("font-size", "11px").set("color", "#888").set("text-transform", "uppercase");
        VerticalLayout card = new VerticalLayout(v, l);
        card.setAlignItems(FlexComponent.Alignment.CENTER);
        card.setSpacing(false);
        card.setPadding(true);
        card.getStyle()
                .set("background", "white").set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)").set("min-width", "140px");
        return card;
    }
}
