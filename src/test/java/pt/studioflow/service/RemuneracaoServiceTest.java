package pt.studioflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.Aula;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Professor;
import pt.studioflow.model.RegistoHoras;
import pt.studioflow.model.Studio;
import pt.studioflow.model.TipoRemuneracao;
import pt.studioflow.model.Turma;

/** Testes puros (sem contexto Spring/BD) do cálculo de remuneração de professores. */
class RemuneracaoServiceTest {

    private final RemuneracaoService service = new RemuneracaoService(new MensalidadeConfig());

    private final YearMonth mesPassado = YearMonth.now().minusMonths(1);
    private final YearMonth mesFuturo = YearMonth.now().plusMonths(2);

    // ---------- helpers ----------

    private static void setId(Object entity, long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Studio studio(String tipo) {
        Studio s = new Studio();
        setId(s, 1L);
        s.setTipoRemuneracaoProf(tipo);
        return s;
    }

    private Professor professor(Studio s) {
        Professor p = new Professor("Ana Marques", "ana@x.pt", "911");
        setId(p, 10L);
        p.setStudio(s);
        p.setValorHoraAula(20.0);
        return p;
    }

    private Turma turma(long id, Professor p, Studio s) {
        Turma t = new Turma();
        setId(t, id);
        t.setDescricao("Ballet A");
        t.setCodigo("BALA");
        t.setProfessor(p);
        t.setStudio(s);
        return t;
    }

    private RegistoHoras registo(String prof, String tipoAtividade, YearMonth mes, double horas) {
        RegistoHoras r = new RegistoHoras();
        r.setProfessor(prof);
        r.setTipoAtividade(tipoAtividade);
        r.setTurma("Ballet A"); // corresponde ao descricao da turma de teste
        LocalDateTime inicio = mes.atDay(10).atTime(10, 0);
        r.setInicio(inicio);
        r.setFim(inicio.plusMinutes((long) (horas * 60)));
        r.setAno(mes.getYear());
        return r;
    }

    private Aluno aluno(long id, boolean crianca, boolean socio) {
        Aluno a = new Aluno();
        setId(a, id);
        a.setCrianca(crianca);
        a.setSocio(socio);
        a.setAtivo(true);
        return a;
    }

    private AlunoTurma inscricao(Aluno a, Turma t, int vezes) {
        AlunoTurma at = new AlunoTurma();
        at.setAluno(a);
        at.setTurma(t);
        at.setAulasPorSemana(vezes);
        return at;
    }

    private Mensalidade mensalidade(Aluno a, Turma t, YearMonth mes, double valor) {
        Mensalidade m = new Mensalidade();
        m.setAluno(a);
        m.setTurma(t);
        m.setAno(mes.getYear());
        m.setMes(Month.of(mes.getMonthValue()));
        m.setValor(valor);
        return m;
    }

    // ---------- resolução de configuração ----------

    @Test
    void tipoEfetivo_overrideDoProfessorGanhaAoEstudio() {
        Studio s = studio("HORA");
        Professor p = professor(s);
        p.setTipoRemuneracao("PERCENTAGEM");
        assertThat(service.tipoEfetivo(p, s)).isEqualTo(TipoRemuneracao.PERCENTAGEM);
    }

    @Test
    void tipoEfetivo_semOverrideHerdaDoEstudio_eCaiEmHoraSemNada() {
        assertThat(service.tipoEfetivo(professor(studio("PERCENTAGEM")), studio("PERCENTAGEM")))
                .isEqualTo(TipoRemuneracao.PERCENTAGEM);
        assertThat(service.tipoEfetivo(new Professor(), new Studio())).isEqualTo(TipoRemuneracao.HORA);
    }

    @Test
    void valorHoraEnsaio_cadeiaDeFallback() {
        Studio s = studio("HORA");
        Professor p = professor(s);
        // sem nada: cai no valor da aula regular
        assertThat(service.valorHoraEnsaio(p, s)).isEqualTo(20.0);
        // default do estúdio
        s.setValorHoraEnsaioProf(8.0);
        assertThat(service.valorHoraEnsaio(p, s)).isEqualTo(8.0);
        // override do professor ganha
        p.setValorHoraEnsaio(12.0);
        assertThat(service.valorHoraEnsaio(p, s)).isEqualTo(12.0);
    }

    @Test
    void percentagem_escolheEscalaoEcaiEmOutras() {
        Studio s = studio("PERCENTAGEM");
        s.setPercProf1x(50.0);
        s.setPercProf2x(45.0);
        s.setPercProfOutras(30.0);
        Professor p = professor(s);

        assertThat(service.percentagem(p, s, 1)).isEqualTo(50.0);
        assertThat(service.percentagem(p, s, 2)).isEqualTo(45.0);
        assertThat(service.percentagem(p, s, 3)).isEqualTo(30.0); // sem 3x → outras
        assertThat(service.percentagem(p, s, 5)).isEqualTo(30.0);

        p.setPerc2x(60.0); // override do professor
        assertThat(service.percentagem(p, s, 2)).isEqualTo(60.0);
    }

    // ---------- custo real: modo HORA ----------

    @Test
    void custoProfessorTurma_modoHora_somaHorasPorTipoDeAtividade() {
        Studio s = studio("HORA");
        s.setValorHoraEnsaioProf(10.0);
        Professor p = professor(s); // 20 €/h regular
        Turma t = turma(100L, p, s);

        RemuneracaoService.Dados d = new RemuneracaoService.Dados().registos(List.of(
                registo("Ana Marques", "Aula regular", mesPassado, 2.0),
                registo("Ana Marques", "Ensaio", mesPassado, 1.5)));

        // 2h * 20 + 1.5h * 10 = 55
        assertThat(service.custoProfessorTurma(t, mesPassado, d)).isCloseTo(55.0, within(0.001));
    }

    // ---------- custo real: modo PERCENTAGEM ----------

    @Test
    void custoProfessorTurma_modoPercentagem_percentagemDaMensalidadePorFrequencia() {
        Studio s = studio("PERCENTAGEM");
        s.setPercProf1x(50.0);
        s.setPercProf2x(40.0);
        Professor p = professor(s);
        Turma t = turma(100L, p, s);

        Aluno a1 = aluno(1L, true, true);
        Aluno a2 = aluno(2L, false, true);

        RemuneracaoService.Dados d = new RemuneracaoService.Dados()
                .mensalidades(List.of(
                        mensalidade(a1, t, mesPassado, 30.0),   // 1x → 50% = 15
                        mensalidade(a2, t, mesPassado, 40.0)))   // 2x → 40% = 16
                .inscricoes(List.of(inscricao(a1, t, 1), inscricao(a2, t, 2)))
                .registos(List.of(registo("Ana Marques", "Ensaio", mesPassado, 2.0))); // 2h * 20 (fallback) = 40

        assertThat(service.custoProfessorTurma(t, mesPassado, d)).isCloseTo(15.0 + 16.0 + 40.0, within(0.001));
    }

    // ---------- previsão: mês futuro ----------

    @Test
    void custoProfessorTurma_mesFuturo_modoHora_usaAulasAgendadas() {
        Studio s = studio("HORA");
        Professor p = professor(s); // 20 €/h
        Turma t = turma(100L, p, s);

        Aula aula = new Aula();
        aula.setTurma(t);
        aula.setDia(DayOfWeek.MONDAY);
        aula.setHoraInicio(LocalTime.of(18, 0));
        aula.setHoraFim(LocalTime.of(19, 0)); // 1h por ocorrência

        long segundasNoMes = 0;
        for (int dia = 1; dia <= mesFuturo.lengthOfMonth(); dia++) {
            if (mesFuturo.atDay(dia).getDayOfWeek() == DayOfWeek.MONDAY) segundasNoMes++;
        }

        RemuneracaoService.Dados d = new RemuneracaoService.Dados().aulas(List.of(aula));
        assertThat(service.custoProfessorTurma(t, mesFuturo, d))
                .isCloseTo(segundasNoMes * 20.0, within(0.001));
    }

    @Test
    void custoProfessorTurma_mesFuturo_modoPercentagem_projetaMensalidadesDasInscricoes() {
        Studio s = studio("PERCENTAGEM");
        s.setMensalidadeAdulto2x(30.0);
        s.setMensalidadeNaoSocioAdicional(10.0);
        s.setPercProf2x(50.0);
        Professor p = professor(s);
        Turma t = turma(100L, p, s);

        Aluno socio = aluno(1L, false, true);      // 30 * 50% = 15
        Aluno naoSocio = aluno(2L, false, false);  // (30 + 10) * 50% = 20

        RemuneracaoService.Dados d = new RemuneracaoService.Dados()
                .inscricoes(List.of(inscricao(socio, t, 2), inscricao(naoSocio, t, 2)));

        assertThat(service.custoProfessorTurma(t, mesFuturo, d)).isCloseTo(35.0, within(0.001));
    }

    @Test
    void custoProfessorTurma_registoSemTurmaNaoEImputadoARentabilidade() {
        Studio s = studio("HORA");
        Professor p = professor(s);
        Turma t = turma(100L, p, s);
        RegistoHoras semTurma = registo("Ana Marques", "Ensaio", mesPassado, 3.0);
        semTurma.setTurma(null);
        RemuneracaoService.Dados d = new RemuneracaoService.Dados().registos(List.of(semTurma));
        assertThat(service.custoProfessorTurma(t, mesPassado, d)).isEqualTo(0.0);
    }

    @Test
    void descricaoEfetiva_textoConformeModo() {
        Studio hora = studio("HORA");
        assertThat(service.descricaoEfetiva(professor(hora), hora)).isEqualTo("20 €/h");

        Studio perc = studio("PERCENTAGEM");
        perc.setPercProf1x(50.0);
        perc.setPercProf2x(45.0);
        perc.setPercProf3x(40.0);
        assertThat(service.descricaoEfetiva(professor(perc), perc)).contains("1x 50%").contains("2x 45%");
    }
}
