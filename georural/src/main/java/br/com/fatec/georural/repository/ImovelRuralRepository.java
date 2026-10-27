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

    // GeoJSON da Reserva Legal do imovel
    @Query(value = """
        SELECT SDO_UTIL.TO_GEOJSON(
            (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(rl.rle_geometria, 0.5))
             FROM RESERVA_LEGAL rl
             WHERE rl.imo_id = :id)
        ) FROM DUAL
        """, nativeQuery = true)
    Object buscarReservaLegalGeoJson(@Param("id") Long id);
}