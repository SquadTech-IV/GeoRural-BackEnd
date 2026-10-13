package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ParametroRegraCalculo;
import br.com.fatec.georural.entity.RegraCalculo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroRegraCalculoRepository extends JpaRepository<ParametroRegraCalculo, Long> {

    List<ParametroRegraCalculo> findByRegraCalculo(RegraCalculo regraCalculo);
}