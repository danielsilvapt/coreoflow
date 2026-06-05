package pt.studioflow.controller;

import java.util.Optional;
import org.springframework.web.bind.annotation.*;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;
import pt.studioflow.service.EmailService;

@RestController
@RequestMapping("/api/convocatoria")
public class ConvocatoriaController {

    private final InscricaoEventoRepository inscricaoRepository;
    private final AlunoRepository alunoRepository;
    private final ConviteRepository conviteRepository;
    private final EmailService emailService;
    private final AlunoTurmaRepository alunoTurmaRepository; // Necessário para achar a turma

    public ConvocatoriaController(InscricaoEventoRepository inscricaoRepository,
            AlunoRepository alunoRepository,
            ConviteRepository conviteRepository,
            EmailService emailService,
            AlunoTurmaRepository alunoTurmaRepository) {
        this.inscricaoRepository = inscricaoRepository;
        this.alunoRepository = alunoRepository;
        this.conviteRepository = conviteRepository;
        this.emailService = emailService;
        this.alunoTurmaRepository = alunoTurmaRepository;
    }

    @GetMapping("/confirmar")
    public String confirmar(@RequestParam Long alunoId, @RequestParam Long conviteId) {
        return processarResposta(alunoId, conviteId, true);
    }

    @GetMapping("/recusar")
    public String recusar(@RequestParam Long alunoId, @RequestParam Long conviteId) {
        return processarResposta(alunoId, conviteId, false);
    }

    private String processarResposta(Long alunoId, Long conviteId, boolean confirmou) {
        Optional<InscricaoEvento> optInscricao = inscricaoRepository.findByAlunoIdAndConviteId(alunoId, conviteId);

        if (optInscricao.isPresent()) {
            InscricaoEvento inscricao = optInscricao.get();

            // Só envia email se o estado estiver a mudar para confirmado agora
            boolean jaEstavaConfirmado = inscricao.isConfirmado();

            inscricao.setConfirmado(confirmou);
            inscricaoRepository.save(inscricao);

            // Se o aluno confirmou (e não era uma confirmação duplicada), notificamos o
            // professor
            if (confirmou && !jaEstavaConfirmado) {
                notificarProfessor(inscricao.getAluno(), inscricao.getConvite());
            }

            String status = confirmou ? "confirmada" : "recusada";
            return "<html><body style='font-family:sans-serif; text-align:center; padding:50px;'>" +
                    "<h1>Obrigado!</h1>" +
                    "<p>A tua presença foi " + status + " com sucesso.</p>" +
                    "</body></html>";
        }
        return "Erro: Convocatória não encontrada.";
    }

    private void notificarProfessor(Aluno aluno, Convite convite) {
        // 1. Encontrar a turma do aluno (assumindo que ele está em pelo menos uma)
        alunoTurmaRepository.findByAluno(aluno).stream()
                .findFirst() // Pega a primeira turma encontrada
                .ifPresent(alunoTurma -> {
                    Turma turma = alunoTurma.getTurma();
                    Professor professor = turma.getProfessor();

                    if (professor != null && professor.getEmail() != null) {
                        emailService.notificarProfessorConfirmacao(aluno, convite, professor);
                    }
                });
    }
}