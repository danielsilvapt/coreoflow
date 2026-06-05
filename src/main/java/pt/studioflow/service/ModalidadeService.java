package pt.studioflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.Modalidade;
import pt.studioflow.repository.ModalidadeRepository;

@Service
public class ModalidadeService {

    private final ModalidadeRepository modalidadeRepository;

    public ModalidadeService(ModalidadeRepository modalidadeRepository) {
        this.modalidadeRepository = modalidadeRepository;
    }

    @Transactional
    public Modalidade save(Modalidade modalidade) {
        return modalidadeRepository.save(modalidade);
    }

    @Transactional
    public void deleteById(Long id) {
        // opcional: verificar se existe antes de apagar
        if (modalidadeRepository.existsById(id)) {
            modalidadeRepository.deleteById(id);
        } else {
            throw new IllegalArgumentException("Modalidade com id " + id + " não existe");
        }
    }

}
