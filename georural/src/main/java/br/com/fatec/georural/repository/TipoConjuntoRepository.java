package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.TipoConjunto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoConjuntoRepository extends JpaRepository<TipoConjunto, Long> {

    // tip_codigo e UNIQUE: busca o tipo pelo codigo (ex.: "IMOVEL", "RESERVA_LEGAL", "EMBARGO")
    Optional<TipoConjunto> findByCodigo(String codigo);
}
