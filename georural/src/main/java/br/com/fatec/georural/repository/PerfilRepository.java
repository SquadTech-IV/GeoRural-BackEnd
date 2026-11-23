package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Perfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PerfilRepository extends JpaRepository<Perfil, Long> {

    Optional<Perfil> findByFuncao(String funcao);
    Optional<Perfil> findByFuncaoIgnoreCase(String funcao);
}