package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pt.studioflow.model.ConfiguracaoPlataforma;

@Repository
public interface ConfiguracaoPlataformaRepository extends JpaRepository<ConfiguracaoPlataforma, Long> {
}
