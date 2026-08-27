package pt.studioflow.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.TurmaRepository;

@Service
public class DashboardService {

    private final TurmaRepository turmaRepository;
    private final AlunoTurmaRepository alunoTurmaRepository;

    public DashboardService(
            TurmaRepository turmaRepository,
            AlunoTurmaRepository alunoTurmaRepository
    ) {
        this.turmaRepository = turmaRepository;
        this.alunoTurmaRepository = alunoTurmaRepository;
    }

    // =========================================================
    // RENOVAÇÕES DE MATRÍCULA
    // Recebem a lista de alunos do estúdio já carregada pelo chamador
    // (evita nova query e mantém a lógica testável sem base de dados).
    // =========================================================

    /** Pedidos de renovação a aguardar validação em ValidacaoInscricoesView. */
    public List<Aluno> listarRenovacoesPendentes(List<Aluno> alunosDoStudio) {
        return alunosDoStudio.stream()
                .filter(a -> a.getStatus() == AlunoStatus.PENDENTE && a.isPedidoRenovacao())
                .toList();
    }

    public long countRenovacoesPendentes(List<Aluno> alunosDoStudio) {
        return listarRenovacoesPendentes(alunosDoStudio).size();
    }

    /** Renovações já validadas (aluno ATIVO) dentro do ano letivo corrente. */
    public long countRenovacoesConcluidasAnoLetivo(List<Aluno> alunosDoStudio, LocalDate inicioAnoLetivo) {
        return alunosDoStudio.stream()
                .filter(a -> a.getStatus() == AlunoStatus.ATIVO)
                .filter(a -> a.getDataInscricaoRenovacao() != null
                        && !a.getDataInscricaoRenovacao().isBefore(inicioAnoLetivo))
                .count();
    }

    /**
     * Base de comparação: alunos que já estavam matriculados antes do início do
     * ano letivo corrente (candidatos a renovar, tenham já renovado ou não).
     */
    public long countElegiveisRenovacaoAnoLetivo(List<Aluno> alunosDoStudio, LocalDate inicioAnoLetivo) {
        return alunosDoStudio.stream()
                .filter(a -> a.getStatus() == AlunoStatus.ATIVO || a.getStatus() == AlunoStatus.INATIVO)
                .filter(a -> a.getCarimboDataHora() != null && a.getCarimboDataHora().isBefore(inicioAnoLetivo))
                .count();
    }

    /**
     * Número de alunos por turma
     */
    public Map<String, Integer> getAlunosPorTurma() {
        Map<String, Integer> result = new LinkedHashMap<>();

        List<Turma> turmas = turmaRepository.findAll();

        for (Turma turma : turmas) {
            int totalAlunos =
                    alunoTurmaRepository.findByTurma(turma).size();

            result.put(turma.getDescricao(), totalAlunos);
        }

        return result;
    }

    /**
     * Total geral de alunos
     */
    public int getTotalAlunos() {
        return alunoTurmaRepository.findAll().size();
    }

    /**
     * Total de turmas
     */
    public int getTotalTurmas() {
        return turmaRepository.count() > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) turmaRepository.count();
    }
}
