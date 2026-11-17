package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @Query("SELECT u FROM Usuario u JOIN FETCH u.perfil WHERE u.keycloakId = :keycloakId")
    Optional<Usuario> findByKeycloakIdComPerfil(@Param("keycloakId") String keycloakId);

    @Query("SELECT u FROM Usuario u JOIN FETCH u.perfil WHERE LOWER(u.email) = LOWER(:email)")
    Optional<Usuario> findByEmailComPerfil(@Param("email") String email);
}