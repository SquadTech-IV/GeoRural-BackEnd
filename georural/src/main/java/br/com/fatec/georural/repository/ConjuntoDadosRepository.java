package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ConjuntoDados;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConjuntoDadosRepository
        extends JpaRepository<ConjuntoDados, Long> {

    Optional<ConjuntoDados> findByNome(String nome);

    List<ConjuntoDados> findByFonteIdOrderByNomeAsc(Long fonteId);

    Optional<ConjuntoDados> findByFonteIdAndNomeIgnoreCase(
        Long fonteId,
        String nome
    );
}