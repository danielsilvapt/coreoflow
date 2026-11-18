package pt.studioflow.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Presenca;

/**
 * Critério único de "aluno em risco de abandono", partilhado pelo Dashboard,
 * Notificações e CRM.
 *
 * Um aluno está em risco quando está de facto a frequentar o estúdio e deixou de
 * aparecer:
 * <ul>
 * <li>flag {@code ativo} ligada <b>e</b> estado não INATIVO / PENDENTE /
 * EXPERIMENTAL (a flag sozinha não chega: há ex-alunos com estado INATIVO que
 * ficaram com a flag ligada);</li>
 * <li>pelo menos uma matrícula confirmada (não pendente) numa turma ativa - quem
 * já não está em nenhuma turma é ex-aluno, não "ativo sem presenças";</li>
 * <li>sem nenhuma presença efetiva ({@code presente = true}) nos últimos N dias
 * - registos de falta não contam como presença;</li>
 * <li>alunos inscritos há menos de N dias que ainda não tiveram aula não
 * contam.</li>
 * </ul>
 */
public final class AlunosEmRisco {

    public static final int DIAS_SEM_PRESENCA = 15;

    private AlunosEmRisco() {
    }

    /** Última presença efetiva (presente = true) por id de aluno. */
    public static Map<Long, LocalDate> ultimaPresencaEfetiva(Collection<Presenca> presencas) {
        Map<Long, LocalDate> ultima = new HashMap<>();
        for (Presenca p : presencas) {
            if (p != null && p.isPresente() && p.getAluno() != null && p.getAluno().getId() != null
                    && p.getData() != null) {
                ultima.merge(p.getAluno().getId(), p.getData(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        return ultima;
    }

    /** Ids dos alunos com pelo menos uma matrícula confirmada numa turma ativa. */
    public static Set<Long> idsComTurmaConfirmada(Collection<AlunoTurma> matriculas) {
        Set<Long> ids = new HashSet<>();
        for (AlunoTurma at : matriculas) {
            if (at != null && !at.isPendente() && at.getAluno() != null && at.getTurma() != null
                    && at.getTurma().isAtivo()) {
                ids.add(at.getAluno().getId());
            }
        }
        return ids;
    }

    /** O aluno está a frequentar o estúdio (independentemente de ter vindo às aulas). */
    public static boolean frequentaEstudio(Aluno a, Set<Long> idsComTurmaConfirmada) {
        if (a == null || !a.isAtivo() || !idsComTurmaConfirmada.contains(a.getId())) {
            return false;
        }
        Aluno.AlunoStatus s = a.getStatus();
        return s != Aluno.AlunoStatus.INATIVO
                && s != Aluno.AlunoStatus.PENDENTE
                && s != Aluno.AlunoStatus.EXPERIMENTAL;
    }

    public static List<Aluno> calcular(Collection<Aluno> alunos, Collection<AlunoTurma> matriculas,
            Collection<Presenca> presencas, LocalDate hoje, int diasSemPresenca) {
        Set<Long> idsComTurma = idsComTurmaConfirmada(matriculas);
        Map<Long, LocalDate> ultima = ultimaPresencaEfetiva(presencas);
        LocalDate limite = hoje.minusDays(diasSemPresenca);

        return alunos.stream()
                .filter(a -> frequentaEstudio(a, idsComTurma))
                .filter(a -> {
                    LocalDate u = ultima.get(a.getId());
                    if (u != null) {
                        return u.isBefore(limite);
                    }
                    // Nunca veio: só é risco se já está inscrito há tempo suficiente.
                    LocalDate inscricao = a.getCarimboDataHora();
                    return inscricao == null || inscricao.isBefore(limite);
                })
                .distinct()
                .sorted(Comparator.comparing(
                        (Aluno a) -> a.getNomeCompleto() == null ? "" : a.getNomeCompleto().toLowerCase()))
                .toList();
    }
}
