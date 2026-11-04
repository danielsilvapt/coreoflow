package pt.studioflow.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.RegistoAuditoria;

/**
 * Eliminação definitiva de um aluno <b>já inativo</b> e de todos os seus registos:
 * turmas, presenças, avaliações, contratos, mensalidades, pagamentos online,
 * compras de crédito, encomendas da loja, eventos, lista de espera, marcações de
 * sala, referências, inquéritos, subsídios, aulas experimentais, foto no R2 e a
 * conta do portal (se nenhum outro aluno usar o mesmo email).
 *
 * <p>Decisão do produto: elimina também o histórico financeiro. As faturas já
 * emitidas no programa de faturação (TOC Online/Vendus) não são tocadas.</p>
 */
@Service
public class AlunoRemocaoService {

    @PersistenceContext
    private EntityManager em;

    private final R2StorageService r2StorageService;
    private final AuditoriaService auditoriaService;

    public AlunoRemocaoService(R2StorageService r2StorageService, AuditoriaService auditoriaService) {
        this.r2StorageService = r2StorageService;
        this.auditoriaService = auditoriaService;
    }

    @Transactional
    public void eliminarDefinitivamente(Long alunoId) {
        Aluno aluno = em.find(Aluno.class, alunoId);
        if (aluno == null) return;
        if (aluno.getStatus() != Aluno.AlunoStatus.INATIVO) {
            throw new IllegalStateException("Só é possível eliminar alunos que já estejam inativos.");
        }

        String nome = aluno.getNomeCompleto();
        String email = aluno.getEmail();
        String fotoChave = aluno.getFotoChave();
        Long studioId = aluno.getStudio() != null ? aluno.getStudio().getId() : null;

        // A partir daqui só bulk deletes — tira o aluno do contexto para o cascade
        // das coleções (turmas/presenças) não interferir.
        em.flush();
        em.clear();

        // Presenças de outros alunos que tenham consumido créditos deste (não deve
        // acontecer, mas a FK impediria a eliminação).
        exec("UPDATE Presenca p SET p.compraCredito = NULL WHERE p.aluno.id <> :id AND p.compraCredito.id IN "
                + "(SELECT c.id FROM CompraCredito c WHERE c.aluno.id = :id)", alunoId);
        exec("DELETE FROM Presenca p WHERE p.aluno.id = :id", alunoId);
        exec("DELETE FROM AlunoTurma x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM AvaliacaoAluno x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM ContratoDigital x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM EncomendaLoja x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM InscricaoEvento x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM ListaEspera x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM PagamentoOnline x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM Mensalidade x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM CompraCredito x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM Referencia x WHERE x.referenciador.id = :id OR x.referenciado.id = :id", alunoId);
        exec("DELETE FROM RespostaInquerito x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM SubsidioAluno x WHERE x.aluno.id = :id", alunoId);
        exec("DELETE FROM AulaExperimental x WHERE x.aluno.id = :id", alunoId);
        em.createNativeQuery("DELETE FROM marcacao_alunos WHERE aluno_id = :id")
                .setParameter("id", alunoId).executeUpdate();

        exec("DELETE FROM Aluno a WHERE a.id = :id", alunoId);

        // Conta do portal: só se mais nenhum aluno (de qualquer estúdio) usar este email.
        if (email != null && !email.isBlank()) {
            Long restantes = em.createQuery(
                    "SELECT COUNT(a) FROM Aluno a WHERE LOWER(a.email) = :email", Long.class)
                    .setParameter("email", email.trim().toLowerCase()).getSingleResult();
            if (restantes == 0) {
                em.createQuery("DELETE FROM ContaPortal c WHERE LOWER(c.email) = :email")
                        .setParameter("email", email.trim().toLowerCase()).executeUpdate();
            }
        }

        auditoriaService.registarDados(RegistoAuditoria.Tipo.REMOCAO, "Aluno", String.valueOf(alunoId),
                "Aluno eliminado definitivamente com todos os registos: " + nome, studioId);

        // Foto no R2 só depois do commit — se a transação falhar, a foto continua lá.
        if (fotoChave != null && !fotoChave.isBlank()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        r2StorageService.apagar(fotoChave);
                    } catch (Exception e) {
                        System.err.println("Não foi possível apagar a foto " + fotoChave + " do R2: " + e.getMessage());
                    }
                }
            });
        }
    }

    private void exec(String jpql, Long alunoId) {
        em.createQuery(jpql).setParameter("id", alunoId).executeUpdate();
    }
}
