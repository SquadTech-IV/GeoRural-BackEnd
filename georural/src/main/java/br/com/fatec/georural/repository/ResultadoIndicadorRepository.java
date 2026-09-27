package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.entity.Indicador;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultadoIndicadorRepository extends JpaRepository<ResultadoIndicador, Long> {
    Optional <ResultadoIndicador> findTopByImovelIdAndIndicadorOrderByDataCalculoDesc(Long imovelId, Indicador indicador);
}
