package pt.studioflow.service;

import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.Aula;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Professor;
import pt.studioflow.model.RegistoHoras;
import pt.studioflow.model.Studio;
import pt.studioflow.model.TipoRemuneracao;
import pt.studioflow.model.Turma;
import pt.studioflow.util.TextoUtil;

/**
 * Fonte única de verdade para o cálculo do que se paga a um professor e da
 * rentabilidade de uma turma, seja o estúdio pago à hora ou por percentagem da
 * mensalidade. Toda a lógica é pura (recebe listas já carregadas) para ser
 * testável sem contexto Spring, à semelhança de {@link DashboardService}.
 *
 * <p>Herança de configuração: o {@link Studio} guarda os valores por defeito;
 * cada {@link Professor} pode sobrepor qualquer um deles. Campo a null no
 * professor ⇒ herda do estúdio.
 */
@Service
public class RemuneracaoService {

    private final MensalidadeConfig mensalidadeConfig;

    public RemuneracaoService(MensalidadeConfig mensalidadeConfig) {
        this.mensalidadeConfig = mensalidadeConfig;
    }

    /** Dados já carregados pelo chamador, usados nos cálculos de um mês. */
    public static class Dados {
        public List<RegistoHoras> registos = List.of();
        public List<Mensalidade> mensalidades = List.of();
        public List<AlunoTurma> inscricoes = List.of();
        public List<Aula> aulas = List.of();

        public Dados registos(List<RegistoHoras> v) { this.registos = v; return this; }
        public Dados mensalidades(List<Mensalidade> v) { this.mensalidades = v; return this; }
        public Dados inscricoes(List<AlunoTurma> v) { this.inscricoes = v; return this; }
        public Dados aulas(List<Aula> v) { this.aulas = v; return this; }
    }

    /** Linha de pagamento de um professor num mês. */
    public record LinhaPagamento(Professor professor, String nome, TipoRemuneracao modo,
                                 double base, double ensaios, double privadas,
                                 double total, boolean previsto) {}

    // =====================================================
    // RESOLUÇÃO DA CONFIGURAÇÃO EFETIVA (professor → estúdio)
    // =====================================================

    public TipoRemuneracao tipoEfetivo(Professor p, Studio s) {
        if (p != null && p.getTipoRemuneracao() != null && !p.getTipoRemuneracao().isBlank()) {
            return TipoRemuneracao.from(p.getTipoRemuneracao());
        }
        return TipoRemuneracao.from(s != null ? s.getTipoRemuneracaoProf() : null);
    }

    public double valorHoraRegular(Professor p, Studio s) {
        if (p != null) return p.getValorHoraAula();
        if (s != null && s.getValorRemuneracaoProf() != null) return s.getValorRemuneracaoProf();
        return 0.0;
    }

    public double valorHoraEnsaio(Professor p, Studio s) {
        if (p != null && p.getValorHoraEnsaio() != null) return p.getValorHoraEnsaio();
        if (s != null && s.getValorHoraEnsaioProf() != null) return s.getValorHoraEnsaioProf();
        return valorHoraRegular(p, s);
    }

    public double valorHoraPrivada(Professor p, Studio s) {
        if (p != null && p.getValorHoraPrivada() != null) return p.getValorHoraPrivada();
        if (s != null && s.getValorHoraPrivadaProf() != null) return s.getValorHoraPrivadaProf();
        return valorHoraRegular(p, s);
    }

    /** Percentagem da mensalidade aplicável a um aluno com {@code aulasPorSemana}. */
    public double percentagem(Professor p, Studio s, int aulasPorSemana) {
        Double v = switch (aulasPorSemana) {
            case 1 -> coalesce(p == null ? null : p.getPerc1x(), s == null ? null : s.getPercProf1x());
            case 2 -> coalesce(p == null ? null : p.getPerc2x(), s == null ? null : s.getPercProf2x());
            case 3 -> coalesce(p == null ? null : p.getPerc3x(), s == null ? null : s.getPercProf3x());
            default -> null;
        };
        if (v == null) v = coalesce(p == null ? null : p.getPercOutras(), s == null ? null : s.getPercProfOutras());
        return v != null ? v : 0.0;
    }

    // =====================================================
    // CUSTO DO PROFESSOR POR TURMA / MÊS
    // =====================================================

    public boolean ehFuturo(YearMonth mes) {
        return mes.isAfter(YearMonth.now());
    }

    /** Receita (mensalidades) da turma no mês — real para meses fechados, estimada para futuros. */
    public double receitaTurma(Turma t, YearMonth mes, Dados d, Studio studio) {
        return ehFuturo(mes) ? receitaEstimadaTurma(t, mes, d) : receitaRealTurma(t, mes, d);
    }

    /** Receita <b>real</b> (mensalidades efetivamente emitidas) da turma no mês. Para meses futuros tende a 0. */
    public double receitaRealTurma(Turma t, YearMonth mes, Dados d) {
        return d.mensalidades.stream()
                .filter(m -> m.getTurma() != null && m.getTurma().getId().equals(t.getId()))
                .filter(m -> mesIgual(m, mes))
                .filter(RemuneracaoService::emitida)
                .mapToDouble(Mensalidade::getValor).sum();
    }

    /**
     * Uma mensalidade "conta" como receita real quando já foi emitida
     * (FATURADO / PAGO / EM_DÍVIDA). As POR_EMITIR são geradas com antecedência
     * até junho e não são ainda receita faturada. (estado a null ⇒ conta, para
     * compatibilidade com dados/testes antigos.)
     */
    private static boolean emitida(Mensalidade m) {
        return m.getEstado() != EstadoMensalidade.POR_EMITIR;
    }

    /**
     * Receita <b>estimada</b> da turma num mês: soma EXATA das mensalidades já
     * definidas para essa turma nesse mês (qualquer estado, incl. POR_EMITIR).
     * É o que se espera faturar — o mesmo valor que a lista de Mensalidades mostra
     * com o filtro dessa turma + mês. Sem projeções nem recálculo por regras (que
     * ignoravam descontos manuais e inflavam a estimativa).
     */
    public double receitaEstimadaTurma(Turma t, YearMonth mes, Dados d) {
        return d.mensalidades.stream()
                .filter(m -> m.getTurma() != null && m.getTurma().getId().equals(t.getId()))
                .filter(m -> mesIgual(m, mes))
                .mapToDouble(m -> Math.max(0.0, m.getValor()))
                .sum();
    }

    /**
     * Custo do professor associado a uma turma no mês.
     * {@code studio} é passado explicitamente (nunca {@code t.getStudio()}) para
     * não depender de uma associação lazy fora de sessão Hibernate.
     */
    public double custoProfessorTurma(Turma t, Studio studio, YearMonth mes, Dados d) {
        return ehFuturo(mes)
                ? custoProfessorEstimadoTurma(t, studio, mes, d)
                : custoProfessorRealTurma(t, studio, mes, d);
    }

    /**
     * Custo <b>real</b> do professor para a turma no mês: percentagem das
     * mensalidades efetivamente emitidas (ou horas registadas no modo HORA), mais
     * os extras pagos à hora (ensaios, privadas, workshops). Para meses futuros
     * tende a 0.
     */
    public double custoProfessorRealTurma(Turma t, Studio studio, YearMonth mes, Dados d) {
        Professor p = t.getProfessor();
        Studio s = studio;
        if (tipoEfetivo(p, s) == TipoRemuneracao.PERCENTAGEM) {
            double regular = d.mensalidades.stream()
                    .filter(m -> m.getTurma() != null && m.getTurma().getId().equals(t.getId()))
                    .filter(m -> mesIgual(m, mes))
                    .filter(RemuneracaoService::emitida)
                    .mapToDouble(m -> m.getValor()
                            * percentagem(p, s, freqAluno(m, d.inscricoes)) / 100.0)
                    .sum();
            // ensaios / privadas / workshops não têm mensalidade → pagos à hora (só reais)
            return regular + horasExtraValorizadas(t, p, s, mes, d, true);
        }
        // HORA
        return horasExtraValorizadas(t, p, s, mes, d, false);
    }

    /**
     * Custo <b>estimado</b> do professor para a turma no mês, calculado sempre a
     * partir do previsto — aulas agendadas × valor/hora no modo HORA, projeção
     * das mensalidades das inscrições ativas × percentagem no modo PERCENTAGEM —
     * independentemente de o mês já ter registos de horas reais. Serve de termo
     * de comparação com o custo real de {@link #custoProfessorTurma}.
     */
    public double custoProfessorEstimadoTurma(Turma t, Studio studio, YearMonth mes, Dados d) {
        Professor p = t.getProfessor();
        Studio s = studio;
        if (tipoEfetivo(p, s) == TipoRemuneracao.PERCENTAGEM) {
            // Percentagem sobre as mensalidades já definidas da turma nesse mês
            // (mesma base da receita estimada), qualquer estado.
            return d.mensalidades.stream()
                    .filter(m -> m.getTurma() != null && m.getTurma().getId().equals(t.getId()))
                    .filter(m -> mesIgual(m, mes))
                    .mapToDouble(m -> Math.max(0.0, m.getValor())
                            * percentagem(p, s, freqAluno(m, d.inscricoes)) / 100.0)
                    .sum();
        }
        return horasAgendadas(t, mes, d.aulas, p) * valorHoraRegular(p, s);
    }

    /**
     * Soma horas × taxa dos registos de horas desta turma/professor no mês.
     * @param apenasNaoRegulares se true, ignora "aula regular" (usado no modo percentagem)
     */
    private double horasExtraValorizadas(Turma t, Professor p, Studio s, YearMonth mes,
                                         Dados d, boolean apenasNaoRegulares) {
        return d.registos.stream()
                .filter(r -> r.getAno() == mes.getYear() && r.getMesNumero() == mes.getMonthValue())
                .filter(r -> p != null && TextoUtil.contemNome(r.getProfessor(), p.getNome()))
                .filter(r -> registoDaTurma(r, t))
                .filter(r -> !apenasNaoRegulares || !ehRegular(r.getTipoAtividade()))
                .mapToDouble(r -> horas(r) * taxaAtividade(r.getTipoAtividade(), p, s))
                .sum();
    }

    // =====================================================
    // AGREGADOS
    // =====================================================

    /** id da turma → [receita, custoProf, saldo]. */
    public Map<Long, double[]> rentabilidadePorTurma(List<Turma> turmas, Studio studio, YearMonth mes, Dados d) {
        Map<Long, double[]> res = new LinkedHashMap<>();
        for (Turma t : turmas) {
            double rec = receitaTurma(t, mes, d, studio);
            double custo = custoProfessorTurma(t, studio, mes, d);
            res.put(t.getId(), new double[] { rec, custo, rec - custo });
        }
        return res;
    }

    /** Índices de {@link #rentabilidadeDetalhadaPorTurma}. */
    public static final int REC_REAL = 0, REC_EST = 1, CUSTO_REAL = 2, CUSTO_EST = 3, SALDO_REAL = 4, SALDO_EST = 5;

    /**
     * id da turma → [receitaReal, receitaEst, custoReal, custoEst, saldoReal, saldoEst].
     * Os três pares real/estimado são calculados sempre, para qualquer mês, de modo
     * a comparar o previsto com o faturado.
     */
    public Map<Long, double[]> rentabilidadeDetalhadaPorTurma(List<Turma> turmas, Studio studio,
                                                              YearMonth mes, Dados d) {
        Map<Long, double[]> res = new LinkedHashMap<>();
        for (Turma t : turmas) {
            double rReal = receitaRealTurma(t, mes, d);
            double rEst = receitaEstimadaTurma(t, mes, d);
            double cReal = custoProfessorRealTurma(t, studio, mes, d);
            double cEst = custoProfessorEstimadoTurma(t, studio, mes, d);
            res.put(t.getId(), new double[] { rReal, rEst, cReal, cEst, rReal - cReal, rEst - cEst });
        }
        return res;
    }

    /** Pagamento devido a cada professor no mês (uma linha por professor com atividade). */
    public List<LinhaPagamento> pagamentosPorProfessor(List<Professor> professores, List<Turma> turmas,
                                                       Studio studio, YearMonth mes, Dados d) {
        boolean futuro = ehFuturo(mes);
        List<LinhaPagamento> linhas = new ArrayList<>();

        for (Professor p : professores) {
            Studio s = studio;
            TipoRemuneracao modo = tipoEfetivo(p, s);
            // Inclui turmas onde o professor é o principal OU dá pelo menos um dia
            // (Aula.professor) de uma turma partilhada com outro professor principal.
            List<Turma> turmasProf = turmas.stream()
                    .filter(t -> (t.getProfessor() != null && t.getProfessor().getId().equals(p.getId()))
                            || (t.getAulas() != null && t.getAulas().stream()
                                    .map(Aula::getProfessorEfetivo)
                                    .anyMatch(ap -> ap != null && ap.getId().equals(p.getId()))))
                    .toList();

            double base = 0, ensaios = 0, privadas = 0;

            if (modo == TipoRemuneracao.PERCENTAGEM) {
                // Percentagem sobre as mensalidades já definidas da turma nesse mês
                // (as mensalidades são geradas com antecedência, por isso serve
                // também para meses futuros).
                for (Turma t : turmasProf) {
                    base += d.mensalidades.stream()
                            .filter(m -> m.getTurma() != null && m.getTurma().getId().equals(t.getId()))
                            .filter(m -> mesIgual(m, mes))
                            .mapToDouble(m -> Math.max(0.0, m.getValor())
                                    * percentagem(p, s, freqAluno(m, d.inscricoes)) / 100.0)
                            .sum();
                }
            }

            // horas registadas do professor no mês (para modo HORA: tudo; para %: só extras)
            for (RegistoHoras r : d.registos) {
                if (r.getAno() != mes.getYear() || r.getMesNumero() != mes.getMonthValue()) continue;
                if (!TextoUtil.contemNome(r.getProfessor(), p.getNome())) continue;
                double v = horas(r) * taxaAtividade(r.getTipoAtividade(), p, s);
                if (ehEnsaio(r.getTipoAtividade())) ensaios += v;
                else if (ehPrivadaOuWorkshop(r.getTipoAtividade())) privadas += v;
                else if (modo == TipoRemuneracao.HORA) base += v;
            }

            // modo HORA + mês futuro: sem registos ainda → estima pelas aulas agendadas
            if (modo == TipoRemuneracao.HORA && futuro && d.registos.stream().noneMatch(
                    r -> r.getAno() == mes.getYear() && r.getMesNumero() == mes.getMonthValue()
                            && TextoUtil.contemNome(r.getProfessor(), p.getNome()))) {
                for (Turma t : turmasProf) {
                    base += horasAgendadas(t, mes, d.aulas, p) * valorHoraRegular(p, s);
                }
            }

            double total = base + ensaios + privadas;
            if (total > 0) {
                linhas.add(new LinhaPagamento(p, p.getNome(), modo, base, ensaios, privadas, total, futuro));
            }
        }
        linhas.sort((a, b) -> Double.compare(b.total(), a.total()));
        return linhas;
    }

    /** Texto curto da remuneração efetiva de um professor (para grelhas). */
    public String descricaoEfetiva(Professor p, Studio s) {
        if (tipoEfetivo(p, s) == TipoRemuneracao.PERCENTAGEM) {
            return String.format("1x %.0f%% · 2x %.0f%% · 3x %.0f%%",
                    percentagem(p, s, 1), percentagem(p, s, 2), percentagem(p, s, 3));
        }
        return String.format("%.0f €/h", valorHoraRegular(p, s));
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private double mensalidadeProjetada(Studio s, AlunoTurma at) {
        if (s == null || at.getAluno() == null) return 0.0;
        return mensalidadeConfig.calcularMensalidade(s, at.getTurma(),
                at.getAluno().isCrianca() ? "crianca" : "adulto",
                at.getAulasPorSemana(), at.getAluno().isSocio());
    }

    private double taxaAtividade(String tipoAtividade, Professor p, Studio s) {
        if (ehEnsaio(tipoAtividade)) return valorHoraEnsaio(p, s);
        if (ehPrivadaOuWorkshop(tipoAtividade)) return valorHoraPrivada(p, s);
        return valorHoraRegular(p, s);
    }

    private static boolean ehEnsaio(String t) {
        return t != null && t.toLowerCase().contains("ensaio");
    }

    private static boolean ehPrivadaOuWorkshop(String t) {
        if (t == null) return false;
        String s = t.toLowerCase();
        return s.contains("privada") || s.contains("workshop");
    }

    private static boolean ehRegular(String t) {
        return !ehEnsaio(t) && !ehPrivadaOuWorkshop(t);
    }

    // O professor recebe sempre por horas completas (aula de 45 min = 1 h).
    private static double horas(RegistoHoras r) {
        return pt.studioflow.util.HorasUtil.faturaveis(r.getInicio(), r.getFim());
    }

    private static boolean mesIgual(Mensalidade m, YearMonth mes) {
        return m.getAno() == mes.getYear() && m.getMes() != null
                && m.getMes().getValue() == mes.getMonthValue();
    }

    private static boolean registoDaTurma(RegistoHoras r, Turma t) {
        String rt = r.getTurma();
        // Registo sem turma não é imputado à rentabilidade de nenhuma turma (evita
        // contá-lo em duplicado quando o professor tem várias turmas); entra à
        // mesma no total do professor em pagamentosPorProfessor().
        if (rt == null || rt.isBlank()) return false;
        return TextoUtil.nomeIgual(rt, t.getDescricao()) || TextoUtil.nomeIgual(rt, t.getCodigo());
    }

    private static int freqAluno(Mensalidade m, List<AlunoTurma> inscricoes) {
        if (m.getAluno() == null || m.getTurma() == null) return 2;
        return inscricoes.stream()
                .filter(at -> at.getTurma() != null && at.getAluno() != null
                        && at.getTurma().getId().equals(m.getTurma().getId())
                        && at.getAluno().getId().equals(m.getAluno().getId()))
                .findFirst()
                .map(AlunoTurma::getAulasPorSemana)
                .orElse(2);
    }

    /**
     * Nº de horas agendadas (Aula) para a turma no mês atribuíveis ao professor
     * {@code p}, contando as ocorrências de cada dia. Numa turma partilhada, só
     * conta os dias cujo {@link Aula#getProfessorEfetivo()} seja {@code p}.
     */
    private static double horasAgendadas(Turma t, YearMonth mes, List<Aula> aulas, Professor p) {
        double horas = 0;
        for (Aula au : aulas) {
            if (au.getTurma() == null || !au.getTurma().getId().equals(t.getId())) continue;
            if (au.getHoraInicio() == null || au.getHoraFim() == null || au.getDia() == null) continue;
            Professor efetivo = au.getProfessorEfetivo();
            if (p != null && (efetivo == null || !efetivo.getId().equals(p.getId()))) continue;
            long ocorrencias = 0;
            for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
                if (mes.atDay(dia).getDayOfWeek() == au.getDia()) ocorrencias++;
            }
            horas += ocorrencias * pt.studioflow.util.HorasUtil.faturaveis(au.getHoraInicio(), au.getHoraFim());
        }
        return horas;
    }

    private static Double coalesce(Double a, Double b) {
        return a != null ? a : b;
    }
}
