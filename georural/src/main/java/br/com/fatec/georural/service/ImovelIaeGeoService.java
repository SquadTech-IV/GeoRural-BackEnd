package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.imovel.ImovelGeoJsonResponse;
import br.com.fatec.georural.dto.response.imovel.ImovelIaeResumo;
import br.com.fatec.georural.dto.response.imovel.ImovelProperties;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import org.springframework.stereotype.Service;

import java.sql.Clob;

@Service
public class ImovelIaeGeoService {

    private static final String SIGLA_IAE = "IAE";

    private final ImovelRuralRepository imovelRepository;
    private final ResultadoIndicadorRepository resultadoRepository;
    private final IndicadorRepository indicadorRepository;

    public ImovelIaeGeoService(ImovelRuralRepository imovelRepository,
                               ResultadoIndicadorRepository resultadoRepository,
                               IndicadorRepository indicadorRepository) {
        this.imovelRepository = imovelRepository;
        this.resultadoRepository = resultadoRepository;
        this.indicadorRepository = indicadorRepository;
    }

    public ImovelGeoJsonResponse consultarPorCar(String codigoCar) {
        if (codigoCar == null || codigoCar.isBlank()) {
            throw new IllegalArgumentException("Codigo do CAR nao pode ser vazio");
        }

        ImovelRural imovel = imovelRepository.findByCodigoCar(codigoCar)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Imovel nao encontrado para o CAR: " + codigoCar));


        String geometria = clobParaString(imovelRepository.buscarGeometriaGeoJson(imovel.getId()));


        ImovelIaeResumo iae = indicadorRepository.findBySigla(SIGLA_IAE)
                .flatMap(ind -> resultadoRepository
                        .findTopByImovelIdAndIndicadorOrderByDataCalculoDesc(imovel.getId(), ind))
                .map(this::toIaeResumo)
                .orElse(null);

        ImovelProperties props = new ImovelProperties(
                imovel.getCodigoCar(),
                imovel.getAreaTotal(),
                imovel.getMunicipio() != null ? imovel.getMunicipio().getNome() : null,
                iae);

        return new ImovelGeoJsonResponse("Feature", geometria, props);
    }
    
    private String clobParaString(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof String s) {
            return s;
        }
        if (valor instanceof Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("Falha ao ler a geometria (CLOB) do imovel", e);
            }
        }

        return valor.toString();
    }

    private ImovelIaeResumo toIaeResumo(ResultadoIndicador r) {
        return new ImovelIaeResumo(
                r.getValorPercentual(),
                r.getValorHectares(),
                r.getValorAbsoluto(),
                r.getDataCalculo());
    }
}