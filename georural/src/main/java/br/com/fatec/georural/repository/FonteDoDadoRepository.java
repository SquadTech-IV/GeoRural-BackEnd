<<<<<<< HEAD
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.FonteDoDado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FonteDoDadoRepository
        extends JpaRepository<FonteDoDado, Long> {

    Optional<FonteDoDado> findByNome(String nome);

    Optional<FonteDoDado> findByNomeIgnoreCase(String nome);

    Optional<FonteDoDado> findBySiglaIgnoreCase(String sigla);
=======
package br.com.fatec.georural.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fatec.georural.entity.FonteDoDado;

public interface FonteDoDadoRepository extends JpaRepository<FonteDoDado, Long> {
    Optional<FonteDoDado> findByNome(String nome);
>>>>>>> 00e592e (chore(US#07): Commit em devcontainer)
}