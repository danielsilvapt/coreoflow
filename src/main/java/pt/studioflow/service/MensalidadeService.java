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

        // 🔹 Gerar mensalidades do mês atual até junho
        LocalDate hoje = LocalDate.now();
        int anoAtual = hoje.getYear();
        int mesAtual = hoje.getMonthValue();

        List<Mensalidade> mensalidades = new ArrayList<>();

        for (int mes = mesAtual; mes <= 6; mes++) { // de mês atual até junho
            Month monthEnum = Month.of(mes);

            // Verifica se já existe
            boolean jaExiste = mensalidadeRepository.existsByAlunoAndTurmaAndAnoAndMes(aluno, turma, anoAtual,
                    monthEnum);
            if (jaExiste)
                continue;

            Mensalidade m = new Mensalidade();
            m.setAluno(aluno);
            m.setTurma(turma);
            m.setAno(anoAtual);
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
