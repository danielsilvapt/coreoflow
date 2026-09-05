package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;
import pt.studioflow.model.VideoAula;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface VideoAulaRepository extends JpaRepository<VideoAula, Long> {
    List<VideoAula> findByTurmaAndDataOrderByDataUploadDesc(Turma turma, LocalDate data);
    List<VideoAula> findByTurmaInAndDataInOrderByDataUploadDesc(Collection<Turma> turmas, Collection<LocalDate> datas);
    List<VideoAula> findByStudioOrderByDataUploadDesc(Studio studio);
    List<VideoAula> findByDataBefore(LocalDate data);
    void deleteByTurma(Turma turma);
}
