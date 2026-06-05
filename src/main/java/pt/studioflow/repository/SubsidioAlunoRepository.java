package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Studio;
import pt.studioflow.model.SubsidioAluno;

import java.time.LocalDate;
import java.util.List;

public interface SubsidioAlunoRepository extends JpaRepository<SubsidioAluno, Long> {
    List<SubsidioAluno> findByStudioOrderByDataRenovacaoAsc(Studio studio);
    List<SubsidioAluno> findByAlunoAndAtivo(Aluno aluno, boolean ativo);
    List<SubsidioAluno> findByStudioAndAtivoAndDataRenovacaoBefore(Studio studio, boolean ativo, LocalDate data);
}
