package pt.studioflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.AlunoTurma;
import pt.studioflow.model.ListaEspera;
import pt.studioflow.model.Turma;
import pt.studioflow.repository.AlunoTurmaRepository;
import pt.studioflow.repository.ListaEsperaRepository;

import java.util.List;

@Service
public class AlunoTurmaService {

    private final AlunoTurmaRepository repository;
    private final ListaEsperaRepository listaEsperaRepository;

    public AlunoTurmaService(AlunoTurmaRepository repository,
                              ListaEsperaRepository listaEsperaRepository) {
        this.repository = repository;
        this.listaEsperaRepository = listaEsperaRepository;
    }

    @Transactional
    public void adicionar(Aluno aluno, Turma turma) {
        if (repository.existsByAlunoAndTurma(aluno, turma)) return;
        AlunoTurma at = new AlunoTurma();
        at.setAluno(aluno);
        at.setTurma(turma);
        repository.save(at);
    }

    @Transactional
    public void remover(Aluno aluno, Turma turma) {
        repository.deleteByAlunoAndTurma(aluno, turma);
        notificarProximoNaEspera(turma);
    }

    /**
     * Após libertar uma vaga, notifica via WhatsApp o primeiro da lista de espera.
     */
    private void notificarProximoNaEspera(Turma turma) {
        List<ListaEspera> fila = listaEsperaRepository
                .findByTurmaAndEstadoOrderByDataInscricaoAsc(turma, ListaEspera.Estado.AGUARDA);

        if (fila.isEmpty()) return;

        ListaEspera proximo = fila.get(0);
        proximo.setEstado(ListaEspera.Estado.NOTIFICADO);
        listaEsperaRepository.save(proximo);

        // Abrir WhatsApp — o utilizador confirma manualmente (sem envio automático server-side)
        // A view ListaEsperaView mostra um badge "Notificado" e o botão de WhatsApp fica em destaque
    }

    /**
     * Retorna a URL do WhatsApp para notificar manualmente um elemento da lista.
     */
    public static String whatsappUrlEspera(ListaEspera entrada, String nomeTurma) {
        if (entrada.getTelemovel() == null) return null;
        String numero = entrada.getTelemovel().replaceAll("\\D", "");
        if (numero.length() == 9) numero = "351" + numero;
        String msg = "Olá " + entrada.getNomeCompleto().split(" ")[0]
                + "! Abriu uma vaga na turma " + nomeTurma
                + ". Contacta-nos para confirmar a inscrição.";
        return "https://wa.me/" + numero + "?text=" + msg.replace(" ", "%20");
    }
}
