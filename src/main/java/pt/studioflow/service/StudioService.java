package pt.studioflow.service;

import org.springframework.stereotype.Service;
import pt.studioflow.model.Studio;
import pt.studioflow.repository.StudioRepository;

import java.util.List;
import java.util.Optional;

@Service
public class StudioService {

    private final StudioRepository studioRepository;

    public StudioService(StudioRepository studioRepository) {
        this.studioRepository = studioRepository;
    }

    public List<Studio> findAll() {
        return studioRepository.findAll();
    }

    public Optional<Studio> findById(Long id) {
        return studioRepository.findById(id);
    }

    public Optional<Studio> findBySlug(String slug) {
        return studioRepository.findBySlugAndAtivoTrue(slug);
    }

    public Studio save(Studio studio) {
        return studioRepository.save(studio);
    }

    public void delete(Long id) {
        studioRepository.deleteById(id);
    }
}
