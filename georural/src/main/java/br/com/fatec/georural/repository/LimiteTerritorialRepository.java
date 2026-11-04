package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.LimiteTerritorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LimiteTerritorialRepository extends JpaRepository<LimiteTerritorial, Long> {

    Optional<LimiteTerritorial> findByTipoAndCodigo(String tipo, String codigo);

    boolean existsByTipoAndCodigo(String tipo, String codigo);
}
