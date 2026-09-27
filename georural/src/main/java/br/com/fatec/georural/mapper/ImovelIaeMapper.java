package br.com.fatec.georural.mapper;

import java.io.IOException;
import java.io.StringWriter;

import org.geotools.geojson.geom.GeometryJSON;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.fatec.georural.dto.response.IaeResultadoResponse;
import br.com.fatec.georural.dto.response.ImovelIaeGeoJsonResponse;
import br.com.fatec.georural.dto.response.ImovelIaeProperties;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.ResultadoIndicador;

@Component
public class ImovelIaeMapper {

    private static final GeometryJSON GEOMETRY_JSON = new GeometryJSON(7);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public ImovelIaeGeoJsonResponse toGeoJson(ImovelRural imovel, ResultadoIndicador resultado) {
        ImovelIaeProperties properties = new ImovelIaeProperties(
                imovel.getCodigoCar(),
                imovel.getAreaTotal(),
                imovel.getMunicipio() != null ? imovel.getMunicipio().getNome() : null,
                toIaeResultado(resultado));

        return new ImovelIaeGeoJsonResponse(
                "Feature",
                geometryToJsonNode(imovel.getGeometria()),
                properties);
    }

    private IaeResultadoResponse toIaeResultado(ResultadoIndicador resultado) {
        if (resultado == null) {
            return null;
        }
        return new IaeResultadoResponse(
                resultado.getIndicador().getSigla(),
                resultado.getValorPercentual(),
                resultado.getValorHectares(),
                resultado.getValorAbsoluto(),
                resultado.getDataCalculo());
    }

    private JsonNode geometryToJsonNode(Geometry geometria) {
        if (geometria == null) {
            return null;
        }
        try (StringWriter writer = new StringWriter()) {
            GEOMETRY_JSON.write(geometria, writer);
            return OBJECT_MAPPER.readTree(writer.toString());
        } catch (IOException e) {
            throw new RuntimeException("Falha ao converter geometria do imovel para GeoJSON", e);
        }
    }
}