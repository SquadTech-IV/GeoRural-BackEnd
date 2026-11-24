package br.com.fatec.georural.repository;

import br.com.fatec.georural.dto.response.indicador.ResultadoIndicadorResponse;
import br.com.fatec.georural.entity.ImovelRural;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImovelRuralRepository extends JpaRepository<ImovelRural, Long> {

    Optional<ImovelRural> findByCodigoCar(String codigoCar);

    List<ImovelRural> findByCodigoCarIn(Collection<String> codigos);

    List<ImovelRural> findByArquivoBruto_Id(Long arquivoBrutoId);

    @Query(value = "SELECT SDO_UTIL.TO_GEOJSON(i.imo_geometria) FROM imovel_rural i WHERE i.imo_id = :id",
            nativeQuery = true)
    Object buscarGeometriaGeoJson(@Param("id") Long id);

    @Query(value = """
        SELECT SDO_UTIL.TO_GEOJSON(
            (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(e.emb_geometria, 0.5))
             FROM EMBARGO e
             WHERE SDO_RELATE(
                 (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :id),
                 e.emb_geometria, 'mask=ANYINTERACT') = 'TRUE')
        ) FROM DUAL
        """, nativeQuery = true)
    Object buscarEmbargosGeoJson(@Param("id") Long id);

    @Query(value = """
        SELECT
            CAST(SDO_GEOM.SDO_AREA(
                SDO_GEOM.SDO_INTERSECTION(
                    i.imo_geometria,
                    (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(e.emb_geometria, 0.5))
                    FROM EMBARGO e
                    WHERE SDO_RELATE(i.imo_geometria, e.emb_geometria, 'mask=ANYINTERACT') = 'TRUE'),
                    0.5
                ), 0.5
            ) AS NUMBER(18,4)) AS areaEmbargadaM2,
            i.imo_area_total AS areaTotalHa
        FROM IMOVEL_RURAL i
        WHERE i.imo_id = :imovelId
        """, nativeQuery = true)
    List<Object[]> calcularAreaEmbargadaAgregada(@Param("imovelId") Long imovelId);

    @Query(value = """
        SELECT
            i.imo_id AS imovelId,
            e.emb_id AS embargoId,
            e.emb_identificador AS identificador,
            SDO_GEOM.SDO_AREA(
                SDO_GEOM.SDO_INTERSECTION(i.imo_geometria, e.emb_geometria, 0.5),
                0.5
            ) AS areaSobrepostaM2
        FROM IMOVEL_RURAL i
        JOIN EMBARGO e
            ON SDO_RELATE(i.imo_geometria, e.emb_geometria, 'mask=ANYINTERACT') = 'TRUE'
        WHERE i.imo_id = :imovelId
        """, nativeQuery = true)
    List<Object[]> encontrarEmbargosIntersectantes(@Param("imovelId") Long imovelId);

    // espacial do IRL (Reserva Legal)
    @Query(value = """
        SELECT
            SDO_GEOM.SDO_AREA(
                SDO_GEOM.SDO_INTERSECTION(
                    i.imo_geometria,
                    (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(rl.rle_geometria, 0.5))
                     FROM RESERVA_LEGAL rl
                     WHERE rl.imo_id = i.imo_id),
                    0.5
                ), 0.5
            ) AS areaReservaLegalM2,
            i.imo_area_total AS areaTotalHa
        FROM IMOVEL_RURAL i
        WHERE i.imo_id = :imovelId
        """, nativeQuery = true)
    Object[] calcularAreaReservaLegalAgregada(@Param("imovelId") Long imovelId);

    // ------------------------------------------------------------------
    // Geometria de cada indicador para o mapa (US#05).
    //
    // A coluna rin_features existe no schema e no DTO desde o inicio, com CHECK de
    // JSON, mas NENHUM lugar do sistema escreve nela. Era por isso que o mapa so
    // desenhava o IAPP, que tem endpoint proprio: os outros seis chegavam ao front
    // com geometria nula e o prepararCamadasIndicadores descartava em silencio.
    //
    // Nenhuma destas consultas filtra pela versao do resultado, e isso e de
    // proposito: a versao do resultado e a versao do CALCULO, e a versao das linhas
    // da camada e a versao do TRATAMENTO que carregou o arquivo. Sao coisas
    // diferentes. Medido no banco: o ICV de tres imoveis foi calculado na versao
    // 183, enquanto a vegetacao deles esta nas versoes 104, 21 e 183 -- um arquivo
    // por imovel. Casar as duas so acerta por coincidencia.
    //
    // RESERVA_LEGAL e APP apagam por imo_id, entao existe uma geracao por vez e
    // basta o imo_id. As camadas de referencia podem ter varias geracoes
    // sobrepostas, entao vale a mais recente que toca o imovel (MAX(ver_id)), para
    // o mapa nao empilhar o antes e o depois de um reprocessamento.
    //
    // As camadas de referencia vem recortadas no perimetro do imovel com
    // PKG_GEO.intersecao. Sem o recorte um embargo de 46 mil hectares entraria
    // inteiro e um imovel de 200 ha viraria um ponto na tela.

    // IRL
    @Query(value = """
        SELECT SDO_UTIL.TO_GEOJSON(
                   (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(rl.rle_geometria, 0.5))
                      FROM RESERVA_LEGAL rl
                     WHERE rl.imo_id = :imovelId)
               ) FROM DUAL
        """, nativeQuery = true)
    Object geometriaReservaLegalGeoJson(@Param("imovelId") Long imovelId);

    // IAPP
    @Query(value = """
        SELECT SDO_UTIL.TO_GEOJSON(
                   (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(a.app_geometria, 0.5))
                      FROM APP a
                     WHERE a.imo_id = :imovelId)
               ) FROM DUAL
        """, nativeQuery = true)
    Object geometriaAppGeoJson(@Param("imovelId") Long imovelId);

    // ICV
    @Query(value = """
        SELECT SDO_UTIL.TO_GEOJSON(
                   PKG_GEO.intersecao(
                       (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(v.veg_geometria, 0.5))
                          FROM VEGETACAO_NATIVA v
                         WHERE SDO_RELATE(v.veg_geometria,
                                 (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId),
                                 'mask=ANYINTERACT') = 'TRUE'
                           AND v.ver_id = (SELECT MAX(v2.ver_id)
                                               FROM VEGETACAO_NATIVA v2
                                              WHERE SDO_RELATE(v2.veg_geometria,
                                                      (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId),
                                                      'mask=ANYINTERACT') = 'TRUE')),
                       (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId)
                   )
               ) FROM DUAL
        """, nativeQuery = true)
    Object geometriaVegetacaoGeoJson(@Param("imovelId") Long imovelId);

    // IAE
    @Query(value = """
        SELECT SDO_UTIL.TO_GEOJSON(
                   PKG_GEO.intersecao(
                       (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(e.emb_geometria, 0.5))
                          FROM EMBARGO e
                         WHERE SDO_RELATE(e.emb_geometria,
                                 (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId),
                                 'mask=ANYINTERACT') = 'TRUE'
                           AND e.ver_id = (SELECT MAX(e2.ver_id)
                                               FROM EMBARGO e2
                                              WHERE SDO_RELATE(e2.emb_geometria,
                                                      (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId),
                                                      'mask=ANYINTERACT') = 'TRUE')),
                       (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId)
                   )
               ) FROM DUAL
        """, nativeQuery = true)
    Object geometriaEmbargoGeoJson(@Param("imovelId") Long imovelId);

    // ISAP
    @Query(value = """
        SELECT SDO_UTIL.TO_GEOJSON(
                   PKG_GEO.intersecao(
                       (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(ap.apr_geometria, 0.5))
                          FROM AREA_PROTEGIDA ap
                         WHERE SDO_RELATE(ap.apr_geometria,
                                 (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId),
                                 'mask=ANYINTERACT') = 'TRUE'
                           AND ap.ver_id = (SELECT MAX(ap2.ver_id)
                                               FROM AREA_PROTEGIDA ap2
                                              WHERE SDO_RELATE(ap2.apr_geometria,
                                                      (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId),
                                                      'mask=ANYINTERACT') = 'TRUE')),
                       (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId)
                   )
               ) FROM DUAL
        """, nativeQuery = true)
    Object geometriaAreaProtegidaGeoJson(@Param("imovelId") Long imovelId);

    // IDESMAT
    @Query(value = """
        SELECT SDO_UTIL.TO_GEOJSON(
                   PKG_GEO.intersecao(
                       (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(d.des_geometria, 0.5))
                          FROM DESMATAMENTO d
                         WHERE SDO_RELATE(d.des_geometria,
                                 (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId),
                                 'mask=ANYINTERACT') = 'TRUE'
                           AND d.ver_id = (SELECT MAX(d2.ver_id)
                                               FROM DESMATAMENTO d2
                                              WHERE SDO_RELATE(d2.des_geometria,
                                                      (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId),
                                                      'mask=ANYINTERACT') = 'TRUE')),
                       (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId)
                   )
               ) FROM DUAL
        """, nativeQuery = true)
    Object geometriaDesmatamentoGeoJson(@Param("imovelId") Long imovelId);

    // IFC: o calcular_ifc conta por periodo de data, nao por versao, e grava o
    // periodo em rin_detalhes -- a camada le o mesmo periodo dali, para o mapa
    // mostrar exatamente os focos que entraram na conta
    @Query(value = """
        SELECT SDO_UTIL.TO_GEOJSON(
                   (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(f.foc_geometria, 0.5))
                      FROM FOCO_CALOR f, RESULTADO_INDICADOR r
                     WHERE r.rin_id = :resultadoId
                       AND f.foc_geometria IS NOT NULL
                       AND f.foc_data BETWEEN
                             TO_DATE(JSON_VALUE(r.rin_detalhes, '$.periodo_inicio'), 'YYYY-MM-DD')
                         AND TO_DATE(JSON_VALUE(r.rin_detalhes, '$.periodo_fim'), 'YYYY-MM-DD')
                       AND SDO_RELATE(f.foc_geometria,
                             (SELECT i.imo_geometria FROM IMOVEL_RURAL i WHERE i.imo_id = :imovelId),
                             'mask=ANYINTERACT') = 'TRUE')
               ) FROM DUAL
        """, nativeQuery = true)
    Object geometriaFocoCalorGeoJson(@Param("imovelId") Long imovelId,
                                     @Param("resultadoId") Long resultadoId);
}