package pt.studioflow.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

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
