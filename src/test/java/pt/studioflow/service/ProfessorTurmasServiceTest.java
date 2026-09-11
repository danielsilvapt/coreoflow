package pt.studioflow.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import pt.studioflow.model.*;
import pt.studioflow.repository.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uma turma partilhada (professor principal + um professor de um dia
 * concreto via Aula.professor) tem de dar acesso à turma inteira a AMBOS,
 * exatamente como já acontece hoje com coProfessores — é o resolvedor
 * canónico usado por todas as vistas de professor.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProfessorTurmasService.class)
class ProfessorTurmasServiceTest {

    @Autowired private StudioRepository studioRepository;
    @Autowired private ModalidadeRepository modalidadeRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private ProfessorRepository professorRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfessorTurmasService service;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user.getUsername(), "pwd",
                List.of(new SimpleGrantedAuthority("ROLE_PROF")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User novoUser(Studio studio, String username, String email, String firstName) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("x");
        u.setEmail(email);
        u.setFirstName(firstName);
        u.setRole("PROF");
        u.setStudio(studio);
        return userRepository.save(u);
    }

    @Test
    void turmasDoUtilizador_incluiTurmaPartilhadaOndeSoDaUmDia() {
        Studio studio = new Studio();
        studio.setNome("Studio Turmas Partilhadas");
        studio.setSlug("studio-partilhada-" + System.nanoTime());
        studio.setAtivo(true);
        studio = studioRepository.save(studio);

        Modalidade modalidade = new Modalidade();
        modalidade.setDescricao("Ballet");
        modalidade.setStudio(studio);
        modalidade = modalidadeRepository.save(modalidade);

        Professor principal = new Professor("Jussara Dias", "jussara@x.pt", "911");
        principal.setStudio(studio);
        principal = professorRepository.save(principal);

        Professor outro = new Professor("Iolanda Sousa", "iolanda@x.pt", "922");
        outro.setStudio(studio);
        outro = professorRepository.save(outro);

        Turma turma = new Turma();
        turma.setCodigo("BARRE-9H30");
        turma.setDescricao("Barre 9h30");
        turma.setModalidade(modalidade);
        turma.setStudio(studio);
        turma.setAtivo(true);
        turma.setProfessor(principal);
        turma = turmaRepository.save(turma);

        Aula aulaQuarta = new Aula();
        aulaQuarta.setTurma(turma);
        aulaQuarta.setDia(DayOfWeek.WEDNESDAY);
        aulaQuarta.setHoraInicio(LocalTime.of(9, 30));
        aulaQuarta.setHoraFim(LocalTime.of(10, 30));
        aulaQuarta.setProfessor(outro); // Iolanda só dá esta turma à quarta
        turma.setAulas(new ArrayList<>(List.of(aulaQuarta)));
        turma = turmaRepository.save(turma);

        User userOutro = novoUser(studio, "iolanda", outro.getEmail(), "Iolanda");
        autenticarComo(userOutro);

        List<Turma> turmasDeIolanda = service.turmasDoUtilizador(studio);

        assertThat(turmasDeIolanda).extracting(Turma::getId).containsExactly(turma.getId());
    }

    @Test
    void turmasDoUtilizador_naoInclui_professorSemQualquerAulaNemPrincipalNemCoProfessor() {
        Studio studio = new Studio();
        studio.setNome("Studio Sem Acesso");
        studio.setSlug("studio-sem-acesso-" + System.nanoTime());
        studio.setAtivo(true);
        studio = studioRepository.save(studio);

        Modalidade modalidade = new Modalidade();
        modalidade.setDescricao("Jazz");
        modalidade.setStudio(studio);
        modalidade = modalidadeRepository.save(modalidade);

        Professor principal = new Professor("Lucas Henriques", "lucas@x.pt", "933");
        principal.setStudio(studio);
        principal = professorRepository.save(principal);

        Professor semAcesso = new Professor("Rubia Queiroz", "rubia@x.pt", "944");
        semAcesso.setStudio(studio);
        semAcesso = professorRepository.save(semAcesso);

        Turma turma = new Turma();
        turma.setCodigo("YOGA");
        turma.setDescricao("Yoga");
        turma.setModalidade(modalidade);
        turma.setStudio(studio);
        turma.setAtivo(true);
        turma.setProfessor(principal);
        turmaRepository.save(turma);

        User userSemAcesso = novoUser(studio, "rubia", semAcesso.getEmail(), "Rubia");
        autenticarComo(userSemAcesso);

        assertThat(service.turmasDoUtilizador(studio)).isEmpty();
    }
}
