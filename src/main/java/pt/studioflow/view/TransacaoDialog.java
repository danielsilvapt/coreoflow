package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import pt.studioflow.model.TipoTransacao;
import pt.studioflow.model.Transacao;
import pt.studioflow.repository.TransacaoRepository;

import java.time.LocalDate;
import java.util.Arrays;

public class TransacaoDialog extends Dialog {

    private Transacao transacao;
    private final TransacaoRepository repository;
    private final Runnable onSave;

    private ComboBox<TipoTransacao> tipo = new ComboBox<>("Tipo", TipoTransacao.values());
    private DatePicker data = new DatePicker("Data");
    private ComboBox<String> categoria = new ComboBox<>("Categoria (Geral)");
    private TextField descricao = new TextField("Descrição");
    private TextField valorField = new TextField("Valor (€)");
    private TextField linkDoc = new TextField("Link Google Drive");

    // Construtor para NOVO lançamento
    public TransacaoDialog(TransacaoRepository repository, Runnable onSave) {
        this(repository, onSave, new Transacao());
    }

    // Construtor para EDIÇÃO (Corrigido: transacaoExistente sem espaço)
    public TransacaoDialog(TransacaoRepository repository, Runnable onSave, Transacao transacaoExistente) {
        this.repository = repository;
        this.onSave = onSave;
        this.transacao = transacaoExistente;

        setHeaderTitle(transacao.getId() == null ? "Novo Lançamento" : "Editar Lançamento");
        setWidth("450px");

        // Configurações visuais
        tipo.setWidthFull();
        data.setWidthFull();
        categoria.setItems(Arrays.asList("Mensalidades", "Pagamento Professores", "Renda Pavilhão", "Eletricidade/Água",
                "Seguros", "Figurinos", "Espetáculos", "Outros"));
        categoria.setAllowCustomValue(true);
        categoria.setWidthFull();
        descricao.setWidthFull();
        valorField.setPlaceholder("0.00");
        valorField.setPrefixComponent(new Span("€ "));
        valorField.setWidthFull();
        linkDoc.setWidthFull();

        // Preencher dados se for edição
        if (transacao.getId() != null) {
            tipo.setValue(transacao.getTipo());
            data.setValue(transacao.getData());
            categoria.setValue(transacao.getCategoria());
            descricao.setValue(transacao.getDescricao());
            valorField.setValue(String.valueOf(transacao.getValor()));
            linkDoc.setValue(transacao.getLinkDocumento());
        } else {
            tipo.setValue(TipoTransacao.RECEITA);
            data.setValue(LocalDate.now());
        }

        VerticalLayout layout = new VerticalLayout(tipo, data, categoria, descricao, valorField, linkDoc);
        add(layout);

        Button btnGuardar = new Button("Registar", e -> guardar());
        btnGuardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        getFooter().add(new Button("Cancelar", i -> close()), btnGuardar);
    }

    private void guardar() {
        String val = valorField.getValue().replace(",", ".");
        if (val.isEmpty() || categoria.getValue() == null) {
            Notification.show("Preencha o valor e a categoria!");
            return;
        }
        try {
            transacao.setTipo(tipo.getValue());
            transacao.setData(data.getValue());
            transacao.setCategoria(categoria.getValue());
            transacao.setDescricao(descricao.getValue());
            transacao.setValor(Double.parseDouble(val));
            transacao.setLinkDocumento(linkDoc.getValue());

            repository.save(transacao);
            onSave.run();
            close();
            Notification.show("Guardado com sucesso!");
        } catch (Exception ex) {
            Notification.show("Erro no valor!");
        }
    }
}