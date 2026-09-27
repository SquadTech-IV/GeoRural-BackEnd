package br.com.fatec.georural.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fatec.georural.entity.ConjuntoDados;

public interface ConjuntoDadosRepository extends JpaRepository<ConjuntoDados, Long> {
    Optional<ConjuntoDados> findByNome(String nome);
}