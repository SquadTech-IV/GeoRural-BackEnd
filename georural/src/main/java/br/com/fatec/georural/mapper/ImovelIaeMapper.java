package br.com.fatec.georural.mapper;

import org.springframework.stereotype.Component;

import br.com.fatec.georural.dto.response.IaeResultadoResponse;
import br.com.fatec.georural.dto.response.ImovelIaeGeoJsonResponse;
import br.com.fatec.georural.dto.response.ImovelIaeProperties;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.ResultadoIndicador;

@Component
public class ImovelIaeMapper {

    public ImovelIaeGeoJsonResponse toGeoJson(ImovelRural imovel, String geometriaGeoJson,
                                              ResultadoIndicador resultado) {
        ImovelIaeProperties properties = new ImovelIaeProperties(
                imovel.getCodigoCar(),
                imovel.getAreaTotal(),
                imovel.getMunicipio() != null ? imovel.getMunicipio().getNome() : null,
                toIaeResultado(resultado));

        return new ImovelIaeGeoJsonResponse("Feature", geometriaGeoJson, properties);
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
}