package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.ContaPortal;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.ContaPortalRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponto único para criar acessos ao portal do aluno/encarregado e gerar o
 * link onde o destinatário define a palavra-passe ({@code /portal-ativar?token=…}).
 *
 * <p>Usado na validação de inscrições, na compra de aulas avulsas, na vista
 * "Acessos ao Portal" e no "Esqueci-me da palavra-passe" do login.</p>
 */
@Service
public class ContaPortalService {

    private final ContaPortalRepository contaRepo;
    private final AlunoRepository alunoRepo;
    private final EmailService emailService;
    private final String baseUrl;

    public ContaPortalService(ContaPortalRepository contaRepo, AlunoRepository alunoRepo,
            EmailService emailService, @Value("${app.base-url:https://app.coreoflow.me}") String baseUrl) {
        this.contaRepo = contaRepo;
        this.alunoRepo = alunoRepo;
        this.emailService = emailService;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Garante que o email tem conta no portal e devolve o link de ativação.
     * Se a conta já estiver ativa (já definiu palavra-passe) devolve vazio — não
     * mexe nela, nem no estúdio a que pertence.
     */
    @Transactional
    public Optional<String> prepararAtivacao(String email, String nome, Studio studio, int diasValidade) {
        if (email == null || email.isBlank() || studio == null) return Optional.empty();
        String normalizado = email.trim().toLowerCase();
        ContaPortal conta = contaRepo.findByEmailIgnoreCase(normalizado).orElseGet(ContaPortal::new);
        if (conta.isAtivo()) return Optional.empty();
        conta.setEmail(normalizado);
        if (nome != null && !nome.isBlank()) conta.setNome(nome);
        conta.setStudio(studio);
        // Irmãos com o mesmo email: reaproveita o link ainda válido para não invalidar o 1º email.
        if (conta.getId() != null && conta.tokenValido(conta.getTokenAtivacao())
                && conta.getTokenExpiraEm().isAfter(LocalDateTime.now().plusDays(1))) {
            contaRepo.save(conta);
            return Optional.of(baseUrl + "/portal-ativar?token=" + conta.getTokenAtivacao());
        }
        return Optional.of(gerarToken(conta, diasValidade));
    }

    /** Cria (se preciso) a conta e envia o convite por email. Não faz nada se já estiver ativa. */
    @Transactional
    public boolean convidarSeNecessario(String email, String nome, Studio studio, int diasValidade) {
        Optional<String> link = prepararAtivacao(email, nome, studio, diasValidade);
        link.ifPresent(l -> emailService.enviarConvitePortal(email.trim().toLowerCase(), studio, l));
        return link.isPresent();
    }

    /**
     * Gera novo token para uma conta existente (convite ou reposição de password),
     * envia o email e devolve a conta. Numa reposição a conta mantém-se ativa e a
     * password antiga continua a funcionar até ser definida a nova.
     */
    @Transactional
    public ContaPortal enviarConvite(String email, String nome, Studio studio, int diasValidade) {
        String normalizado = email.trim().toLowerCase();
        ContaPortal conta = contaRepo.findByEmailIgnoreCase(normalizado).orElseGet(ContaPortal::new);
        conta.setEmail(normalizado);
        if (nome != null && !nome.isBlank()) conta.setNome(nome);
        conta.setStudio(studio);
        String link = gerarToken(conta, diasValidade);
        emailService.enviarConvitePortal(conta.getEmail(), conta.getStudio(), link);
        return conta;
    }

    /**
     * "Primeiro acesso / Esqueci-me da palavra-passe" no login. Envia o link se
     * o email tiver conta no portal ou estiver na ficha de algum aluno ativo.
     * Silencioso em todos os casos — quem chama mostra sempre a mesma mensagem,
     * para não revelar que emails estão registados.
     */
    @Transactional
    public void pedirAcesso(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) return;
        String normalizado = email.trim().toLowerCase();

        Optional<ContaPortal> existente = contaRepo.findByEmailIgnoreCase(normalizado);
        if (existente.isPresent()) {
            ContaPortal conta = existente.get();
            enviarConvite(normalizado, conta.getNome(), conta.getStudio(), 2);
            return;
        }

        alunoRepo.findAllByEmailIgnoreCase(normalizado).stream()
                .filter(a -> a.getStudio() != null && a.getStudio().isAtivo())
                .filter(a -> a.getStatus() != Aluno.AlunoStatus.INATIVO)
                .max(Comparator.comparing(Aluno::getId))
                .ifPresent(a -> enviarConvite(normalizado, a.getNomeCompleto(), a.getStudio(), 2));
    }

    private String gerarToken(ContaPortal conta, int diasValidade) {
        conta.setTokenAtivacao(UUID.randomUUID().toString().replace("-", ""));
        conta.setTokenExpiraEm(LocalDateTime.now().plusDays(diasValidade));
        contaRepo.save(conta);
        return baseUrl + "/portal-ativar?token=" + conta.getTokenAtivacao();
    }
}
