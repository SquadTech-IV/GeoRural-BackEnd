package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.Versao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultadoIndicadorRepository extends JpaRepository<ResultadoIndicador, Long> {

    Optional<ResultadoIndicador> findTopByImovelIdAndIndicadorOrderByDataCalculoDesc(Long imovelId, Indicador indicador);

    Optional<ResultadoIndicador> findByImovelIdAndIndicadorAndVersao(Long imovelId, Indicador indicador, Versao versao);

    Optional<ResultadoIndicador> findByImovelIdAndIndicadorAndVigente(Long imovelId, Indicador indicador, String vigente);

    // todos os indicadores vigentes de um imovel
    @Query("""
           SELECT r FROM ResultadoIndicador r
           JOIN FETCH r.indicador
           LEFT JOIN FETCH r.regraCalculo
           WHERE r.imovel.id = :imovelId AND r.vigente = 'S'
           """)
    List<ResultadoIndicador> buscarVigentesDoImovel(@Param("imovelId") Long imovelId);
}