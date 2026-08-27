package pt.studioflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fluxo público de renovação de matrícula: um educando existente (ATIVO ou
 * INATIVO) pede para continuar/regressar num novo período, ficando PENDENTE
 * até ser validado em ValidacaoInscricoesView (mesmo fluxo de aprovação das
 * novas inscrições).
 */
@Service
public class RenovacaoService {

    private final AlunoRepository alunoRepository;
    private final EmailService emailService;

    public RenovacaoService(AlunoRepository alunoRepository, EmailService emailService) {
        this.alunoRepository = alunoRepository;
        this.emailService = emailService;
    }

    /** Procura educandos associados a um email, dentro do estúdio atual. */
    public List<Aluno> procurarPorEmail(String email, Studio studio) {
        if (email == null || email.isBlank() || studio == null) return List.of();
        return alunoRepository.findByEmailAndStudioWithTurmas(email.trim(), studio);
    }

    /** ATIVO pode renovar o ano letivo; INATIVO pode voltar (readmissão). Já com pedido em curso não é elegível. */
    public boolean elegivel(Aluno aluno) {
        return aluno.getStatus() == AlunoStatus.ATIVO || aluno.getStatus() == AlunoStatus.INATIVO;
    }

    /**
     * Submete o pedido de renovação. Devolve false sem alterar nada se o aluno já
     * tiver um pedido PENDENTE em curso (evita duplicar pedidos).
     */
    @Transactional
    public boolean submeterRenovacao(Aluno aluno, Map<Turma, Integer> turmasEFrequencia, String nomeStudio) {
        if (!elegivel(aluno) || turmasEFrequencia.isEmpty()) return false;

        String interesses = turmasEFrequencia.entrySet().stream()
                .map(entry -> {
                    Turma t = entry.getKey();
                    String freq = entry.getValue() == 1 ? "1 vez / semana" : "2 vezes / semana";
                    return t.getModalidade().getDescricao() + " " + t.getDescricao() + " (" + freq + ")";
                })
                .collect(Collectors.joining(", "));

        String moradaAtual = aluno.getMorada() != null ? aluno.getMorada() : "";
        String moradaLimpa = moradaAtual.contains("[") ? moradaAtual.substring(0, moradaAtual.indexOf("[")).trim() : moradaAtual;
        aluno.setMorada(moradaLimpa + " [Interesses: " + interesses + "]");

        aluno.setStatus(AlunoStatus.PENDENTE);
        aluno.setPedidoRenovacao(true);
        aluno.setDataInscricaoRenovacao(LocalDate.now());
        alunoRepository.save(aluno);

        emailService.enviarEmailNotificacaoRenovacao(aluno);
        emailService.enviarEmailConfirmacaoCandidato(aluno, nomeStudio);
        return true;
    }
}
