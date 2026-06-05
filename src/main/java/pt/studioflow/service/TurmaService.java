package pt.studioflow.service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Aula;
import pt.studioflow.model.Sala;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.TurmaRepository;

@Service
public class TurmaService {

    @Autowired
    private TurmaRepository turmaRepository;

    @Autowired
    private AlunoRepository alunoRepository;

    @Autowired
    private AlunoTurmaRepository alunoTurmaRepository;

    // =========================
    // TURMAS
    // =========================

    @Transactional(readOnly = true)
    public List<Turma> findAll() {
        return turmaRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Turma findById(Long id) {
        return turmaRepository.findById(id).orElseThrow();
    }

    @Transactional
    public Turma save(Turma turma) {
        return turmaRepository.save(turma);
    }

    @Transactional
    public void delete(Turma turma) {
        turmaRepository.delete(turma);
    }

    // =========================
    // ALUNOS DA TURMA
    // =========================

    @Transactional(readOnly = true)
    public List<Aluno> getAlunosDaTurma(Turma turma) {
        return alunoTurmaRepository.findByTurma(turma).stream()
                .map(AlunoTurma::getAluno)
                // REMOVE qualquer filtro .filter(Aluno::isAtivo)
                // Ou substitui por este que aceita Experimentais:
                .filter(a -> a.isAtivo() || a.getStatus() == AlunoStatus.EXPERIMENTAL)
                .collect(Collectors.toList());
    }

    @Transactional
    public void registarAlunoExperimental(String nome, String telemovel, Turma turma) {
        Aluno novo = new Aluno();
        novo.setNomeCompleto(nome);
        novo.setTelemovel(telemovel);
        novo.setStatus(Aluno.AlunoStatus.EXPERIMENTAL);
        novo.setAtivo(true); // Para que apareça nas listas atuais
        novo.setCarimboDataHora(LocalDate.now());

        // 1. Salva o aluno
        alunoRepository.save(novo);

        // 2. Cria a associação com a turma (entidade AlunoTurma)
        AlunoTurma associacao = new AlunoTurma();
        associacao.setAluno(novo);
        associacao.setTurma(turma);
        // associacao.setDataInscricao(LocalDate.now());

        alunoTurmaRepository.save(associacao);
    }

    public List<Turma> getTurmasPorAluno(String email) {
        return turmaRepository.findByAlunosEmail(email);
    }

    @Transactional(readOnly = true)
    public List<Turma> findAllComplete() {
        // Primeira passagem: carrega turmas + aulas para a cache
        turmaRepository.findAllWithAulas();

        // Segunda passagem: carrega as mesmas turmas + alunos e retorna
        // O Hibernate faz o "merge" automático na memória
        return turmaRepository.findAllWithAlunos();
    }
}
