package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.entity.Indicador;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegraCalculoRepository extends JpaRepository<RegraCalculo, Long> {

    Optional<RegraCalculo> findByIndicadorAndVigente(Indicador indicador, String vigente);

    long countByIndicador(Indicador indicador);

    long countByIndicadorAndVigente(Indicador indicador, String vigente);

    List<RegraCalculo> findByIndicadorOrderByDataCriacaoDesc(Indicador indicador);
}