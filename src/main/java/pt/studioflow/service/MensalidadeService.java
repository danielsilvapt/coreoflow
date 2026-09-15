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
     * - Modelo {@code HORAS_SEMANA} → pacote único por total de horas/semana do
     *   aluno, repartido proporcionalmente por todas as suas turmas (ver
     *   {@link #gerarMensalidadesModeloHorasSemana}).
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
        Double valorProprio = config.valorProprioDaTurma(turma, aluno.isSocio());
        if (valorProprio != null) {
            double valor = valorProprio;
            gerarMensalidadesComValor(aluno, turma, studio, mes -> valor);
            return;
        }

        if (studio != null && studio.isModeloHorasSemana()) {
            gerarMensalidadesModeloHorasSemana(aluno, studio);
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

        double valorFinal = valorBase;
        gerarMensalidadesComValor(aluno, turma, studio, mes -> valorFinal);
    }

    /**
     * Modelo {@code HORAS_SEMANA}: soma as "aulas por semana" de todas as turmas
     * do aluno (excluindo as com mensalidade própria e as sem mensalidade),
     * procura o pacote correspondente na tabela do estúdio (com meio mês
     * automático em Setembro/Dezembro/Julho) e reparte esse valor por cada turma,
     * proporcionalmente ao seu peso semanal — assim a receita/remuneração por
     * turma continua a fazer sentido apesar do valor ser um pacote único.
     * Não recalcula mensalidades já geradas noutras turmas (mesma limitação do
     * modelo PADRAO: a mudança só se reflete nos meses ainda por gerar).
     */
    private void gerarMensalidadesModeloHorasSemana(Aluno aluno, pt.studioflow.model.Studio studio) {
        List<AlunoTurma> inscricoes = alunoTurmaRepository.findByAluno(aluno).stream()
                .filter(a -> !a.isSemMensalidade())
                .filter(a -> config.valorProprioDaTurma(a.getTurma(), aluno.isSocio()) == null)
                .toList();
        if (inscricoes.isEmpty()) {
            return;
        }

        int totalHoras = inscricoes.stream().mapToInt(AlunoTurma::getAulasPorSemana).sum();
        if (totalHoras <= 0) {
            return;
        }

        for (AlunoTurma insc : inscricoes) {
            double fracao = insc.getAulasPorSemana() / (double) totalHoras;
            gerarMensalidadesComValor(aluno, insc.getTurma(), studio, mes -> {
                double pacote = config.valorTabelaHoras(studio, totalHoras, config.isMesMeioMensalidade(mes));
                return Math.round(pacote * fracao * 100.0) / 100.0;
            });
        }
    }

    /**
     * Gera as mensalidades em falta (do mês atual até junho, atravessando o
     * ano civil quando necessário) de {@code aluno}/{@code turma}, com o valor
     * calculado por mês através de {@code valorPorMes}. Meses já existentes
     * ficam intactos (idempotente).
     */
    private void gerarMensalidadesComValor(Aluno aluno, Turma turma, pt.studioflow.model.Studio studio,
            java.util.function.Function<Month, Double> valorPorMes) {
        LocalDate hoje = LocalDate.now();
        int mesAtual = hoje.getMonthValue();

        java.time.YearMonth inicio;
        java.time.YearMonth fim;
        if (mesAtual >= 9) { // setembro–dezembro: ano letivo em curso termina em junho do ano seguinte
            inicio = java.time.YearMonth.of(hoje.getYear(), mesAtual);
            fim = java.time.YearMonth.of(hoje.getYear() + 1, 6);
        } else if (mesAtual >= 7) { // julho–agosto: arranca já o próximo ano letivo
            inicio = java.time.YearMonth.of(hoje.getYear(), 9);
            fim = java.time.YearMonth.of(hoje.getYear() + 1, 6);
        } else { // janeiro–junho: ano letivo em curso termina em junho deste ano
            inicio = java.time.YearMonth.of(hoje.getYear(), mesAtual);
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
            m.setStudio(studio);

            mensalidades.add(m);
        }

        if (!mensalidades.isEmpty()) {
            mensalidadeRepository.saveAll(mensalidades);
        }
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
