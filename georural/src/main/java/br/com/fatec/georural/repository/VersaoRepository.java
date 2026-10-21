package br.com.fatec.georural.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.Versao;

public interface VersaoRepository extends JpaRepository<Versao, Long> {

    Optional<Versao> findTopByConjuntoOrderByNumeroDesc(ConjuntoDados conjunto);

    /** Versoes de um conjunto em uma dada situacao. */
    List<Versao> findByConjunto_IdAndSituacao(Long conjuntoId, String situacao);
}