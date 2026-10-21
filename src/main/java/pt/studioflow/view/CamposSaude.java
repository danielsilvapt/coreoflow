package pt.studioflow.view;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.binder.Binder;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.Idioma;
import pt.studioflow.service.TranslationService;

/**
 * Pergunta "O aluno tem algum problema de saúde?" (Sim/Não, obrigatória) com o campo
 * "Qual?", que só aparece - e só é obrigatório - quando a resposta é Sim. Partilhado
 * pelos formulários públicos de inscrição (via Binder) e de renovação (sem Binder).
 */
public class CamposSaude extends VerticalLayout {

    private final TranslationService translationService;
    private Idioma idioma;

    private final RadioButtonGroup<Boolean> temProblema = new RadioButtonGroup<>();
    private final TextArea qual = new TextArea();

    public CamposSaude(TranslationService translationService, Idioma idioma) {
        this.translationService = translationService;

        temProblema.setItems(Boolean.TRUE, Boolean.FALSE);
        qual.setMaxLength(1000);
        qual.setWidthFull();
        qual.setVisible(false);

        temProblema.addValueChangeListener(e -> {
            boolean sim = Boolean.TRUE.equals(e.getValue());
            qual.setVisible(sim);
            if (!sim) {
                qual.clear();
            }
        });

        setIdioma(idioma);
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        add(temProblema, qual);
    }

    public void setIdioma(Idioma idioma) {
        this.idioma = idioma;
        temProblema.setLabel(t("saude.pergunta"));
        temProblema.setItemLabelGenerator(v -> Boolean.TRUE.equals(v) ? t("saude.sim") : t("saude.nao"));
        qual.setLabel(t("saude.qual"));
        qual.setPlaceholder(t("saude.placeholder"));
    }

    /** Liga os campos ao Binder do formulário de inscrição. */
    public void bind(Binder<Aluno> binder) {
        binder.forField(temProblema).asRequired(t("saude.obrigatorio"))
                .bind(Aluno::getTemProblemaSaude, Aluno::setTemProblemaSaude);
        binder.forField(qual)
                .withValidator(v -> !Boolean.TRUE.equals(temProblema.getValue()) || (v != null && !v.isBlank()),
                        t("saude.indicaQual"))
                .bind(Aluno::getProblemaSaude, Aluno::setProblemaSaude);
    }

    /** Renovação: pré-preenche com a resposta anterior (se existir). */
    public void preencher(Aluno aluno) {
        temProblema.setValue(aluno.getTemProblemaSaude());
        qual.setValue(aluno.getProblemaSaude() != null ? aluno.getProblemaSaude() : "");
    }

    /**
     * Renovação: valida e, se estiver tudo preenchido, copia as respostas para o aluno.
     * Devolve false (e marca o campo em erro) quando falta alguma resposta.
     */
    public boolean validarEAplicar(Aluno aluno) {
        temProblema.setInvalid(false);
        qual.setInvalid(false);
        if (temProblema.getValue() == null) {
            temProblema.setErrorMessage(t("saude.obrigatorio"));
            temProblema.setInvalid(true);
            return false;
        }
        if (temProblema.getValue() && qual.getValue().isBlank()) {
            qual.setErrorMessage(t("saude.indicaQual"));
            qual.setInvalid(true);
            return false;
        }
        aluno.setTemProblemaSaude(temProblema.getValue());
        aluno.setProblemaSaude(qual.getValue());
        return true;
    }

    private String t(String chave) {
        return translationService.t(chave, idioma);
    }
}
