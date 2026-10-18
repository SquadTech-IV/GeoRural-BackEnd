package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ArquivoBruto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArquivoBrutoRepository extends JpaRepository<ArquivoBruto, Long> {

    Optional<ArquivoBruto> findByHash(String hash);

    /** ja existe um arquivo com esse nome nesse conjunto?. */
    boolean existsByConjunto_IdAndNome(Long conjuntoId, String nome);
}