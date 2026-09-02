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

    /** Receita (mensalidades) da turma no mês — real para meses fechados, projetada para futuros. */
    public double receitaTurma(Turma t, YearMonth mes, Dados d, Studio studio) {
        if (!ehFuturo(mes)) {
            return d.mensalidades.stream()
                    .filter(m -> m.getTurma() != null && m.getTurma().getId().equals(t.getId()))
                    .filter(m -> mesIgual(m, mes))
                    .mapToDouble(Mensalidade::getValor).sum();
        }
        return d.inscricoes.stream()
                .filter(at -> at.getTurma() != null && at.getTurma().getId().equals(t.getId()))
                .filter(at -> at.getAluno() != null && at.getAluno().isAtivo())
                .mapToDouble(at -> mensalidadeProjetada(studio, at))
                .sum();
    }

    /** Custo do professor associado a uma turma no mês. */
    public double custoProfessorTurma(Turma t, YearMonth mes, Dados d) {
        Professor p = t.getProfessor();
        Studio s = t.getStudio();
        TipoRemuneracao tipo = tipoEfetivo(p, s);
        boolean futuro = ehFuturo(mes);

        if (tipo == TipoRemuneracao.PERCENTAGEM) {
            double regular = futuro
                    ? d.inscricoes.stream()
                        .filter(at -> at.getTurma() != null && at.getTurma().getId().equals(t.getId()))
                        .filter(at -> at.getAluno() != null && at.getAluno().isAtivo())
                        .mapToDouble(at -> mensalidadeProjetada(s, at)
                                * percentagem(p, s, at.getAulasPorSemana()) / 100.0)
                        .sum()
                    : d.mensalidades.stream()
                        .filter(m -> m.getTurma() != null && m.getTurma().getId().equals(t.getId()))
                        .filter(m -> mesIgual(m, mes))
                        .mapToDouble(m -> m.getValor()
                                * percentagem(p, s, freqAluno(m, d.inscricoes)) / 100.0)
                        .sum();
            // ensaios / privadas / workshops não têm mensalidade → pagos à hora (só reais)
            double extra = futuro ? 0.0 : horasExtraValorizadas(t, p, s, mes, d, true);
            return regular + extra;
        }

        // HORA
        if (futuro) {
            return horasAgendadas(t, mes, d.aulas) * valorHoraRegular(p, s);
        }
        return horasExtraValorizadas(t, p, s, mes, d, false);
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
    public Map<Long, double[]> rentabilidadePorTurma(List<Turma> turmas, YearMonth mes, Dados d) {
        Map<Long, double[]> res = new LinkedHashMap<>();
        for (Turma t : turmas) {
            double rec = receitaTurma(t, mes, d, t.getStudio());
            double custo = custoProfessorTurma(t, mes, d);
            res.put(t.getId(), new double[] { rec, custo, rec - custo });
        }
        return res;
    }

    /** Pagamento devido a cada professor no mês (uma linha por professor com atividade). */
    public List<LinhaPagamento> pagamentosPorProfessor(List<Professor> professores, List<Turma> turmas,
                                                       YearMonth mes, Dados d) {
        boolean futuro = ehFuturo(mes);
        List<LinhaPagamento> linhas = new ArrayList<>();

        for (Professor p : professores) {
            Studio s = p.getStudio();
            TipoRemuneracao modo = tipoEfetivo(p, s);
            List<Turma> turmasProf = turmas.stream()
                    .filter(t -> t.getProfessor() != null && t.getProfessor().getId().equals(p.getId()))
                    .toList();

            double base = 0, ensaios = 0, privadas = 0;

            if (modo == TipoRemuneracao.PERCENTAGEM) {
                for (Turma t : turmasProf) {
                    if (futuro) {
                        base += d.inscricoes.stream()
                                .filter(at -> at.getTurma() != null && at.getTurma().getId().equals(t.getId()))
                                .filter(at -> at.getAluno() != null && at.getAluno().isAtivo())
                                .mapToDouble(at -> mensalidadeProjetada(s, at)
                                        * percentagem(p, s, at.getAulasPorSemana()) / 100.0)
                                .sum();
                    } else {
                        base += d.mensalidades.stream()
                                .filter(m -> m.getTurma() != null && m.getTurma().getId().equals(t.getId()))
                                .filter(m -> mesIgual(m, mes))
                                .mapToDouble(m -> m.getValor()
                                        * percentagem(p, s, freqAluno(m, d.inscricoes)) / 100.0)
                                .sum();
                    }
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
                    base += horasAgendadas(t, mes, d.aulas) * valorHoraRegular(p, s);
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
        return mensalidadeConfig.calcularMensalidade(s,
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

    private static double horas(RegistoHoras r) {
        return Duration.between(r.getInicio(), r.getFim()).toMinutes() / 60.0;
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

    /** Nº de horas agendadas (Aula) para a turma no mês, contando as ocorrências de cada dia. */
    private static double horasAgendadas(Turma t, YearMonth mes, List<Aula> aulas) {
        double horas = 0;
        for (Aula au : aulas) {
            if (au.getTurma() == null || !au.getTurma().getId().equals(t.getId())) continue;
            if (au.getHoraInicio() == null || au.getHoraFim() == null || au.getDia() == null) continue;
            long ocorrencias = 0;
            for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
                if (mes.atDay(dia).getDayOfWeek() == au.getDia()) ocorrencias++;
            }
            horas += ocorrencias * (Duration.between(au.getHoraInicio(), au.getHoraFim()).toMinutes() / 60.0);
        }
        return horas;
    }

    private static Double coalesce(Double a, Double b) {
        return a != null ? a : b;
    }
}
