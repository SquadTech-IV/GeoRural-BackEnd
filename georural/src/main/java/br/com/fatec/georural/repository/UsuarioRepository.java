package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}