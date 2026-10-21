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
}
