package pt.studioflow.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.studioflow.model.PackAula;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.PackAulaRepository;

@Service
public class PackAulaService {

    private final PackAulaRepository packAulaRepository;

    public PackAulaService(PackAulaRepository packAulaRepository) {
        this.packAulaRepository = packAulaRepository;
    }

    public List<PackAula> listarPorStudio(Studio studio) {
        return packAulaRepository.findAllByStudio(studio);
    }

    public List<PackAula> listarAtivosPorStudio(Studio studio) {
        return packAulaRepository.findByStudioAndAtivoTrue(studio);
    }

    @Transactional
    public PackAula save(PackAula pack) {
        return packAulaRepository.save(pack);
    }

    @Transactional
    public void deleteById(Long id) {
        packAulaRepository.deleteById(id);
    }
}
