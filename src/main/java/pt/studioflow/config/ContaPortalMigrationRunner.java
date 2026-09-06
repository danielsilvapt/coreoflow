package pt.studioflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.ContaPortal;
import pt.studioflow.model.User;
import pt.studioflow.repository.ContaPortalRepository;
import pt.studioflow.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Migração única: os antigos utilizadores com role {@code ALUNO} na tabela
 * {@code users} passam a ser {@link ContaPortal} (login por email, fora da
 * tabela de utilizadores). A password é copiada tal como está, por isso quem
 * já tinha acesso continua a entrar com a mesma. Idempotente.
 */
@Component
@Order(50)
public class ContaPortalMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContaPortalMigrationRunner.class);

    private final UserRepository userRepository;
    private final ContaPortalRepository contaPortalRepository;

    public ContaPortalMigrationRunner(UserRepository userRepository, ContaPortalRepository contaPortalRepository) {
        this.userRepository = userRepository;
        this.contaPortalRepository = contaPortalRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> alunos = userRepository.findAll().stream()
                .filter(u -> "ALUNO".equalsIgnoreCase(u.getRole()))
                .toList();
        if (alunos.isEmpty()) return;

        int migrados = 0;
        int semStudio = 0;
        for (User u : alunos) {
            if (u.getStudio() == null) {
                semStudio++;
                continue;
            }
            String email = u.getEmail() != null ? u.getEmail().trim().toLowerCase() : null;
            if (email == null || email.isBlank()) {
                semStudio++;
                continue;
            }
            if (contaPortalRepository.findByEmailIgnoreCase(email).isEmpty()) {
                ContaPortal conta = new ContaPortal();
                conta.setEmail(email);
                conta.setNome(u.getFirstName());
                conta.setPasswordHash(u.getPassword());
                conta.setAtivo(true);
                conta.setStudio(u.getStudio());
                conta.setCriadoEm(LocalDateTime.now());
                contaPortalRepository.save(conta);
                migrados++;
            }
            userRepository.delete(u);
        }
        log.info(">>> Migração portal: {} conta(s) criada(s), {} User(s) ALUNO removido(s), {} ignorado(s) (sem estúdio/email).",
                migrados, alunos.size() - semStudio, semStudio);
    }
}
