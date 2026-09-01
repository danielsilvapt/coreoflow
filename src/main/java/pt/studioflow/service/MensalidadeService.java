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
     * Gera mensalidades para um aluno de uma turma, considerando:
     * - Tipo de aluno (criança/adulto)
     * - Frequência de aulas por semana
     * - Se não for sócio, adiciona 10€
     */
    @Transactional
    public void gerarMensalidadesParaAluno(Aluno aluno, Turma turma) {
        // 🔹 Obter associação Aluno-Turma
        Optional<AlunoTurma> atOptional = alunoTurmaRepository.findByAlunoAndTurma(aluno, turma);
        if (atOptional.isEmpty())
            return;

        AlunoTurma at = atOptional.get();

        // 🔹 Determinar aulas por semana e tipo de aluno
        int aulasPorSemana = at.getAulasPorSemana(); // 1 ou 2
        boolean crianca = aluno.isCrianca();
        boolean socio = aluno.isSocio();

        // 🔹 Calcular valor base com base no tipo e frequência
        double valorBase;
        pt.studioflow.model.Studio studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        if (studio == null) {
            // fallback: recarrega o aluno para garantir que o studio está acessível
            aluno = alunoRepository.findById(aluno.getId()).orElse(aluno);
            studio = aluno.getStudio();
        }
        if (crianca) {
            valorBase = (aulasPorSemana == 1 ? config.getValorCrianca1x(studio) : config.getValorCrianca2x(studio));
        } else {
            valorBase = (aulasPorSemana == 1 ? config.getValorAdulto1x(studio) : config.getValorAdulto2x(studio));
        }

        // Acrescenta adicional se não for sócio
        if (!socio) {
            valorBase += studio.getMensalidadeNaoSocioAdicional();
        }

        // 🔹 Gerar mensalidades do mês atual até junho (fim do ano letivo).
        // O ano letivo começa em setembro (ano N) e termina em junho (ano N+1),
        // por isso o ciclo tem de atravessar a mudança de ano civil.
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
            m.setValor(valorBase);
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
