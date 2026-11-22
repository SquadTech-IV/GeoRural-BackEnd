package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ResultadoIsapResumo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResultadoIsapResumoRepository extends JpaRepository<ResultadoIsapResumo, Long> {

    @Query("""
        SELECT r FROM ResultadoIsapResumo r
          JOIN FETCH r.imovel i
          LEFT JOIN FETCH i.municipio m
          JOIN FETCH r.versao v
         WHERE i.id = :imovelId AND r.vigente = 'S'
         ORDER BY r.dataCalculo DESC
        """)
    List<ResultadoIsapResumo> findVigentesPorImovelId(@Param("imovelId") Long imovelId);

    @Query("""
        SELECT r FROM ResultadoIsapResumo r
          JOIN FETCH r.imovel i
          LEFT JOIN FETCH i.municipio m
          JOIN FETCH r.versao v
         WHERE UPPER(i.codigoCar) = UPPER(:codigoCar) AND r.vigente = 'S'
         ORDER BY r.dataCalculo DESC
        """)
    List<ResultadoIsapResumo> findVigentesPorCodigoCar(@Param("codigoCar") String codigoCar);

    @Query("""
        SELECT r FROM ResultadoIsapResumo r
          JOIN FETCH r.imovel i
          LEFT JOIN FETCH i.municipio m
          JOIN FETCH r.versao v
         WHERE i.id = :imovelId
         ORDER BY v.numero DESC, r.dataCalculo DESC
        """)
    List<ResultadoIsapResumo> findHistoricoPorImovelId(@Param("imovelId") Long imovelId);

    @Query(value = """
        SELECT r FROM ResultadoIsapResumo r
          JOIN FETCH r.imovel i
          LEFT JOIN FETCH i.municipio m
          JOIN FETCH r.versao v
         WHERE r.vigente = 'S'
           AND (:situacao IS NULL OR UPPER(r.situacao) = UPPER(:situacao))
           AND (:municipioId IS NULL OR m.id = :municipioId)
           AND (:busca IS NULL
                OR UPPER(i.codigoCar) LIKE CONCAT('%', UPPER(:busca), '%')
                OR UPPER(COALESCE(i.nome, '')) LIKE CONCAT('%', UPPER(:busca), '%'))
        """,
        countQuery = """
        SELECT COUNT(r) FROM ResultadoIsapResumo r
          JOIN r.imovel i
          LEFT JOIN i.municipio m
         WHERE r.vigente = 'S'
           AND (:situacao IS NULL OR UPPER(r.situacao) = UPPER(:situacao))
           AND (:municipioId IS NULL OR m.id = :municipioId)
           AND (:busca IS NULL
                OR UPPER(i.codigoCar) LIKE CONCAT('%', UPPER(:busca), '%')
                OR UPPER(COALESCE(i.nome, '')) LIKE CONCAT('%', UPPER(:busca), '%'))
        """)
    Page<ResultadoIsapResumo> consultarVigentesPaginado(
            @Param("situacao") String situacao,
            @Param("municipioId") Long municipioId,
            @Param("busca") String busca,
            Pageable pageable);
}