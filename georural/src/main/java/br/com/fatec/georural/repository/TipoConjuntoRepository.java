<<<<<<< HEAD
package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.TipoConjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TipoConjuntoRepository extends JpaRepository<TipoConjunto, Long> {
    Optional<TipoConjunto> findByCodigoIgnoreCase(String codigo);
    Optional<TipoConjunto> findByNomeIgnoreCase(String nome);
}
