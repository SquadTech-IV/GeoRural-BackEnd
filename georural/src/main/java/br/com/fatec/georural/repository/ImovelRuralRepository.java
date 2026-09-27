package br.com.fatec.georural.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.com.fatec.georural.entity.ImovelRural;

public interface ImovelRuralRepository extends JpaRepository<ImovelRural, Long> {

    Optional<ImovelRural> findByCodigoCar(String codigoCar);

    // Oracle Spatial qeu serve para  converte a geometria em GeoJSON (texto)
    @Query(value = "SELECT SDO_UTIL.TO_GEOJSON(i.imo_geometria) " +
            "FROM imovel_rural i WHERE i.imo_codigo_car = :car", nativeQuery = true)
    String buscarGeometriaGeoJson(@Param("car") String codigoCar);
}