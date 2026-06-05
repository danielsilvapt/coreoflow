package pt.studioflow;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.TurmaRepository;
import pt.studioflow.service.MensalidadeService;

@Component
public class ApplicationRunner {

    @Autowired
    private MensalidadeService mensalidadeService;

    @Autowired
    private AlunoRepository alunoRepository;

    @Autowired
    private TurmaRepository turmaRepository;

    public void run() {
        // Supondo que já tenha um usuário e uma turma cadastrados
        /* 
        Optional<Aluno> optionalAluno = alunoRepository.findById(10L);
        Aluno aluno = optionalAluno.get(); 
        Optional<Turma> optionalTurma = turmaRepository.findById(1L);
        Turma turma = optionalTurma.get();
        */
        // Criar uma mensalidade para o mês de Fevereiro com estado "POR_LANCAR"
        //mensalidadeService.criarMensalidade(aluno.getId(), turma.getCodigo(), 2025, Month.FEBRUARY, EstadoMensalidade.POR_LANCAR);
    }
}
