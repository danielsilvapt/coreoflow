package pt.studioflow.model;


import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Username único dentro do mesmo estúdio */
    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    /**
     * Roles: ADMIN, PROF, ALUNO, DELEG (por estúdio)
     *        SUPERADMIN (acesso à plataforma toda - studio=null)
     */
    @Column(nullable = false)
    private String role;

    @Column(name = "first_name")
    private String firstName;

    @Column(nullable = false)
    private String email;

    /** Estúdio a que pertence. Null = SUPERADMIN da plataforma. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "studio_id")
    private Studio studio;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Studio getStudio() { return studio; }
    public void setStudio(Studio studio) { this.studio = studio; }
}
