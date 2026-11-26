package pt.studioflow.view;

import java.time.LocalDate;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.data.binder.Binder;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.Idioma;
import pt.studioflow.service.TranslationService;

/**
 * Autorização de captação e divulgação de imagens (Autorizo / Não autorizo, obrigatória),
 * para o ano letivo da inscrição. A pergunta da divulgação (WhatsApp dos pais + redes sociais
 * do estúdio) só aparece - e só é obrigatória - quando a captação é autorizada. Partilhado pelos
 * formulários públicos de inscrição (via Binder) e de renovação (sem Binder).
 */
public class CamposAutorizacaoImagens extends VerticalLayout {

    private final TranslationService translationService;
    private final String nomeStudio;
    private final String anoLetivo = anoLetivoInscricao();
    private Idioma idioma;
    private boolean menor = true;

    private final H2 titulo = new H2();
    private final Paragraph finalidade = new Paragraph();
    private final RadioButtonGroup<Boolean> captacao = new RadioButtonGroup<>();
    private final RadioButtonGroup<Boolean> divulgacao = new RadioButtonGroup<>();

    public CamposAutorizacaoImagens(TranslationService translationService, Idioma idioma, String nomeStudio) {
        this.translationService = translationService;
        this.nomeStudio = nomeStudio != null && !nomeStudio.isBlank() ? nomeStudio : "estúdio";

        titulo.getStyle().set("font-size", "1.1em").set("margin", "0.5em 0 0 0");
        finalidade.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)").set("margin", "0.25em 0");
        captacao.setItems(Boolean.TRUE, Boolean.FALSE);
        captacao.setRequiredIndicatorVisible(true);
        divulgacao.setItems(Boolean.TRUE, Boolean.FALSE);
        divulgacao.setRequiredIndicatorVisible(true);
        divulgacao.setVisible(false);

        captacao.addValueChangeListener(e -> {
            boolean sim = Boolean.TRUE.equals(e.getValue());
            divulgacao.setVisible(sim);
            if (!sim) {
                divulgacao.clear();
                divulgacao.setInvalid(false);
            }
        });

        setIdioma(idioma);
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        add(titulo, finalidade, captacao, divulgacao);
    }

    /**
     * Ano letivo a que a ficha se refere: a partir de julho as inscrições/renovações já são
     * para o ano seguinte (ex.: julho 2026 → "2026/2027").
     */
    public static String anoLetivoInscricao() {
        LocalDate hoje = LocalDate.now();
        int inicio = hoje.getMonthValue() >= 7 ? hoje.getYear() : hoje.getYear() - 1;
        return inicio + "/" + (inicio + 1);
    }

    /** Ajusta o texto: "o meu educando" (menor) ou o próprio aluno (adulto). */
    public void setMenor(boolean menor) {
        this.menor = menor;
        atualizarTextos();
    }

    public void setIdioma(Idioma idioma) {
        this.idioma = idioma;
        atualizarTextos();
    }

    private void atualizarTextos() {
        titulo.setText(t("imagens.titulo"));
        finalidade.setText(t("imagens.finalidade") + nomeStudio + ".");
        captacao.setLabel(t("imagens.captacao") + nomeStudio
                + t(menor ? "imagens.captacaoMenor" : "imagens.captacaoAdulto") + anoLetivo + ".");
        divulgacao.setLabel(t("imagens.divulgacao") + nomeStudio + ".");
        captacao.setItemLabelGenerator(this::rotulo);
        divulgacao.setItemLabelGenerator(this::rotulo);
    }

    private String rotulo(Boolean v) {
        return Boolean.TRUE.equals(v) ? t("imagens.autorizo") : t("imagens.naoAutorizo");
    }

    /** Liga os campos ao Binder do formulário de inscrição. */
    public void bind(Binder<Aluno> binder) {
        binder.forField(captacao).asRequired(t("imagens.obrigatorio"))
                .bind(Aluno::getAutorizaCaptacaoImagens, (a, v) -> {
                    a.setAutorizaCaptacaoImagens(v);
                    a.setAutorizacaoImagensAno(anoLetivo);
                });
        binder.forField(divulgacao)
                .withValidator(v -> !Boolean.TRUE.equals(captacao.getValue()) || v != null,
                        t("imagens.divulgacaoObrigatorio"))
                .bind(Aluno::getAutorizaDivulgacaoImagens, (a, v) -> a.setAutorizaDivulgacaoImagens(
                        Boolean.TRUE.equals(captacao.getValue()) && Boolean.TRUE.equals(v)));
    }

    /**
     * Renovação: pré-preenche só se a autorização já for deste ano letivo — a autorização é
     * anual, por isso numa renovação para um ano novo tem de ser respondida outra vez.
     */
    public void preencher(Aluno aluno) {
        if (anoLetivo.equals(aluno.getAutorizacaoImagensAno())) {
            captacao.setValue(aluno.getAutorizaCaptacaoImagens());
            divulgacao.setValue(aluno.getAutorizaDivulgacaoImagens());
        }
    }

    /**
     * Renovação: valida e, se estiver tudo respondido, copia as respostas para o aluno.
     * Devolve false (e marca o campo em erro) quando falta alguma resposta.
     */
    public boolean validarEAplicar(Aluno aluno) {
        captacao.setInvalid(false);
        divulgacao.setInvalid(false);
        if (captacao.getValue() == null) {
            captacao.setErrorMessage(t("imagens.obrigatorio"));
            captacao.setInvalid(true);
            return false;
        }
        if (captacao.getValue() && divulgacao.getValue() == null) {
            divulgacao.setErrorMessage(t("imagens.divulgacaoObrigatorio"));
            divulgacao.setInvalid(true);
            return false;
        }
        aluno.setAutorizaCaptacaoImagens(captacao.getValue());
        aluno.setAutorizaDivulgacaoImagens(captacao.getValue() && Boolean.TRUE.equals(divulgacao.getValue()));
        aluno.setAutorizacaoImagensAno(anoLetivo);
        return true;
    }

    private String t(String chave) {
        return translationService.t(chave, idioma);
    }
}
