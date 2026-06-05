package pt.studioflow.view;

import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.repository.MensalidadeRepository;

public class MensalidadeForm extends FormLayout {

    private final TextField aluno = new TextField("Aluno");
    private final TextField turma = new TextField("Turma");
    private final NumberField valor = new NumberField("Valor");
    private final ComboBox<String> estado = new ComboBox<>("Estado");
    private final TextField ano = new TextField("Ano");
    private final TextField mes = new TextField("Mês");
    private final TextArea observacoesField = new TextArea("Observações");
    private final DatePicker diaPagamento = new DatePicker("Dia de Pagamento");

    private final List<String> estadosPagamento = Arrays.asList("Por Emitir", "Faturado", "Pago");

    private final Binder<Mensalidade> binder = new Binder<>(Mensalidade.class);

    private final Dialog dialog = new Dialog();
    private Consumer<Mensalidade> saveListener;
    private Runnable closeListener;

    private Mensalidade mensalidadeAtual;

    private final MensalidadeRepository mensalidadeRepository;

    public MensalidadeForm(MensalidadeRepository mensalidadeRepository) {
        this.mensalidadeRepository = mensalidadeRepository;

        // Configura ComboBox e TextArea
        estado.setItems(estadosPagamento);
        estado.setPlaceholder("Selecione...");
        observacoesField.setPlaceholder("Insira algumas observações...");
        observacoesField.setMaxLength(500);
        observacoesField.setHeight("120px");

        this.add(aluno, turma, ano, mes, valor, estado, diaPagamento, observacoesField);

        // Binder: liga campos à entidade
        binder.forField(aluno)
                .asRequired("Aluno é obrigatório")
                .bind(m -> m.getAluno() != null ? m.getAluno().getNomeCompleto() : "", (m, v) -> {
                });
        binder.forField(turma)
                .asRequired("Turma é obrigatória")
                .bind(m -> m.getTurma() != null ? m.getTurma().getDescricao() : "", (m, v) -> {
                });
        binder.forField(valor).asRequired("Valor é obrigatório").bind(Mensalidade::getValor, Mensalidade::setValor);
        binder.forField(estado)
                .asRequired("Estado é obrigatório")
                .bind(m -> traduzEstadoParaPortugues(m.getEstado()), (m, v) -> m.setEstado(traduzEstadoParaEnum(v)));
        binder.forField(ano).asRequired("Ano é obrigatório").bind(m -> String.valueOf(m.getAno()),
                (m, v) -> m.setAno(Integer.parseInt(v)));
        binder.forField(mes).asRequired("Mês é obrigatório").bind(m -> m.getMes().name(),
                (m, v) -> m.setMes(traduzPortugueseToMonth(v)));
        binder.forField(observacoesField).bind(Mensalidade::getObservacoes, Mensalidade::setObservacoes);
        binder.forField(diaPagamento).bind(Mensalidade::getDiaPagamento, Mensalidade::setDiaPagamento);

        // Configura dialog
        Button saveBtn = new Button("Guardar", e -> salvar());
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelBtn = new Button("Cancelar", e -> fechar());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(saveBtn, cancelBtn);
        dialog.add(this);
    }

    public void abrirFormulario(Mensalidade m) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(m == null ? "Nova Mensalidade" : "Editar Mensalidade");

        if (m != null) {
            carregarDados(m);
        } else {
            emptyFields();
            estado.setValue("Por Emitir"); // valor padrão
        }

        Button saveBtn = new Button("Guardar", e -> {
            if (saveListener != null) {
                Mensalidade mensalidadeParaSalvar = m != null ? m : new Mensalidade();
                // popular mensalidadeParaSalvar com os campos do form
                saveListener.accept(mensalidadeParaSalvar);
            }
            dialog.close();
        });
        
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("Cancelar", e -> {
            if (closeListener != null)
                closeListener.run();
            dialog.close();
        });
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(saveBtn, cancelBtn);
        dialog.add(this);
        dialog.open();
    }

    private void carregarDados(Mensalidade m) {
        aluno.setValue(m.getAluno().getNomeCompleto());
        turma.setValue(m.getTurma().getDescricao());
        ano.setValue(String.valueOf(m.getAno()));
        mes.setValue(m.getMes() != null ? m.getMes().name() : "");
        valor.setValue(m.getValor());
        estado.setValue(m.getEstado() != null ? traduzEstadoParaPortugues(m.getEstado()) : "Por Emitir");
        observacoesField.setValue(m.getObservacoes() != null ? m.getObservacoes() : "");
        diaPagamento.setValue(m.getDiaPagamento());
    }

    public void emptyFields() {
        aluno.clear();
        turma.clear();
        ano.clear();
        mes.clear();
        valor.clear();
        estado.setValue(""); // ou "Por Emitir"
        observacoesField.clear();
        diaPagamento.clear();
    }

    private void salvar() {
        binder.validate();
        if (saveListener != null) {
            saveListener.accept(mensalidadeAtual);
        }
        dialog.close();
    }

    private void fechar() {
        if (closeListener != null)
            closeListener.run();
        dialog.close();
    }

    public void addSaveListener(Consumer<Mensalidade> listener) {
        this.saveListener = listener;
    }

    public void addCloseListener(Runnable listener) {
        this.closeListener = listener;
    }

    private String traduzEstadoParaPortugues(EstadoMensalidade estado) {
        return switch (estado) {
            case PAGO -> "Pago";
            case FATURADO -> "Faturado";
            case POR_EMITIR -> "Por Emitir";
            case EM_DIVIDA -> "Em Dívida";
        };
    }

    private EstadoMensalidade traduzEstadoParaEnum(String estado) {
        return switch (estado) {
            case "Pago" -> EstadoMensalidade.PAGO;
            case "Faturado" -> EstadoMensalidade.FATURADO;
            case "Por Emitir" -> EstadoMensalidade.POR_EMITIR;
            default -> EstadoMensalidade.POR_EMITIR;
        };
    }

    public Month traduzPortugueseToMonth(String mes) {
        return switch (mes) {
            case "Janeiro" -> Month.JANUARY;
            case "Fevereiro" -> Month.FEBRUARY;
            case "Março" -> Month.MARCH;
            case "Abril" -> Month.APRIL;
            case "Maio" -> Month.MAY;
            case "Junho" -> Month.JUNE;
            case "Julho" -> Month.JULY;
            case "Agosto" -> Month.AUGUST;
            case "Setembro" -> Month.SEPTEMBER;
            case "Outubro" -> Month.OCTOBER;
            case "Novembro" -> Month.NOVEMBER;
            case "Dezembro" -> Month.DECEMBER;
            default -> throw new IllegalArgumentException("Mês inválido: " + mes);
        };
    }
}
