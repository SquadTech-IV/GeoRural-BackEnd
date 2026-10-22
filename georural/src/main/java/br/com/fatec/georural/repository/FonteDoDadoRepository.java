package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.FonteDoDado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FonteDoDadoRepository
        extends JpaRepository<FonteDoDado, Long> {

    Optional<FonteDoDado> findByNome(String nome);

    Optional<FonteDoDado> findByNomeIgnoreCase(String nome);

    Optional<FonteDoDado> findBySiglaIgnoreCase(String sigla);
}