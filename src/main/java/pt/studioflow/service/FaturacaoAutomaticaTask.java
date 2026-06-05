package pt.studioflow.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.studioflow.model.Aluno;
import pt.studioflow.repository.AlunoRepository;
import java.util.List;

@Component
public class FaturacaoAutomaticaTask {

    private final AlunoRepository alunoRepository;
    private final VendusApiService vendusService;

    public FaturacaoAutomaticaTask(AlunoRepository alunoRepository, VendusApiService vendusService) {
        this.alunoRepository = alunoRepository;
        this.vendusService = vendusService;
    }

    // Cron: "0 0 8 1 * *" significa: Segundo 0, Minuto 0, Hora 8, Dia 5, Todos os meses
    @Scheduled(cron = "0 0 8 1 * *")
    public void processarFaturacaoMensal() {
       /*  List<Aluno> alunosAtivos = alunoRepository.findByAtivo(true);
        
        for (Aluno aluno : alunosAtivos) {
            try {
                // Aqui chamarias um método no VendusApiService para criar o documento
                // vendusService.criarFatura(aluno);
                System.out.println("Fatura gerada para: " + aluno.getNomeCompleto());
            } catch (Exception e) {
                System.err.println("Erro ao faturar aluno " + aluno.getId() + ": " + e.getMessage());
            }
        }

        */
    }
}