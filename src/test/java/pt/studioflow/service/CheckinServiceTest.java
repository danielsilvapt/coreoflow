package pt.studioflow.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import pt.studioflow.model.*;
import pt.studioflow.repository.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checkin automático de aulas avulso/packs: janela antes/depois do início da
 * aula, elegibilidade (matrícula vs crédito), consumo de crédito e idempotência.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CheckinService.class)
class CheckinServiceTest {

    @Autowired private StudioRepository studioRepository;
    @Autowired private ModalidadeRepository modalidadeRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private AlunoRepository alunoRepository;
    @Autowired private AlunoTurmaRepository alunoTurmaRepository;
    @Autowired private PackAulaRepository packAulaRepository;
    @Autowired private CompraCreditoRepository compraCreditoRepository;
    @Autowired private PresencaRepository presencaRepository;
    @Autowired private CheckinService checkinService;

    @MockBean
    private EmailService emailService;

    private Studio novoStudio() {
        Studio s = new Studio();
        s.setNome("Studio Checkin");
        s.setSlug("studio-checkin-" + System.nanoTime());
        s.setAtivo(true);
        s.setCheckinJanelaAntesMin(5);
        s.setCheckinJanelaDepoisMin(15);
        return studioRepository.save(s);
    }

    private Modalidade novaModalidade(Studio studio, String nome) {
        Modalidade m = new Modalidade();
        m.setDescricao(nome);
        m.setAtivo(true);
        m.setStudio(studio);
        return modalidadeRepository.save(m);
    }

    private Turma novaTurma(Studio studio, Modalidade modalidade, LocalDateTime horaInicioAula) {
        Turma t = new Turma();
        t.setCodigo("T-" + System.nanoTime());
        t.setDescricao("Turma Teste");
        t.setModalidade(modalidade);
        t.setStudio(studio);
        t.setAtivo(true);
        t = turmaRepository.save(t);

        Aula aula = new Aula();
        aula.setTurma(t);
        aula.setDia(horaInicioAula.getDayOfWeek());
        aula.setHoraInicio(horaInicioAula.toLocalTime());
        aula.setHoraFim(horaInicioAula.toLocalTime().plusHours(1));
        t.setAulas(new java.util.ArrayList<>(List.of(aula)));
        return turmaRepository.save(t);
    }

    private Aluno novoAluno(Studio studio, String email) {
        Aluno a = new Aluno();
        a.setNomeCompleto("Aluno " + email);
        a.setEmail(email);
        a.setStudio(studio);
        a.setStatus(Aluno.AlunoStatus.EXPERIMENTAL);
        a.setAtivo(true);
        return alunoRepository.save(a);
    }

    private CompraCredito novoCredito(Studio studio, Aluno aluno, Modalidade modalidade, boolean restrito, int creditos) {
        PackAula pack = new PackAula();
        pack.setStudio(studio);
        pack.setNome("Pack Teste");
        pack.setNumAulas(creditos);
        pack.setPreco(BigDecimal.TEN);
        pack.setRestritoAModalidade(restrito);
        pack.setAtivo(true);
        pack = packAulaRepository.save(pack);

        CompraCredito c = new CompraCredito();
        c.setStudio(studio);
        c.setAluno(aluno);
        c.setPackAula(pack);
        c.setNomePack(pack.getNome());
        c.setNumAulasComprado(creditos);
        c.setCreditosRestantes(creditos);
        c.setPrecoPago(pack.getPreco());
        c.setModalidade(modalidade);
        c.setDataCompra(LocalDateTime.now());
        c.setMetodoPagamento(MetodoPagamentoCredito.MANUAL);
        c.setEstadoPagamento(EstadoPagamentoCredito.PENDENTE);
        c.setOrigem(OrigemCompraCredito.JOIN_PUBLICO);
        return compraCreditoRepository.save(c);
    }

    @Test
    void listarTurmasParaCheckin_incluiApenasAulasDentroDaJanela() {
        Studio studio = novoStudio();
        Modalidade modalidade = novaModalidade(studio, "Jazz");
        Aluno aluno = novoAluno(studio, "dentro@test.com");

        LocalDateTime agora = LocalDateTime.now();
        Turma dentro = novaTurma(studio, modalidade, agora); // agora mesmo -> dentro da janela
        Turma foraNoPassado = novaTurma(studio, modalidade, agora.minusHours(3));
        Turma foraNoFuturo = novaTurma(studio, modalidade, agora.plusHours(3));

        AlunoTurma at1 = new AlunoTurma(); at1.setAluno(aluno); at1.setTurma(dentro); alunoTurmaRepository.save(at1);
        AlunoTurma at2 = new AlunoTurma(); at2.setAluno(aluno); at2.setTurma(foraNoPassado); alunoTurmaRepository.save(at2);
        AlunoTurma at3 = new AlunoTurma(); at3.setAluno(aluno); at3.setTurma(foraNoFuturo); alunoTurmaRepository.save(at3);

        List<CheckinService.TurmaCheckin> disponiveis = checkinService.listarTurmasParaCheckin(aluno, agora);

        assertThat(disponiveis).extracting(tc -> tc.turma.getId()).containsExactly(dentro.getId());
    }

    @Test
    void listarTurmasParaCheckin_naoMatriculadoSemCredito_naoAparece() {
        Studio studio = novoStudio();
        Modalidade modalidade = novaModalidade(studio, "Jazz");
        Aluno aluno = novoAluno(studio, "semcredito@test.com");
        Turma turma = novaTurma(studio, modalidade, LocalDateTime.now());

        List<CheckinService.TurmaCheckin> disponiveis = checkinService.listarTurmasParaCheckin(aluno, LocalDateTime.now());

        assertThat(disponiveis).isEmpty();
    }

    @Test
    void listarTurmasParaCheckin_naoMatriculadoComCreditoValido_aparece() {
        Studio studio = novoStudio();
        Modalidade modalidade = novaModalidade(studio, "Jazz");
        Aluno aluno = novoAluno(studio, "comcredito@test.com");
        Turma turma = novaTurma(studio, modalidade, LocalDateTime.now());
        novoCredito(studio, aluno, modalidade, false, 3);

        List<CheckinService.TurmaCheckin> disponiveis = checkinService.listarTurmasParaCheckin(aluno, LocalDateTime.now());

        assertThat(disponiveis).hasSize(1);
        assertThat(disponiveis.get(0).matriculado).isFalse();
        assertThat(disponiveis.get(0).creditoElegivel).isNotNull();
    }

    @Test
    void listarTurmasParaCheckin_creditoRestritoAModalidadeErrada_naoAparece() {
        Studio studio = novoStudio();
        Modalidade modalidadeDaTurma = novaModalidade(studio, "Jazz");
        Modalidade outraModalidade = novaModalidade(studio, "Ballet");
        Aluno aluno = novoAluno(studio, "restrito@test.com");
        novaTurma(studio, modalidadeDaTurma, LocalDateTime.now());
        // crédito restrito comprado para outra modalidade
        novoCredito(studio, aluno, outraModalidade, true, 3);

        List<CheckinService.TurmaCheckin> disponiveis = checkinService.listarTurmasParaCheckin(aluno, LocalDateTime.now());

        assertThat(disponiveis).isEmpty();
    }

    @Test
    void registarCheckin_matriculado_naoConsomeCredito() {
        Studio studio = novoStudio();
        Modalidade modalidade = novaModalidade(studio, "Jazz");
        Aluno aluno = novoAluno(studio, "matriculado@test.com");
        Turma turma = novaTurma(studio, modalidade, LocalDateTime.now());
        AlunoTurma at = new AlunoTurma(); at.setAluno(aluno); at.setTurma(turma); alunoTurmaRepository.save(at);

        Presenca presenca = checkinService.registarCheckin(aluno, turma, MetodoRegistoPresenca.AUTO_ALUNO);

        assertThat(presenca.isPresente()).isTrue();
        assertThat(presenca.getOrigem()).isEqualTo(OrigemPresenca.MATRICULA);
        assertThat(presenca.getCompraCredito()).isNull();
    }

    @Test
    void registarCheckin_avulso_consomeUmCredito() {
        Studio studio = novoStudio();
        Modalidade modalidade = novaModalidade(studio, "Jazz");
        Aluno aluno = novoAluno(studio, "avulso@test.com");
        Turma turma = novaTurma(studio, modalidade, LocalDateTime.now());
        CompraCredito credito = novoCredito(studio, aluno, modalidade, false, 3);

        Presenca presenca = checkinService.registarCheckin(aluno, turma, MetodoRegistoPresenca.AUTO_ALUNO);

        assertThat(presenca.getOrigem()).isEqualTo(OrigemPresenca.CREDITO_AVULSO);
        assertThat(presenca.getCompraCredito().getId()).isEqualTo(credito.getId());

        CompraCredito atualizado = compraCreditoRepository.findById(credito.getId()).orElseThrow();
        assertThat(atualizado.getCreditosRestantes()).isEqualTo(2);
    }

    @Test
    void registarCheckin_duasVezesNoMesmoDia_lancaExcecao() {
        Studio studio = novoStudio();
        Modalidade modalidade = novaModalidade(studio, "Jazz");
        Aluno aluno = novoAluno(studio, "duplicado@test.com");
        Turma turma = novaTurma(studio, modalidade, LocalDateTime.now());
        AlunoTurma at = new AlunoTurma(); at.setAluno(aluno); at.setTurma(turma); alunoTurmaRepository.save(at);

        checkinService.registarCheckin(aluno, turma, MetodoRegistoPresenca.AUTO_ALUNO);

        assertThatThrownBy(() -> checkinService.registarCheckin(aluno, turma, MetodoRegistoPresenca.AUTO_ALUNO))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registarCheckin_foraDaJanela_lancaExcecao() {
        Studio studio = novoStudio();
        Modalidade modalidade = novaModalidade(studio, "Jazz");
        Aluno aluno = novoAluno(studio, "fora@test.com");
        Turma turma = novaTurma(studio, modalidade, LocalDateTime.now().minusHours(5));
        AlunoTurma at = new AlunoTurma(); at.setAluno(aluno); at.setTurma(turma); alunoTurmaRepository.save(at);

        assertThatThrownBy(() -> checkinService.registarCheckin(aluno, turma, MetodoRegistoPresenca.AUTO_ALUNO))
                .isInstanceOf(IllegalStateException.class);
    }
}
