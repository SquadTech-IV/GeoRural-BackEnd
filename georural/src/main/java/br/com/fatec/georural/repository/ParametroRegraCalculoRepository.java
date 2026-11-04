package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ParametroRegraCalculo;
import br.com.fatec.georural.entity.RegraCalculo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroRegraCalculoRepository extends JpaRepository<ParametroRegraCalculo, Long> {

    List<ParametroRegraCalculo> findByRegraCalculo(RegraCalculo regraCalculo);

    List<ParametroRegraCalculo> findByRegraCalculo_IdOrderByNomeAsc(Long regraCalculoId);

    Optional<ParametroRegraCalculo> findByRegraCalculo_IdAndNome(Long regraCalculoId, String nome);
}
