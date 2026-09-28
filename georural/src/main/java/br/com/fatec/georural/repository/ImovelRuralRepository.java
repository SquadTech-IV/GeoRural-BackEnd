package br.com.fatec.georural.repository;

import br.com.fatec.georural.entity.ImovelRural;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImovelRuralRepository extends JpaRepository<ImovelRural, Long> {


    Optional<ImovelRural> findByCodigoCar(String codigoCar);

    @Query(value = "SELECT SDO_UTIL.TO_GEOJSON(i.imo_geometria) FROM imovel_rural i WHERE i.imo_id = :id",
            nativeQuery = true)
    Object buscarGeometriaGeoJson(@Param("id") Long id);

    @Query(value = """
        SELECT
            SDO_GEOM.SDO_AREA(
                SDO_GEOM.SDO_INTERSECTION(
                    i.imo_geometria,
                    (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(e.emb_geometria, 0.5))
                    FROM EMBARGO e
                    WHERE e.ver_id = i.ver_id
                    AND SDO_RELATE(i.imo_geometria, e.emb_geometria, 'mask=ANYINTERACT') = 'TRUE'),
                    0.5
                ), 0.5
            ) AS areaEmbargadaM2,
            i.imo_area_total AS areaTotalM2
        FROM IMOVEL_RURAL i
        WHERE i.imo_id = :imovelId
        """, nativeQuery = true)
    Object[] calcularAreaEmbargadaAgregada(@Param("imovelId") Long imovelId);

    @Query(value = """
        SELECT
            i.imo_id AS imovelId,
            e.emb_id AS embargoId,
            e.emb_identificador AS identificador,
            SDO_GEOM.SDO_AREA(
                SDO_GEOM.SDO_INTERSECTION(i.imo_geometria, e.emb_geometria, 0.5),
                0.5
            ) AS areaSobrepostaM2,
            i.imo_area_total AS areaTotalM2
        FROM IMOVEL_RURAL i
        JOIN EMBARGO e
            ON e.ver_id = i.ver_id
        WHERE i.imo_id = :imovelId
        AND SDO_RELATE(i.imo_geometria, e.emb_geometria, 'mask=ANYINTERACT') = 'TRUE'
        """, nativeQuery = true)
    List<Object[]> encontrarEmbargosIntersectantes(@Param("imovelId") Long imovelId);
}