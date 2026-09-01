package pt.studioflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pt.studioflow.model.Studio;
import pt.studioflow.model.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Login: encontrar utilizador pelo username dentro de um studio específico */
    Optional<User> findByUsernameAndStudio(String username, Studio studio);

    /** Para o SUPERADMIN (sem studio) */
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.studio IS NULL")
    Optional<User> findSuperAdminByUsername(@Param("username") String username);

    /** Procura por username em qualquer studio (usado internamente) */
    List<User> findAllByUsername(String username);

    /** Todos os utilizadores de um studio */
    List<User> findAllByStudio(Studio studio);

    /** Procurar por email dentro de um studio */
    Optional<User> findByEmailAndStudio(String email, Studio studio);

    /** Compatibilidade com código legado - procura por username (usado antes de ter studio context) */
    Optional<User> findByUsername(String username);

    /**
     * Resolve o utilizador a partir do nome de principal do Spring Security.
     * Para utilizadores de um estúdio esse nome vem no formato "slug:username";
     * este método faz o strip do prefixo do tenant e procura dentro do studio
     * ativo na sessão. Deve ser usado por todas as views em vez de
     * {@link #findByUsername(String)} com o auth.getName().
     */
    default Optional<User> findByPrincipalName(String principalName) {
        if (principalName == null) {
            return Optional.empty();
        }
        String username = principalName.contains(":")
                ? principalName.split(":", 2)[1]
                : principalName;
        Studio studio = pt.studioflow.config.TenantContext.getCurrentStudio();
        return studio != null
                ? findByUsernameAndStudio(username, studio)
                : findByUsername(username);
    }
}
