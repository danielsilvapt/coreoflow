package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MensalidadeService {

    @Autowired
    private MensalidadeRepository mensalidadeRepository;

    @Autowired
    private AlunoRepository alunoRepository;

    @Autowired
    private TurmaRepository turmaRepository;

    @Autowired
    private AlunoTurmaRepository alunoTurmaRepository;

    @Autowired
    private MensalidadeConfig config; // nova classe para valores parametrizados

    /**
     * Cria uma mensalidade para um aluno específico.
     */
    public Mensalidade criarMensalidade(int alunoId, int turmaId, int ano, Month mes, EstadoMensalidade estado,
            double valor) {
        Aluno aluno = alunoRepository.findById((long) alunoId)
                .orElseThrow(() -> new IllegalArgumentException("Aluno não encontrado"));
        Turma turma = turmaRepository.findById((long) turmaId)
                .orElseThrow(() -> new IllegalArgumentException("Turma não encontrada"));

        Mensalidade mensalidade = new Mensalidade();
        mensalidade.setAluno(aluno);
        mensalidade.setTurma(turma);
        mensalidade.setAno(ano);
        mensalidade.setMes(mes);
        mensalidade.setEstado(estado);
        mensalidade.setValor(valor);

        return mensalidadeRepository.save(mensalidade);
    }

    /**
     * Gera mensalidades para um aluno de uma turma, considerando o modelo de
     * preçário do estúdio:
     * - Turma com mensalidade própria (competição, workshops) → esse valor, sempre.
     * - Modelos por pacote ({@code HORAS_SEMANA}, {@code MODALIDADES}) → pacote
     *   único do aluno, repartido proporcionalmente por todas as suas turmas (ver
     *   {@link #gerarMensalidadesModeloPacote}).
     * - Modelo {@code PADRAO} (omissão) → criança/adulto × 1x/2x, com acréscimo
     *   de não-sócio.
     */
    @Transactional
    public void gerarMensalidadesParaAluno(Aluno aluno, Turma turma) {
        // 🔹 Obter associação Aluno-Turma
        Optional<AlunoTurma> atOptional = alunoTurmaRepository.findByAlunoAndTurma(aluno, turma);
        if (atOptional.isEmpty())
            return;

        AlunoTurma at = atOptional.get();

        // Inscrição só para presenças (ex.: pré-competição) → não gera mensalidade
        if (at.isSemMensalidade()) {
            return;
        }

        pt.studioflow.model.Studio studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        if (studio == null) {
            // fallback: recarrega o aluno para garantir que o studio está acessível
            aluno = alunoRepository.findById(aluno.getId()).orElse(aluno);
            studio = aluno.getStudio();
        }

        // Turmas com mensalidade própria (competição, workshops) não seguem a tabela
        // do estúdio nem levam o acréscimo de não-sócio — o valor configurado é final,
        // independentemente do modelo de preçário do estúdio.
        Double valorProprio = config.valorProprioDaTurma(turma, aluno.isSocio(), at.getAulasPorSemana());
        if (valorProprio != null) {
            double valor = valorProprio;
            gerarMensalidadesComValor(aluno, turma, studio, mes -> valor, null);
            return;
        }

        if (studio != null && studio.isModeloPacote()) {
            gerarMensalidadesModeloPacote(aluno, studio);
            return;
        }

        int aulasPorSemana = at.getAulasPorSemana(); // 1 ou 2
        boolean crianca = aluno.isCrianca();
        boolean socio = aluno.isSocio();

        double valorBase = crianca
                ? (aulasPorSemana == 1 ? config.getValorCrianca1x(studio) : config.getValorCrianca2x(studio))
                : (aulasPorSemana == 1 ? config.getValorAdulto1x(studio) : config.getValorAdulto2x(studio));
        if (!socio) {
            valorBase += studio.getMensalidadeNaoSocioAdicional();
        }
        boolean familiar = aluno.isDescontoFamiliar();
        if (familiar) {
            valorBase = config.aplicarDescontoFamiliar(studio, valorBase);
        }

        double valorFinal = valorBase;
        gerarMensalidadesComValor(aluno, turma, studio, mes -> valorFinal,
                familiar ? TipoDesconto.DESCONTO_FAMILIARES : null);
    }

    /**
     * Modelos por pacote: junta as turmas do aluno (excluindo as com mensalidade
     * própria e as sem mensalidade), procura o pacote correspondente na tabela do
     * estúdio — por total de aulas/semana ({@code HORAS_SEMANA}) ou por número de
     * modalidades ({@code MODALIDADES}), com meio mês automático em
     * Setembro/Dezembro/Julho — e reparte esse valor por cada turma,
     * proporcionalmente ao seu peso semanal — assim a receita/remuneração por
     * turma continua a fazer sentido apesar do valor ser um pacote único.
     * Não recalcula mensalidades já geradas noutras turmas (mesma limitação do
     * modelo PADRAO: a mudança só se reflete nos meses ainda por gerar).
     */
    private void gerarMensalidadesModeloPacote(Aluno aluno, pt.studioflow.model.Studio studio) {
        List<AlunoTurma> inscricoes = inscricoesNoPacote(aluno);
        if (inscricoes.isEmpty()) {
            return;
        }

        int totalHoras = inscricoes.stream().mapToInt(AlunoTurma::getAulasPorSemana).sum();
        if (totalHoras <= 0) {
            return;
        }

        // Desconto de familiares/irmãos: incide sobre o pacote, antes de o repartir.
        boolean familiar = aluno.isDescontoFamiliar();
        java.util.Map<Turma, Integer> turmasEFrequencia = frequencias(inscricoes);
        for (AlunoTurma insc : inscricoes) {
            double fracao = insc.getAulasPorSemana() / (double) totalHoras;
            gerarMensalidadesComValor(aluno, insc.getTurma(), studio, mes -> {
                double pacote = config.valorPacote(studio, turmasEFrequencia, config.isMesMeioMensalidade(mes));
                if (familiar) {
                    pacote = config.aplicarDescontoFamiliar(studio, pacote);
                }
                return Math.round(pacote * fracao * 100.0) / 100.0;
            }, familiar ? TipoDesconto.DESCONTO_FAMILIARES : null);
        }
    }

    /** Inscrições do aluno que entram no pacote mensal (sem mensalidade própria nem isentas). */
    private List<AlunoTurma> inscricoesNoPacote(Aluno aluno) {
        return alunoTurmaRepository.findByAluno(aluno).stream()
                .filter(a -> !a.isSemMensalidade())
                .filter(a -> config.valorProprioDaTurma(a.getTurma(), aluno.isSocio(), a.getAulasPorSemana()) == null)
                .toList();
    }

    private static java.util.Map<Turma, Integer> frequencias(List<AlunoTurma> inscricoes) {
        java.util.Map<Turma, Integer> mapa = new java.util.LinkedHashMap<>();
        inscricoes.forEach(a -> mapa.merge(a.getTurma(), a.getAulasPorSemana(), Integer::sum));
        return mapa;
    }

    /**
     * Valor completo (antes de descontos) do pacote do aluno no mês, segundo as
     * inscrições atuais. Usado para recalcular um pacote quando muda o desconto.
     */
    @Transactional(readOnly = true)
    public double valorPacoteAtual(Aluno aluno, pt.studioflow.model.Studio studio, Month mes) {
        java.util.Map<Turma, Integer> turmas = frequencias(inscricoesNoPacote(aluno));
        return turmas.isEmpty() ? 0.0 : config.valorPacote(studio, turmas, config.isMesMeioMensalidade(mes));
    }

    /**
     * Gera as mensalidades em falta (do mês atual até junho, atravessando o
     * ano civil quando necessário) de {@code aluno}/{@code turma}, com o valor
     * calculado por mês através de {@code valorPorMes}. Meses já existentes
     * ficam intactos (idempotente).
     */
    private void gerarMensalidadesComValor(Aluno aluno, Turma turma, pt.studioflow.model.Studio studio,
            java.util.function.Function<Month, Double> valorPorMes, TipoDesconto desconto) {
        LocalDate hoje = LocalDate.now();
        int mesAtual = hoje.getMonthValue();

        java.time.YearMonth inicio = inicioPeriodoLetivo(hoje);
        java.time.YearMonth fim;
        if (mesAtual >= 7) { // julho–dezembro: o ano letivo termina em junho do ano seguinte
            fim = java.time.YearMonth.of(hoje.getYear() + 1, 6);
        } else { // janeiro–junho: ano letivo em curso termina em junho deste ano
            fim = java.time.YearMonth.of(hoje.getYear(), 6);
        }

        List<Mensalidade> mensalidades = new ArrayList<>();

        for (java.time.YearMonth ym = inicio; !ym.isAfter(fim); ym = ym.plusMonths(1)) {
            Month monthEnum = ym.getMonth();
            int ano = ym.getYear();

            // Verifica se já existe
            boolean jaExiste = mensalidadeRepository.existsByAlunoAndTurmaAndAnoAndMes(aluno, turma, ano,
                    monthEnum);
            if (jaExiste)
                continue;

            Mensalidade m = new Mensalidade();
            m.setAluno(aluno);
            m.setTurma(turma);
            m.setAno(ano);
            m.setMes(monthEnum);
            m.setEstado(EstadoMensalidade.POR_EMITIR);
            m.setValor(valorPorMes.apply(monthEnum));
            m.setDesconto(desconto);
            m.setStudio(studio);

            mensalidades.add(m);
        }

        if (!mensalidades.isEmpty()) {
            mensalidadeRepository.saveAll(mensalidades);
        }
    }

    /**
     * Modelos por pacote: a mensalidade do mês é um pacote único repartido
     * pelas turmas do aluno, por isso pagar uma parte é pagar o pacote. Marca como
     * {@code PAGO} as restantes partes desse aluno/mês (excluindo turmas com
     * mensalidade própria, que são cobranças à parte). Devolve quantas mudaram.
     * Noutros modelos não faz nada.
     */
    @Transactional
    public int marcarPacoteDoMesComoPago(Mensalidade paga, pt.studioflow.model.Studio studio) {
        if (studio == null || !studio.isModeloPacote() || paga.getEstado() != EstadoMensalidade.PAGO) {
            return 0;
        }
        Aluno aluno = paga.getAluno();
        List<Mensalidade> outras = mensalidadeRepository.findByAlunoAndAnoAndMes(aluno, paga.getAno(), paga.getMes())
                .stream()
                .filter(m -> !m.getId().equals(paga.getId()))
                .filter(m -> m.getEstado() != EstadoMensalidade.PAGO)
                .filter(m -> config.valorProprioDaTurma(m.getTurma(), aluno.isSocio()) == null)
                .toList();
        outras.forEach(m -> {
            m.setEstado(EstadoMensalidade.PAGO);
            if (m.getDiaPagamento() == null) {
                m.setDiaPagamento(paga.getDiaPagamento() != null ? paga.getDiaPagamento() : LocalDate.now());
            }
        });
        mensalidadeRepository.saveAll(outras);
        return outras.size();
    }

    /** Observação que marca as mensalidades oferecidas na inscrição. */
    public static final String OBS_OFERTA_PRIMEIRA = "1ª mensalidade oferecida na inscrição";

    /**
     * Oferece a primeira mensalidade a um aluno novo: as mensalidades do mês mais
     * antigo gerado (todas as turmas desse mês) passam a 0€ e pagas. Não faz nada
     * se o aluno já recebeu esta oferta antes.
     */
    @Transactional
    public void oferecerPrimeiraMensalidade(Aluno aluno) {
        List<Mensalidade> mensalidades = mensalidadeRepository.findByAluno(aluno);
        if (mensalidades.isEmpty()
                || mensalidades.stream().anyMatch(m -> OBS_OFERTA_PRIMEIRA.equals(m.getObservacoes()))) {
            return;
        }
        java.time.YearMonth primeiro = mensalidades.stream()
                .map(m -> java.time.YearMonth.of(m.getAno(), m.getMes()))
                .min(java.util.Comparator.naturalOrder()).get();
        List<Mensalidade> doPrimeiroMes = mensalidades.stream()
                .filter(m -> m.getAno() == primeiro.getYear() && m.getMes() == primeiro.getMonth())
                .filter(m -> m.getEstado() == EstadoMensalidade.POR_EMITIR)
                .toList();
        doPrimeiroMes.forEach(m -> {
            m.setValor(0.0);
            m.setEstado(EstadoMensalidade.PAGO);
            m.setDiaPagamento(LocalDate.now());
            m.setObservacoes(OBS_OFERTA_PRIMEIRA);
        });
        mensalidadeRepository.saveAll(doPrimeiroMes);
    }

    /**
     * Primeiro mês para o qual se geram mensalidades numa inscrição feita em
     * {@code data}: o próprio mês, exceto em julho–agosto, em que já arranca o
     * próximo ano letivo (setembro).
     */
    public static java.time.YearMonth inicioPeriodoLetivo(LocalDate data) {
        int mes = data.getMonthValue();
        return mes == 7 || mes == 8
                ? java.time.YearMonth.of(data.getYear(), 9)
                : java.time.YearMonth.of(data.getYear(), mes);
    }

    /**
     * Gera mensalidades para todos os alunos de uma turma.
     */
    public void gerarMensalidadesParaTurma(Turma turma) {
        List<AlunoTurma> alunosTurma = alunoTurmaRepository.findByTurma(turma);
        for (AlunoTurma at : alunosTurma) {
            gerarMensalidadesParaAluno(at.getAluno(), turma);
        }
    }

    public List<Mensalidade> obterMensalidadesPorAluno(Aluno aluno) {
        return mensalidadeRepository.findByAluno(aluno);
    }
}
