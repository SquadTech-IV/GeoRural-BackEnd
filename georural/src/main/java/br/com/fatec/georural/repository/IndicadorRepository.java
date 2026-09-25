package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Indicador;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndicadorRepository extends JpaRepository<Indicador, Long> {
}
