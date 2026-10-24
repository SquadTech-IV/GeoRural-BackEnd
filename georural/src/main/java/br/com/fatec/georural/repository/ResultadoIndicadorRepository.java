package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.Versao;

import java.util.Collection;
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

    @Query("""
       SELECT DISTINCT i
       FROM ResultadoIndicador r
       JOIN r.indicador i
       WHERE r.execucao.id IN (:execucaoIds)
          OR r.execucao.id IN (
               SELECT ent.execucao.id FROM EntradaExecucao ent
               WHERE ent.versao.id IN (
                   SELECT ex.versaoGerada.id FROM ExecucaoPipeline ex
                   WHERE ex.id IN (:execucaoIds) AND ex.versaoGerada IS NOT NULL))
       ORDER BY i.sigla
       """)
    List<Indicador> buscarIndicadoresGeradosPorExecucoes(@Param("execucaoIds") Collection<Long> execucaoIds);

    @Query("""
       SELECT r FROM ResultadoIndicador r
       JOIN FETCH r.indicador i
       JOIN FETCH r.imovel im
       JOIN FETCH r.versao v
       WHERE v.situacao IN ('VIGENTE', 'ANTERIOR')
         AND (:indicador = '' OR i.sigla = :indicador)
         AND LOWER(im.codigoCar) LIKE :busca
       ORDER BY r.dataCalculo DESC
       """)
    List<ResultadoIndicador> buscarPublicados(@Param("indicador") String indicador,
                                              @Param("busca") String busca);
    @Query("""
           SELECT DISTINCT r.versao.id, i.sigla, i.nome, COALESCE(rc.descricao, rc.versao)
           FROM ResultadoIndicador r
           JOIN r.indicador i
           LEFT JOIN r.regraCalculo rc
           WHERE r.versao.id IN :versaoIds
           """)
    List<Object[]> listarIndicadoresPorVersoes(@Param("versaoIds") Collection<Long> versaoIds);
}