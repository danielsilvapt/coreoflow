package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.ContaPortal;
import pt.studioflow.model.Studio;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContaPortalRepository extends JpaRepository<ContaPortal, Long> {

    Optional<ContaPortal> findByEmailIgnoreCase(String email);

    Optional<ContaPortal> findByTokenAtivacao(String tokenAtivacao);

    List<ContaPortal> findAllByStudio(Studio studio);
}
