package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Studio;
import pt.studioflow.model.SubsidioAluno;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.SubsidioAlunoRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "subsidios", layout = MainLayout.class)
@PageTitle("Subsídios | CoreoFlow")
@RolesAllowed("ADMIN")
public class SubsidiosView extends VerticalLayout {

    private final SubsidioAlunoRepository subsidioRepo;
    private final AlunoRepository alunoRepo;
    private Grid<SubsidioAluno> grid;

    public SubsidiosView(SubsidioAlunoRepository subsidioRepo, AlunoRepository alunoRepo) {
        this.subsidioRepo = subsidioRepo;
        this.alunoRepo = alunoRepo;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        add(ViewUtils.toolbar(ViewUtils.botaoNovo("Novo Apoio", e -> abrirDialog(null))),
            criarGrid());
        atualizar();
    }

    private Grid<SubsidioAluno> criarGrid() {
        grid = new Grid<>(SubsidioAluno.class, false);
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        grid.addComponentColumn(s -> {
            Button editar = new Button(VaadinIcon.EDIT.create(), e -> abrirDialog(s));
            editar.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button del = new Button(VaadinIcon.TRASH.create(), e -> { subsidioRepo.delete(s); atualizar(); });
            del.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            return new HorizontalLayout(editar, del);
        }).setHeader("Ações").setAutoWidth(true);

        grid.addColumn(s -> s.getAluno().getNomeCompleto()).setHeader("Aluno").setFlexGrow(2).setSortable(true);
        grid.addColumn(SubsidioAluno::getEntidade).setHeader("Entidade").setAutoWidth(true);
        grid.addColumn(s -> s.getDescricaoApoio() != null ? s.getDescricaoApoio() : "—")
                .setHeader("Descrição").setFlexGrow(1);
        grid.addColumn(s -> String.format("%.0f%%", s.getPercentagem()))
                .setHeader("Desconto").setAutoWidth(true);
        grid.addComponentColumn(s -> {
            if (s.getDataRenovacao() == null) return new Span("—");
            boolean expirado = s.getDataRenovacao().isBefore(LocalDate.now());
            boolean alerta = s.getDataRenovacao().isBefore(LocalDate.now().plusDays(30));
            String texto = s.getDataRenovacao().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            Span b = new Span((expirado ? "⚠️ " : alerta ? "🔔 " : "") + texto);
            b.getStyle().set("color", expirado ? "#E74C3C" : alerta ? "#E67E22" : "#27AE60")
                    .set("font-weight", expirado || alerta ? "700" : "400");
            return b;
        }).setHeader("Renovação").setAutoWidth(true);
        grid.addComponentColumn(s -> {
            Span b = new Span(s.isAtivo() ? "Ativo" : "Inativo");
            b.getStyle().set("color", s.isAtivo() ? "#27AE60" : "#888").set("font-weight", "600");
            return b;
        }).setHeader("Estado").setAutoWidth(true);

        return grid;
    }

    private void atualizar() {
        Studio s = TenantContext.getCurrentStudio();
        grid.setItems(s != null ? subsidioRepo.findByStudioOrderByDataRenovacaoAsc(s)
                                 : subsidioRepo.findAll());
    }

    private void abrirDialog(SubsidioAluno sub) {
        boolean novo = sub == null;
        SubsidioAluno subsidio = novo ? new SubsidioAluno() : sub;
        Studio studio = TenantContext.getCurrentStudio();

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(novo ? "Novo Apoio/Subsídio" : "Editar Apoio");
        dialog.setWidth("460px");

        ComboBox<Aluno> aluno = new ComboBox<>("Aluno");
        aluno.setItems(studio != null ? alunoRepo.findAllByStudio(studio) : alunoRepo.findAll());
        aluno.setItemLabelGenerator(Aluno::getNomeCompleto);
        aluno.setRequired(true); aluno.setWidthFull();
        if (subsidio.getAluno() != null) aluno.setValue(subsidio.getAluno());

        ComboBox<String> entidade = new ComboBox<>("Entidade");
        entidade.setItems("IPDJ", "Câmara Municipal", "Junta de Freguesia", "Fundo Social", "Outro");
        entidade.setAllowCustomValue(true);
        entidade.addCustomValueSetListener(e -> entidade.setValue(e.getDetail()));
        entidade.setRequired(true); entidade.setWidthFull();
        if (subsidio.getEntidade() != null) entidade.setValue(subsidio.getEntidade());

        TextField descricao = new TextField("Descrição do Apoio");
        descricao.setWidthFull();
        descricao.setValue(subsidio.getDescricaoApoio() != null ? subsidio.getDescricaoApoio() : "");

        NumberField percentagem = new NumberField("Desconto (%)");
        percentagem.setMin(0); percentagem.setMax(100); percentagem.setWidthFull();
        percentagem.setValue(subsidio.getPercentagem());

        DatePicker dataInicio = new DatePicker("Data de Início");
        dataInicio.setWidthFull();
        if (subsidio.getDataInicio() != null) dataInicio.setValue(subsidio.getDataInicio());

        DatePicker dataRenovacao = new DatePicker("Data de Renovação");
        dataRenovacao.setWidthFull();
        if (subsidio.getDataRenovacao() != null) dataRenovacao.setValue(subsidio.getDataRenovacao());

        TextArea obs = new TextArea("Observações");
        obs.setWidthFull();
        obs.setValue(subsidio.getObservacoes() != null ? subsidio.getObservacoes() : "");

        Checkbox ativo = new Checkbox("Apoio ativo");
        ativo.setValue(subsidio.isAtivo());

        Button guardar = new Button("Guardar", e -> {
            if (aluno.getValue() == null || entidade.getValue() == null) {
                Notification.show("Aluno e Entidade são obrigatórios"); return;
            }
            subsidio.setAluno(aluno.getValue());
            subsidio.setEntidade(entidade.getValue());
            subsidio.setDescricaoApoio(descricao.getValue().trim());
            subsidio.setPercentagem(percentagem.getValue() != null ? percentagem.getValue() : 0);
            subsidio.setDataInicio(dataInicio.getValue());
            subsidio.setDataRenovacao(dataRenovacao.getValue());
            subsidio.setObservacoes(obs.getValue().trim());
            subsidio.setAtivo(ativo.getValue());
            subsidio.setStudio(studio);
            subsidioRepo.save(subsidio);
            atualizar();
            dialog.close();
            Notification.show("Apoio guardado").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dialog.add(new FormLayout(aluno, entidade, descricao, percentagem, dataInicio, dataRenovacao, obs, ativo));
        dialog.getFooter().add(new Button("Cancelar", e -> dialog.close()), guardar);
        dialog.open();
    }
}
