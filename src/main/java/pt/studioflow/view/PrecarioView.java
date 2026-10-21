package pt.studioflow.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Studio;
import pt.studioflow.service.StudioService;

/**
 * Preçário do estúdio, editável pelo próprio ADMIN: modelo de preçário
 * (criança/adulto × 1x/2x ou tabela por aulas/semana somadas entre todas as
 * turmas do aluno), vencimento, multa, descontos e taxas. Os mesmos campos
 * continuam disponíveis ao SUPERADMIN em {@link StudioAdminView}.
 */
@Route(value = "precario", layout = MainLayout.class)
@PageTitle("Preçário | CoreoFlow")
@RolesAllowed("ADMIN")
public class PrecarioView extends VerticalLayout {

    private final StudioService studioService;

    public PrecarioView(StudioService studioService) {
        this.studioService = studioService;

        setWidthFull();
        setMaxWidth("900px");

        H2 titulo = new H2("Preçário");
        titulo.getStyle().set("margin-top", "0");
        add(titulo);

        Studio sessao = TenantContext.getCurrentStudio();
        Studio studio = sessao != null && sessao.getId() != null
                ? studioService.findById(sessao.getId()).orElse(null) : null;
        if (studio == null) {
            add(new Span("Sem estúdio ativo na sessão."));
            return;
        }
        construirFormulario(studio);
    }

    private void construirFormulario(Studio studio) {
        // --- Modelo de preçário ---
        ComboBox<String> modeloPrecario = new ComboBox<>("Modelo de preçário");
        modeloPrecario.setItems("PADRAO", "HORAS_SEMANA");
        modeloPrecario.setItemLabelGenerator(v -> "HORAS_SEMANA".equals(v)
                ? "Por aulas/semana (soma de todas as turmas)" : "Criança/Adulto × 1x/2x por turma");
        modeloPrecario.setValue(studio.getModeloPrecario() != null ? studio.getModeloPrecario() : "PADRAO");
        modeloPrecario.setWidthFull();
        modeloPrecario.setHelperText("\"Por aulas/semana\": soma as aulas semanais de todas as turmas do aluno, "
                + "independentemente da modalidade, e cobra um valor único (ex.: um aluno em duas modalidades "
                + "de 1x/semana paga o escalão de 2 aulas/semana). Desliga os descontos automáticos de "
                + "família e +modalidades.");

        NumberField crianca1x = campoEuro("Criança 1x/semana (€)", studio.getMensalidadeCrianca1x());
        NumberField crianca2x = campoEuro("Criança 2x/semana (€)", studio.getMensalidadeCrianca2x());
        NumberField adulto1x = campoEuro("Adulto 1x/semana (€)", studio.getMensalidadeAdulto1x());
        NumberField adulto2x = campoEuro("Adulto 2x/semana (€)", studio.getMensalidadeAdulto2x());
        NumberField naoSocio = campoEuro("Adicional não sócio (€)", studio.getMensalidadeNaoSocioAdicional());
        FormLayout grupoPadrao = grelha(crianca1x, crianca2x, adulto1x, adulto2x, naoSocio);

        NumberField horas1 = campoEuro("1 aula/semana (€)", valorOu(studio.getTabelaHoras1(), 35.0));
        NumberField horas2 = campoEuro("2 aulas/semana (€)", valorOu(studio.getTabelaHoras2(), 43.0));
        NumberField horas3 = campoEuro("3 aulas/semana (€)", valorOu(studio.getTabelaHoras3(), 49.0));
        NumberField horas4 = campoEuro("4 aulas/semana (€)", valorOu(studio.getTabelaHoras4(), 56.0));
        NumberField horas5 = campoEuro("5 aulas/semana (€)", valorOu(studio.getTabelaHoras5(), 60.0));
        NumberField horas6 = campoEuro("6+ aulas/semana — passe (€)", valorOu(studio.getTabelaHoras6Mais(), 65.0));
        Checkbox meiaMensalidade = new Checkbox("Meia mensalidade automática em Setembro, Dezembro e Julho");
        meiaMensalidade.setValue(studio.isMeiaMensalidadeAutomatica());
        FormLayout grupoHoras = grelha(horas1, horas2, horas3, horas4, horas5, horas6, meiaMensalidade);
        grupoHoras.setColspan(meiaMensalidade, 3);

        Runnable ajustarVisibilidade = () -> {
            boolean horas = "HORAS_SEMANA".equals(modeloPrecario.getValue());
            grupoPadrao.setVisible(!horas);
            grupoHoras.setVisible(horas);
        };
        modeloPrecario.addValueChangeListener(e -> ajustarVisibilidade.run());
        ajustarVisibilidade.run();

        // --- Pagamentos ---
        NumberField diaLimite = new NumberField("Dia limite de pagamento (1–28)");
        if (studio.getDiaLimitePagamento() != null) {
            diaLimite.setValue(studio.getDiaLimitePagamento().doubleValue());
        }
        diaLimite.setMin(1);
        diaLimite.setMax(28);
        diaLimite.setStep(1);
        diaLimite.setClearButtonVisible(true);
        diaLimite.setHelperText("Vazio = sem vencimento automático.");
        NumberField multa = campoEuro("Multa por atraso (%)", valorOu(studio.getMultaAtrasoPercentagem(), 0.0));
        multa.setHelperText("Aplicada enquanto a mensalidade estiver \"Em dívida\".");
        NumberField taxaInscricao = campoEuro("Taxa de inscrição (€)", valorOu(studio.getTaxaInscricao(), 0.0));
        NumberField taxaRenovacao = campoEuro("Taxa de renovação (€)", valorOu(studio.getTaxaRenovacao(), 0.0));
        Checkbox primeiraGratis = new Checkbox("Primeira mensalidade gratuita para alunos novos");
        primeiraGratis.setValue(studio.isPrimeiraMensalidadeGratis());
        primeiraGratis.setTooltipText("Ao aceitar a inscrição de um aluno novo, as mensalidades do primeiro mês "
                + "ficam a 0€ e pagas. Não se aplica a renovações.");

        // --- Descontos ---
        NumberField descFamiliares = campoEuro("Desconto familiares (€)", studio.getDescontoFamiliaresEuros());
        NumberField descDirecao = campoEuro("Desconto direção (%)", studio.getDescontoDirecaoPercentagem());
        NumberField descMaisModal = campoEuro("Desconto +modalidades (%)", studio.getDescontoMaisModalidadesPercentagem());
        NumberField descMais65 = campoEuro("Desconto +65 anos (%)", studio.getDescontoMais65Percentagem());

        Span notaAlteracoes = new Span("As alterações aplicam-se às mensalidades geradas a partir de agora; "
                + "as mensalidades já geradas mantêm o valor.");
        notaAlteracoes.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        Button guardar = new Button("Guardar", e -> {
            studio.setModeloPrecario(modeloPrecario.getValue());
            studio.setMensalidadeCrianca1x(crianca1x.getValue());
            studio.setMensalidadeCrianca2x(crianca2x.getValue());
            studio.setMensalidadeAdulto1x(adulto1x.getValue());
            studio.setMensalidadeAdulto2x(adulto2x.getValue());
            studio.setMensalidadeNaoSocioAdicional(naoSocio.getValue());
            studio.setTabelaHoras1(horas1.getValue());
            studio.setTabelaHoras2(horas2.getValue());
            studio.setTabelaHoras3(horas3.getValue());
            studio.setTabelaHoras4(horas4.getValue());
            studio.setTabelaHoras5(horas5.getValue());
            studio.setTabelaHoras6Mais(horas6.getValue());
            studio.setMeiaMensalidadeAutomatica(meiaMensalidade.getValue());
            studio.setDiaLimitePagamento(diaLimite.getValue() != null
                    ? Math.max(1, Math.min(28, (int) Math.round(diaLimite.getValue()))) : null);
            studio.setMultaAtrasoPercentagem(multa.getValue() != null ? Math.max(0, multa.getValue()) : 0.0);
            studio.setTaxaInscricao(taxaInscricao.getValue());
            studio.setTaxaRenovacao(taxaRenovacao.getValue());
            studio.setPrimeiraMensalidadeGratis(primeiraGratis.getValue());
            studio.setDescontoFamiliaresEuros(descFamiliares.getValue());
            studio.setDescontoDirecaoPercentagem(descDirecao.getValue());
            studio.setDescontoMaisModalidadesPercentagem(descMaisModal.getValue());
            studio.setDescontoMais65Percentagem(descMais65.getValue());

            // A sessão guarda uma cópia do estúdio: atualiza-a para os cálculos usarem já os novos valores.
            TenantContext.setCurrentStudio(studioService.save(studio));
            Notification.show("Preçário guardado.").addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        guardar.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(seccao("Mensalidades"), modeloPrecario, grupoPadrao, grupoHoras,
                seccao("Pagamentos e taxas"), grelha(diaLimite, multa, taxaInscricao, taxaRenovacao),
                primeiraGratis,
                seccao("Descontos"), grelha(descFamiliares, descDirecao, descMaisModal, descMais65),
                notaAlteracoes, guardar);
    }

    private static H4 seccao(String texto) {
        H4 h = new H4(texto);
        h.getStyle().set("margin", "16px 0 4px 0");
        return h;
    }

    private static FormLayout grelha(com.vaadin.flow.component.Component... campos) {
        FormLayout f = new FormLayout(campos);
        f.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 3));
        f.setWidthFull();
        return f;
    }

    private static NumberField campoEuro(String label, Double valor) {
        NumberField f = new NumberField(label);
        f.setValue(valor);
        f.setMin(0);
        return f;
    }

    private static Double valorOu(Double v, double omissao) {
        return v != null ? v : omissao;
    }
}
