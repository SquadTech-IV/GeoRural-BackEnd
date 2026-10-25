package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ExecucaoPipeline;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExecucaoPipelineRepository extends JpaRepository<ExecucaoPipeline, Long> {

    List<ExecucaoPipeline> findByConjunto_IdOrderByInicioAsc(Long conjuntoId);

    List<ExecucaoPipeline> findByDagRunIdOrderByInicioAsc(String dagRunId);

    List<ExecucaoPipeline> findByArquivoBruto_IdOrderByInicioDesc(Long arquivoBrutoId);

    List<ExecucaoPipeline> findByArquivoBruto_IdAndEtapaOrderByInicioDesc(Long arquivoBrutoId, String etapa);

    @Query("select e from ExecucaoPipeline e where (:conjuntoId is null or e.conjunto.id = :conjuntoId)")
    Page<ExecucaoPipeline> listarPorConjuntoPaginado(@Param("conjuntoId") Long conjuntoId, Pageable pageable);
}