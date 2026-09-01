package pt.studioflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import pt.studioflow.model.Lead;
import pt.studioflow.model.User;
import pt.studioflow.repository.LeadRepository;
import pt.studioflow.repository.UserRepository;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LeadRepository leadRepository;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            LeadRepository leadRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.leadRepository = leadRepository;
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

        seedLeadsIniciais();
    }

    /**
     * Semeia os primeiros leads comerciais (apenas na primeira vez, se a
     * tabela estiver vazia) para que a pipeline de vendas já apareça
     * preenchida em /admin/leads.
     */
    private void seedLeadsIniciais() {
        if (leadRepository.count() > 0) return;

        Lead risa = new Lead();
        risa.setNome("RiSa by ADCR");
        risa.setTipo(Lead.TipoLead.ESCOLA_DANCA);
        risa.setNomeContacto("Sandra Silva");
        risa.setEstado(Lead.EstadoLead.NOVO);
        risa.setOrigem("Prospecção direta");
        risa.setProximoPasso("Marcar demo");
        leadRepository.save(risa);

        Lead ritmus = new Lead();
        ritmus.setNome("Ritmus - Academia de Dança");
        ritmus.setTipo(Lead.TipoLead.ESCOLA_DANCA);
        ritmus.setNomeContacto("João Martins");
        ritmus.setEstado(Lead.EstadoLead.NOVO);
        ritmus.setOrigem("Prospecção direta");
        ritmus.setProximoPasso("Marcar demo");
        leadRepository.save(ritmus);

        log.info(">>> Leads iniciais semeados: RiSa by ADCR, Ritmus - Academia de Dança");
    }
}
