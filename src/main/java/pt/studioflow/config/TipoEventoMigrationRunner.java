package pt.studioflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Convite;
import pt.studioflow.model.TipoEvento;
import pt.studioflow.repository.ConviteRepository;

import java.util.List;

/**
 * Migração única: preenche {@link Convite#getTipo()} nos convites antigos, criados
 * antes de existir o campo. Mantém o comportamento anterior (deteção de "Competição"
 * no nome do evento) como ponto de partida — depois disto o tipo passa a ser escolhido
 * na combo, não inferido do nome. Idempotente (só mexe em convites com tipo nulo).
 */
@Component
@Order(51)
public class TipoEventoMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TipoEventoMigrationRunner.class);

    private final ConviteRepository conviteRepository;

    public TipoEventoMigrationRunner(ConviteRepository conviteRepository) {
        this.conviteRepository = conviteRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Convite> semTipo = conviteRepository.findAll().stream()
                .filter(c -> c.getTipo() == null)
                .toList();
        if (semTipo.isEmpty()) return;

        int competicoes = 0;
        for (Convite c : semTipo) {
            boolean eraCompeticao = c.getEvento() != null && c.getEvento().contains("Competição");
            c.setTipo(eraCompeticao ? TipoEvento.COMPETICAO : TipoEvento.OUTRO);
            if (eraCompeticao) competicoes++;
        }
        conviteRepository.saveAll(semTipo);
        log.info(">>> Migração tipo evento: {} convite(s) atualizado(s), {} marcado(s) como Competição.",
                semTipo.size(), competicoes);
    }
}
