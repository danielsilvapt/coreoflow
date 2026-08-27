package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.ListaEspera;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

import jakarta.transaction.Transactional;
import java.util.List;

@Repository
public interface ListaEsperaRepository extends JpaRepository<ListaEspera, Long> {

    List<ListaEspera> findByTurmaAndEstadoOrderByDataInscricaoAsc(Turma turma, ListaEspera.Estado estado);

    List<ListaEspera> findByStudioOrderByDataInscricaoAsc(Studio studio);

    List<ListaEspera> findByTurmaAndStudioOrderByDataInscricaoAsc(Turma turma, Studio studio);

    long countByTurmaAndEstado(Turma turma, ListaEspera.Estado estado);

    @Transactional
    void deleteByTurma(Turma turma);
}
