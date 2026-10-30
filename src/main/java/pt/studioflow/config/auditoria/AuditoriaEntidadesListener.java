package pt.studioflow.config.auditoria;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Hibernate;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.PostCommitDeleteEventListener;
import org.hibernate.event.spi.PostCommitInsertEventListener;
import org.hibernate.event.spi.PostCommitUpdateEventListener;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.Type;
import org.springframework.stereotype.Component;

import pt.studioflow.model.LogEntry;
import pt.studioflow.model.RegistoAuditoria;
import pt.studioflow.model.RegistoAuditoria.Tipo;
import pt.studioflow.model.Studio;
import pt.studioflow.service.AuditoriaService;
import pt.studioflow.util.DataUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Regista na auditoria todas as criações, alterações e remoções de entidades JPA.
 * Usa os eventos "post-commit" do Hibernate: só dispara depois de a transação ser
 * confirmada (um rollback não deixa rasto) e corre na thread do pedido, por isso o
 * utilizador autenticado ainda está no SecurityContext. Operações em bloco (JPQL
 * UPDATE/DELETE) não passam por aqui.
 */
@Component
public class AuditoriaEntidadesListener implements PostCommitInsertEventListener,
        PostCommitUpdateEventListener, PostCommitDeleteEventListener {

    /** Entidades que não se auditam (as próprias tabelas de log/auditoria). */
    private static final Set<Class<?>> IGNORADAS = Set.of(RegistoAuditoria.class, LogEntry.class);

    /** Propriedades cujo valor nunca é escrito na auditoria. */
    private static final List<String> SENSIVEIS = List.of("password", "senha", "token", "secret", "apikey", "hash");

    /** Propriedades usadas, por esta ordem, como nome legível da entidade. */
    private static final List<String> PROPRIEDADES_NOME = List.of("nome", "titulo", "username", "assunto", "email", "descricao");

    /** Associações cujo nome serve de etiqueta quando a entidade não tem nome próprio (ex.: Mensalidade → aluno). */
    private static final List<String> ASSOCIACOES_NOME = List.of("aluno", "professor", "turma");

    private final EntityManagerFactory entityManagerFactory;
    private final AuditoriaService auditoriaService;

    public AuditoriaEntidadesListener(EntityManagerFactory entityManagerFactory, AuditoriaService auditoriaService) {
        this.entityManagerFactory = entityManagerFactory;
        this.auditoriaService = auditoriaService;
    }

    @PostConstruct
    void registar() {
        EventListenerRegistry registry = entityManagerFactory.unwrap(SessionFactoryImplementor.class)
                .getServiceRegistry().getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_COMMIT_INSERT, this);
        registry.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        registry.appendListeners(EventType.POST_COMMIT_DELETE, this);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return !IGNORADAS.contains(persister.getMappedClass());
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        try {
            if (ignorar(event.getEntity())) {
                return;
            }
            EntityPersister p = event.getPersister();
            auditoriaService.registarDados(Tipo.CRIACAO, nomeEntidade(p), String.valueOf(event.getId()),
                    "Criou " + etiqueta(p, event.getState(), event.getId()),
                    studioId(event.getEntity(), p, event.getState(), event.getId()));
        } catch (Throwable ignore) {
            // auditoria nunca parte a ação do utilizador
        }
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        try {
            if (ignorar(event.getEntity()) || event.getDirtyProperties() == null) {
                return;
            }
            EntityPersister p = event.getPersister();
            String[] nomes = p.getPropertyNames();
            Type[] tipos = p.getPropertyTypes();
            Object[] antes = event.getOldState();
            Object[] depois = event.getState();

            List<String> mudancas = new ArrayList<>();
            for (int i : event.getDirtyProperties()) {
                if (tipos[i].isCollectionType()) {
                    continue;
                }
                if (sensivel(nomes[i])) {
                    mudancas.add(nomes[i] + " (alterado)");
                } else if (antes == null) {
                    mudancas.add(nomes[i] + " → " + valor(depois[i], tipos[i]));
                } else {
                    mudancas.add(nomes[i] + ": " + valor(antes[i], tipos[i]) + " → " + valor(depois[i], tipos[i]));
                }
            }
            if (mudancas.isEmpty()) {
                return;
            }
            auditoriaService.registarDados(Tipo.ALTERACAO, nomeEntidade(p), String.valueOf(event.getId()),
                    "Alterou " + etiqueta(p, depois, event.getId()) + " — " + String.join("; ", mudancas),
                    studioId(event.getEntity(), p, depois, event.getId()));
        } catch (Throwable ignore) {
            // auditoria nunca parte a ação do utilizador
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        try {
            if (ignorar(event.getEntity())) {
                return;
            }
            EntityPersister p = event.getPersister();
            auditoriaService.registarDados(Tipo.REMOCAO, nomeEntidade(p), String.valueOf(event.getId()),
                    "Removeu " + etiqueta(p, event.getDeletedState(), event.getId()),
                    studioId(event.getEntity(), p, event.getDeletedState(), event.getId()));
        } catch (Throwable ignore) {
            // auditoria nunca parte a ação do utilizador
        }
    }

    @Override
    public void onPostInsertCommitFailed(PostInsertEvent event) {
        // rollback: nada a registar
    }

    @Override
    public void onPostUpdateCommitFailed(PostUpdateEvent event) {
        // rollback: nada a registar
    }

    @Override
    public void onPostDeleteCommitFailed(PostDeleteEvent event) {
        // rollback: nada a registar
    }

    // ---------------------------------------------------------------- formatação

    private static boolean ignorar(Object entity) {
        return entity == null || IGNORADAS.contains(Hibernate.getClass(entity));
    }

    private static String nomeEntidade(EntityPersister p) {
        String n = p.getEntityName();
        int i = n.lastIndexOf('.');
        return i >= 0 ? n.substring(i + 1) : n;
    }

    /** Ex.: "Aluno «Maria Silva» (#12)" ou "Presenca #345". */
    private static String etiqueta(EntityPersister p, Object[] state, Object id) {
        String nome = nomeLegivel(p, state);
        return nomeEntidade(p) + (nome != null ? " «" + nome + "» (#" + id + ")" : " #" + id);
    }

    private static String nomeLegivel(EntityPersister p, Object[] state) {
        if (state == null) {
            return null;
        }
        String[] nomes = p.getPropertyNames();
        for (String alvo : PROPRIEDADES_NOME) {
            for (int i = 0; i < nomes.length; i++) {
                if (nomes[i].equals(alvo) && state[i] instanceof String s && !s.isBlank()) {
                    return truncar(s, 80);
                }
            }
        }
        for (String alvo : ASSOCIACOES_NOME) {
            for (int i = 0; i < nomes.length; i++) {
                if (nomes[i].equals(alvo) && state[i] != null && Hibernate.isInitialized(state[i])) {
                    Object nome = invocar(state[i], "getNome");
                    if (nome instanceof String s && !s.isBlank()) {
                        return truncar(s, 80);
                    }
                }
            }
        }
        return null;
    }

    /** Estúdio dono da entidade: a própria (se for um Studio) ou a propriedade "studio". */
    private static Long studioId(Object entity, EntityPersister p, Object[] state, Object id) {
        if (entity instanceof Studio && id instanceof Long l) {
            return l;
        }
        if (state == null) {
            return null;
        }
        String[] nomes = p.getPropertyNames();
        for (int i = 0; i < nomes.length; i++) {
            if ("studio".equals(nomes[i]) && state[i] != null && idDe(state[i]) instanceof Long l) {
                return l;
            }
        }
        return null;
    }

    private static String valor(Object v, Type tipo) {
        if (v == null) {
            return "—";
        }
        if (tipo.isEntityType()) {
            Object id = idDe(v);
            return id != null ? "#" + id : "?";
        }
        if (v instanceof Boolean b) {
            return b ? "sim" : "não";
        }
        if (v instanceof LocalDate d) {
            return DataUtil.formatar(d);
        }
        if (v instanceof LocalDateTime d) {
            return DataUtil.formatar(d);
        }
        if (v instanceof byte[]) {
            return "(binário)";
        }
        String s = String.valueOf(v).replaceAll("\\s+", " ").trim();
        return "\"" + truncar(s, 80) + "\"";
    }

    private static boolean sensivel(String propriedade) {
        String p = propriedade.toLowerCase(Locale.ROOT);
        return SENSIVEIS.stream().anyMatch(p::contains);
    }

    private static Object idDe(Object v) {
        if (v instanceof HibernateProxy proxy) {
            return proxy.getHibernateLazyInitializer().getIdentifier();
        }
        return invocar(v, "getId");
    }

    private static Object invocar(Object alvo, String metodo) {
        try {
            return alvo.getClass().getMethod(metodo).invoke(alvo);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
