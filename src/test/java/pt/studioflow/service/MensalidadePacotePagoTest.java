package pt.studioflow.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import pt.studioflow.config.MensalidadeConfig;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Aluno.AlunoStatus;
import pt.studioflow.model.EstadoMensalidade;
import pt.studioflow.model.Mensalidade;
import pt.studioflow.model.Modalidade;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoRepository;
import pt.studioflow.repository.MensalidadeRepository;
import pt.studioflow.repository.ModalidadeRepository;
import pt.studioflow.repository.StudioRepository;
import pt.studioflow.repository.TurmaRepository;

import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ MensalidadeService.class, MensalidadeConfig.class })
class MensalidadePacotePagoTest {

    @Autowired private StudioRepository studioRepository;
    @Autowired private ModalidadeRepository modalidadeRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private AlunoRepository alunoRepository;
    @Autowired private MensalidadeRepository mensalidadeRepository;
    @Autowired private MensalidadeService mensalidadeService;
    @Autowired private pt.studioflow.repository.AlunoTurmaRepository alunoTurmaRepository;

    private Studio studio;
    private Modalidade modalidade;
    private Aluno aluno;

    private void preparar(String slug, String modelo) {
        studio = new Studio();
        studio.setNome("Studio " + slug);
        studio.setSlug(slug);
        studio.setAtivo(true);
        studio.setModeloPrecario(modelo);
        studio = studioRepository.save(studio);

        modalidade = new Modalidade();
        modalidade.setDescricao("Ballet");
        modalidade.setStudio(studio);
        modalidade = modalidadeRepository.save(modalidade);

        aluno = new Aluno();
        aluno.setNomeCompleto("Aluno Pacote");
        aluno.setStudio(studio);
        aluno.setStatus(AlunoStatus.ATIVO);
        aluno = alunoRepository.save(aluno);
    }

    private Turma turma(String codigo, Double mensalidadePropria) {
        Turma t = new Turma();
        t.setCodigo(codigo);
        t.setDescricao("Turma " + codigo);
        t.setModalidade(modalidade);
        t.setStudio(studio);
        t.setAtivo(true);
        t.setMensalidadeSocio(mensalidadePropria);
        return turmaRepository.save(t);
    }

    private Mensalidade mensalidade(Turma t, Month mes, EstadoMensalidade estado) {
        Mensalidade m = new Mensalidade();
        m.setAluno(aluno);
        m.setTurma(t);
        m.setAno(2026);
        m.setMes(mes);
        m.setValor(21.5);
        m.setEstado(estado);
        m.setStudio(studio);
        return mensalidadeRepository.save(m);
    }

    @Test
    void modeloHorasSemana_pagarUmaParte_pagaAsRestantesDoMesmoMes() {
        preparar("pacote-horas", "HORAS_SEMANA");
        Turma a = turma("A", null);
        Turma b = turma("B", null);
        Turma competicao = turma("C", 50.0);

        Mensalidade paga = mensalidade(a, Month.NOVEMBER, EstadoMensalidade.PAGO);
        Mensalidade irma = mensalidade(b, Month.NOVEMBER, EstadoMensalidade.FATURADO);
        Mensalidade outroMes = mensalidade(b, Month.DECEMBER, EstadoMensalidade.POR_EMITIR);
        Mensalidade propria = mensalidade(competicao, Month.NOVEMBER, EstadoMensalidade.POR_EMITIR);

        int n = mensalidadeService.marcarPacoteDoMesComoPago(paga, studio);

        assertThat(n).isEqualTo(1);
        assertThat(mensalidadeRepository.findById(irma.getId()).get().getEstado()).isEqualTo(EstadoMensalidade.PAGO);
        assertThat(mensalidadeRepository.findById(outroMes.getId()).get().getEstado())
                .isEqualTo(EstadoMensalidade.POR_EMITIR);
        assertThat(mensalidadeRepository.findById(propria.getId()).get().getEstado())
                .isEqualTo(EstadoMensalidade.POR_EMITIR);
    }

    @Test
    void gerar_modeloHorasSemana_irmaoComDescontoPercentual() {
        preparar("pacote-irmao", "HORAS_SEMANA");
        studio.setDescontoFamiliaresPercentagem(10.0);
        studio.setMeiaMensalidadeAutomatica(false);
        studio = studioRepository.save(studio);
        aluno.setDescontoFamiliar(true);
        aluno = alunoRepository.save(aluno);
        Turma a = turma("A", null);
        Turma b = turma("B", null);
        for (Turma t : java.util.List.of(a, b)) {
            pt.studioflow.model.AlunoTurma at = new pt.studioflow.model.AlunoTurma();
            at.setAluno(aluno);
            at.setTurma(t);
            at.setAulasPorSemana(1);
            alunoTurmaRepository.save(at);
        }

        mensalidadeService.gerarMensalidadesParaAluno(aluno, a);

        java.util.List<Mensalidade> geradas = mensalidadeRepository.findByAluno(aluno);
        assertThat(geradas).isNotEmpty();
        // 43€ (2 aulas/semana) - 10% = 38,70€, repartidos por 2 turmas
        assertThat(geradas).allSatisfy(m -> {
            assertThat(m.getValor()).isEqualTo(19.35);
            assertThat(m.getDesconto()).isEqualTo(pt.studioflow.model.TipoDesconto.DESCONTO_FAMILIARES);
        });
    }

    @Test
    void modeloPadrao_naoMexeNasOutrasTurmas() {
        preparar("pacote-padrao", "PADRAO");
        Turma a = turma("A", null);
        Turma b = turma("B", null);

        Mensalidade paga = mensalidade(a, Month.NOVEMBER, EstadoMensalidade.PAGO);
        Mensalidade outra = mensalidade(b, Month.NOVEMBER, EstadoMensalidade.FATURADO);

        assertThat(mensalidadeService.marcarPacoteDoMesComoPago(paga, studio)).isZero();
        assertThat(mensalidadeRepository.findById(outra.getId()).get().getEstado())
                .isEqualTo(EstadoMensalidade.FATURADO);
    }

    private Turma turmaNa(Modalidade mod, String codigo) {
        Turma t = new Turma();
        t.setCodigo(codigo);
        t.setDescricao("Turma " + codigo);
        t.setModalidade(mod);
        t.setStudio(studio);
        t.setAtivo(true);
        return turmaRepository.save(t);
    }

    private void inscrever(Turma t) {
        pt.studioflow.model.AlunoTurma at = new pt.studioflow.model.AlunoTurma();
        at.setAluno(aluno);
        at.setTurma(t);
        at.setAulasPorSemana(1);
        alunoTurmaRepository.save(at);
    }

    private double totalDoMes(java.time.YearMonth ym) {
        return mensalidadeRepository.findByAluno(aluno).stream()
                .filter(m -> m.getAno() == ym.getYear() && m.getMes() == ym.getMonth())
                .mapToDouble(Mensalidade::getValor).sum();
    }

    @Test
    void modeloModalidades_turmaNovaRecalculaAsTurmasQueOAlunoJaTinha() {
        preparar("pacote-mod", "MODALIDADES");
        studio.setMeiaMensalidadeAutomatica(false);
        studio = studioRepository.save(studio);
        Modalidade barre = new Modalidade();
        barre.setDescricao("Barre");
        barre.setStudio(studio);
        barre = modalidadeRepository.save(barre);
        Turma ballet = turmaNa(modalidade, "BL");
        Turma barre1 = turmaNa(barre, "BR1");
        Turma barre2 = turmaNa(barre, "BR2");
        java.time.YearMonth agora = MensalidadeService.inicioPeriodoLetivo(java.time.LocalDate.now());

        inscrever(barre1);
        mensalidadeService.gerarMensalidadesParaAluno(aluno, barre1);
        assertThat(totalDoMes(agora)).isEqualTo(25.0); // aula fixa

        inscrever(barre2);
        mensalidadeService.gerarMensalidadesParaAluno(aluno, barre2);
        assertThat(totalDoMes(agora)).isEqualTo(40.0); // 1 modalidade, 2 aulas

        inscrever(ballet);
        mensalidadeService.gerarMensalidadesParaAluno(aluno, ballet);
        assertThat(totalDoMes(agora)).isEqualTo(50.0); // 2 modalidades
        assertThat(totalDoMes(agora.plusMonths(1))).isEqualTo(50.0);
    }

    @Test
    void recalcularPacote_naoMexeEmMesesJaPagos() {
        preparar("pacote-pago", "MODALIDADES");
        studio.setMeiaMensalidadeAutomatica(false);
        studio = studioRepository.save(studio);
        Turma a = turmaNa(modalidade, "A");
        Turma b = turmaNa(modalidade, "B");
        inscrever(a);
        inscrever(b);
        java.time.YearMonth agora = MensalidadeService.inicioPeriodoLetivo(java.time.LocalDate.now());
        Mensalidade paga = mensalidade(a, agora.getMonth(), EstadoMensalidade.PAGO);
        paga.setAno(agora.getYear());
        mensalidadeRepository.save(paga);
        Mensalidade porEmitir = mensalidade(b, agora.getMonth(), EstadoMensalidade.POR_EMITIR);
        porEmitir.setAno(agora.getYear());
        mensalidadeRepository.save(porEmitir);

        mensalidadeService.recalcularPacote(aluno, studio, agora);

        assertThat(mensalidadeRepository.findById(paga.getId()).get().getValor()).isEqualTo(21.5);
        assertThat(mensalidadeRepository.findById(porEmitir.getId()).get().getValor()).isEqualTo(21.5);
    }
}
