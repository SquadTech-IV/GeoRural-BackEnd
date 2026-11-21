package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    @Query("SELECT u FROM Usuario u JOIN FETCH u.perfil WHERE u.keycloakId = :keycloakId")
    Optional<Usuario> findByKeycloakIdComPerfil(@Param("keycloakId") String keycloakId);

    @Query("SELECT u FROM Usuario u JOIN FETCH u.perfil WHERE LOWER(u.email) = LOWER(:email)")
    Optional<Usuario> findByEmailComPerfil(@Param("email") String email);

    @Query("SELECT u FROM Usuario u JOIN FETCH u.perfil WHERE u.id = :id")
    Optional<Usuario> findByIdComPerfil(@Param("id") Long id);

    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.perfil.funcao = 'ADMIN' AND u.status = 'S'")
    List<Usuario> findAdminsAtivosComLock();

    @Query(
            value = """
                    SELECT u FROM Usuario u
                      JOIN FETCH u.perfil p
                     WHERE (:busca IS NULL
                            OR LOWER(u.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
                            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :busca, '%')))
                       AND (:perfilId IS NULL OR p.id = :perfilId)
                       AND (:status IS NULL OR UPPER(u.status) = UPPER(:status))
                    """,
            countQuery = """
                    SELECT COUNT(u) FROM Usuario u
                      JOIN u.perfil p
                     WHERE (:busca IS NULL
                            OR LOWER(u.nome) LIKE LOWER(CONCAT('%', :busca, '%'))
                            OR LOWER(u.email) LIKE LOWER(CONCAT('%', :busca, '%')))
                       AND (:perfilId IS NULL OR p.id = :perfilId)
                       AND (:status IS NULL OR UPPER(u.status) = UPPER(:status))
                    """
    )
    Page<Usuario> consultarUsuariosPaginado(
            @Param("busca") String busca,
            @Param("perfilId") Long perfilId,
            @Param("status") String status,
            Pageable pageable
    );
}