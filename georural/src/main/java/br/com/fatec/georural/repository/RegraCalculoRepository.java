package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.entity.Indicador;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegraCalculoRepository extends JpaRepository<RegraCalculo, Long> {

    Optional<RegraCalculo> findByIndicadorAndVigente(Indicador indicador, String vigente);

    long countByIndicador(Indicador indicador);

    long countByIndicadorAndVigente(Indicador indicador, String vigente);

    List<RegraCalculo> findByIndicadorOrderByDataCriacaoDesc(Indicador indicador);

    Optional<RegraCalculo> findByIndicadorAndVersao(Indicador indicador, String versao);

    // regra vigente pela sigla do indicador
    Optional<RegraCalculo> findFirstByIndicador_SiglaAndVigenteOrderByDataCriacaoDesc(
            String sigla, String vigente);

    // todas as versoes de regra de um indicador, mais nova primeiro
    List<RegraCalculo> findByIndicador_SiglaOrderByDataCriacaoDesc(String sigla);

    // UPDATE so em rca_vigente: o trigger de imutabilidade dispara em UPDATE OF
    // rca_formula/rca_versao, e o save do JPA mandaria todas as colunas
    @Modifying
    @Query("UPDATE RegraCalculo r SET r.vigente = :vigente WHERE r.id = :id")
    int marcarVigencia(@Param("id") Long id, @Param("vigente") String vigente);
}
