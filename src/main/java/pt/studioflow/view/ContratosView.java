package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.ContratoDigital;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.ContratoDigitalRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "contratos", layout = MainLayout.class)
@PageTitle("Contratos Digitais | CoreoFlow")
@RolesAllowed("ADMIN")
public class ContratosView extends VerticalLayout {

    private final ContratoDigitalRepository contratoRepo;
    private final AlunoRepository alunoRepo;
    private Grid<ContratoDigital> grid;

    public ContratosView(ContratoDigitalRepository contratoRepo, AlunoRepository alunoRepo) {
        this.contratoRepo = contratoRepo;
        this.alunoRepo = alunoRepo;
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        H2 titulo = new H2("Contratos Digitais");
        titulo.getStyle().set("margin", "0 0 8px 0").set("padding", "20px 20px 0 20px");

        add(titulo, ViewUtils.toolbar(ViewUtils.botaoNovo("Gerar Contrato", e -> abrirGerador())),
            criarGrid());
        atualizar();
    }

    private Grid<ContratoDigital> criarGrid() {
        grid = new Grid<>(ContratoDigital.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addComponentColumn(c -> {
            Button ver = new Button(VaadinIcon.EYE.create(), e -> verContrato(c));
            ver.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            ver.getElement().setProperty("title", "Ver contrato");

            Button del = new Button(VaadinIcon.TRASH.create(), e -> {
                contratoRepo.delete(c); atualizar();
            });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return new HorizontalLayout(ver, del);
        }).setHeader("Ações").setAutoWidth(true);

        grid.addColumn(c -> c.getAluno().getNomeCompleto()).setHeader("Aluno").setFlexGrow(2).setSortable(true);
        grid.addColumn(c -> c.getTipo() + " · " + c.getAnoLetivo()).setHeader("Contrato").setFlexGrow(1);
        grid.addColumn(c -> c.getDataGeracao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .setHeader("Gerado").setAutoWidth(true).setSortable(true);

        grid.addComponentColumn(c -> {
            boolean assinado = "ASSINADO".equals(c.getEstado());
            Span b = new Span(assinado ? "✅ Assinado" : "⏳ Pendente");
            b.getStyle().set("color", assinado ? "#27AE60" : "#E67E22").set("font-weight", "600");
            return b;
        }).setHeader("Estado").setAutoWidth(true);

        grid.addColumn(c -> c.getDataAssinatura() != null
                ? c.getDataAssinatura().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "—")
                .setHeader("Data Assinatura").setAutoWidth(true);

        return grid;
    }

    private void abrirGerador() {
        Studio studio = TenantContext.getCurrentStudio();
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Gerar Contrato Digital");
        dialog.setWidth("480px");

        ComboBox<Aluno> alunoCombo = new ComboBox<>("Aluno");
        List<Aluno> alunos = studio != null ? alunoRepo.findAllByStudio(studio) : alunoRepo.findAll();
        alunoCombo.setItems(alunos);
        alunoCombo.setItemLabelGenerator(Aluno::getNomeCompleto);
        alunoCombo.setRequired(true);
        alunoCombo.setWidthFull();

        ComboBox<String> tipo = new ComboBox<>("Tipo de Contrato");
        tipo.setItems("Inscrição", "Renovação");
        tipo.setValue("Inscrição");
        tipo.setWidthFull();

        TextField anoLetivo = new TextField("Ano Letivo");
        anoLetivo.setPlaceholder("ex: 2024/2025");
        anoLetivo.setValue(anoLetivoAtual());
        anoLetivo.setWidthFull();

        dialog.add(new FormLayout(alunoCombo, tipo, anoLetivo));

        Button gerar = new Button("Gerar e Enviar ao Aluno", e -> {
            if (alunoCombo.getValue() == null || anoLetivo.isEmpty()) {
                Notification.show("Preenche todos os campos"); return;
            }
            ContratoDigital c = new ContratoDigital();
            c.setAluno(alunoCombo.getValue());
            c.setTipo(tipo.getValue());
            c.setAnoLetivo(anoLetivo.getValue().trim());
            c.setConteudo(gerarConteudoHtml(alunoCombo.getValue(), tipo.getValue(),
                    anoLetivo.getValue(), studio));
            c.setStudio(studio);
            contratoRepo.save(c);
            atualizar();
            dialog.close();
            Notification.show("Contrato gerado! O aluno pode assinar no Portal.")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        gerar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), gerar);
        dialog.open();
    }

    private void verContrato(ContratoDigital c) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(c.getTipo() + " · " + c.getAluno().getNomeCompleto());
        dialog.setWidth("700px");
        dialog.setMaxWidth("98vw");
        dialog.setMaxHeight("90vh");

        Html conteudo = new Html("<div style='padding:16px;font-family:sans-serif;line-height:1.6'>"
                + c.getConteudo() + "</div>");
        com.vaadin.flow.component.orderedlayout.Scroller scroller =
                new com.vaadin.flow.component.orderedlayout.Scroller(conteudo);
        scroller.setHeight("500px");
        scroller.setWidth("100%");

        dialog.add(scroller);
        dialog.getFooter().add(new Button("Fechar", e -> dialog.close()));
        dialog.open();
    }

    private void atualizar() {
        Studio s = TenantContext.getCurrentStudio();
        grid.setItems(s != null ? contratoRepo.findByStudioOrderByDataGeracaoDesc(s)
                                 : contratoRepo.findAll());
    }

    private String anoLetivoAtual() {
        int ano = LocalDate.now().getYear();
        return (LocalDate.now().getMonthValue() >= 9 ? ano : ano - 1) + "/"
                + (LocalDate.now().getMonthValue() >= 9 ? ano + 1 : ano);
    }

    private String gerarConteudoHtml(Aluno aluno, String tipo, String anoLetivo, Studio studio) {
        String nomeStudio = studio != null ? studio.getNome() : "Estúdio";
        String data = LocalDate.now().format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy",
                new java.util.Locale("pt")));
        String turmas = aluno.getTurmas() != null
                ? aluno.getTurmas().stream().map(at -> at.getTurma().getDescricao())
                        .reduce("", (a, b) -> a.isEmpty() ? b : a + ", " + b)
                : "—";

        return "<h2 style='text-align:center;color:#2B3A6B'>CONTRATO DE " + tipo.toUpperCase()
                + "</h2>"
                + "<p style='text-align:center;color:#888'>Ano Letivo " + anoLetivo + "</p>"
                + "<hr/>"
                + "<p><strong>" + nomeStudio + "</strong>, adiante designado por <em>Estúdio</em>, "
                + "e o aluno <strong>" + aluno.getNomeCompleto() + "</strong>"
                + (aluno.getNumeroIdentificacao() != null ? " (CC: " + aluno.getNumeroIdentificacao() + ")" : "")
                + ", acordam o seguinte:</p>"
                + "<h3>1. Objeto</h3>"
                + "<p>O presente contrato tem por objeto a " + tipo.toLowerCase()
                + " do aluno nas seguintes atividades: <strong>" + turmas + "</strong>.</p>"
                + "<h3>2. Mensalidades</h3>"
                + "<p>O pagamento das mensalidades deverá ser efetuado até ao dia 8 de cada mês.</p>"
                + "<h3>3. Rescisão</h3>"
                + "<p>Qualquer das partes pode rescindir o contrato com um aviso prévio de 30 dias.</p>"
                + "<h3>4. Dados Pessoais</h3>"
                + "<p>Os dados pessoais são tratados nos termos do RGPD, exclusivamente para gestão das atividades.</p>"
                + "<hr/>"
                + "<p>Data: " + data + "</p>"
                + "<br/><br/>"
                + "<table width='100%'><tr>"
                + "<td width='50%' style='text-align:center'>_________________________<br/>" + nomeStudio + "</td>"
                + "<td width='50%' style='text-align:center'>_________________________<br/>" + aluno.getNomeCompleto() + "</td>"
                + "</tr></table>";
    }
}
