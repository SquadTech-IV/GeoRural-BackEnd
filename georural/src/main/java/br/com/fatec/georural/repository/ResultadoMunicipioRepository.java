package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.ResultadoMunicipio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResultadoMunicipioRepository extends JpaRepository<ResultadoMunicipio, Long> {
    Optional<ResultadoMunicipio> findByMunicipioIdAndIndicadorAndVigente(Long municipioId, Indicador indicador, String vigente);
}
