package pt.studioflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import pt.studioflow.model.User;
import pt.studioflow.repository.UserRepository;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        userRepository.findSuperAdminByUsername("superadmin").ifPresentOrElse(
            existing -> {
                // Garante que o hash está sempre actualizado com a password definida aqui
                String encoded = passwordEncoder.encode("coreoflow2026");
                existing.setPassword(encoded);
                userRepository.save(existing);
                log.info(">>> SUPERADMIN: password reinicializada. Login: superadmin / coreoflow2026");
            },
            () -> {
                User superAdmin = new User();
                superAdmin.setUsername("superadmin");
                superAdmin.setPassword(passwordEncoder.encode("coreoflow2026"));
                superAdmin.setRole("SUPERADMIN");
                superAdmin.setFirstName("Super");
                superAdmin.setEmail("geral@coreoflow.me");
                superAdmin.setStudio(null);
                userRepository.save(superAdmin);
                log.info(">>> SUPERADMIN criado. Login: superadmin / coreoflow2026");
            }
        );
    }
}
