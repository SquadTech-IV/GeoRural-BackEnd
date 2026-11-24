package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.imovel.ImovelIndicadoresResponse;
import br.com.fatec.georural.dto.response.imovel.IndicadorVigenteResponse;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import org.springframework.stereotype.Service;

import java.sql.Clob;
import java.util.List;

@Service
public class ImovelIndicadoresService {

    private final ImovelRuralRepository imovelRepository;
    private final ResultadoIndicadorRepository resultadoRepository;
    public ImovelIndicadoresService(ImovelRuralRepository imovelRepository,
                                    ResultadoIndicadorRepository resultadoRepository) {
        this.imovelRepository = imovelRepository;
        this.resultadoRepository = resultadoRepository;
    }

    public ImovelIndicadoresResponse detalharPorCar(String codigoCar) {
        if (codigoCar == null || codigoCar.isBlank()) {
            throw new IllegalArgumentException("Codigo do CAR nao pode ser vazio");
        }

        ImovelRural imovel = imovelRepository.findByCodigoCar(codigoCar)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Imovel nao encontrado para o CAR: " + codigoCar));

        String geometria = clobParaString(imovelRepository.buscarGeometriaGeoJson(imovel.getId()));

        List<IndicadorVigenteResponse> indicadores = listarIndicadoresVigentes(imovel.getId(), true);

        return montar(imovel, geometria, indicadores);
    }

    // todos os imoveis para pintar o mapa
    public List<ImovelIndicadoresResponse> listarMapa() {
        return imovelRepository.findAll().stream()
                .map(this::toItemMapa)
                .toList();
    }

    private ImovelIndicadoresResponse toItemMapa(ImovelRural imovel) {
        String geometria = clobParaString(imovelRepository.buscarGeometriaGeoJson(imovel.getId()));
        List<IndicadorVigenteResponse> indicadores = listarIndicadoresVigentes(imovel.getId(), false);
        return montar(imovel, geometria, indicadores);
    }

    // So os indicadores que este imovel de fato tem -- nunca os sete com vazio.
    // ISAP e IAPP saem daqui tambem: o PKG_PROJECAO copia os dois para a
    // RESULTADO_INDICADOR no fim do CALCULO, entao nao ha o que somar das
    // tabelas proprias deles (era de onde vinha a duplicata).
    private List<IndicadorVigenteResponse> listarIndicadoresVigentes(Long imovelId,
                                                                     boolean comFeatures) {
        return resultadoRepository.buscarVigentesDoImovel(imovelId).stream()
                .map(r -> toIndicador(r, comFeatures))
                .toList();
    }

    private ImovelIndicadoresResponse montar(ImovelRural imovel, String geometria,
                                             List<IndicadorVigenteResponse> indicadores) {
        return new ImovelIndicadoresResponse(
                imovel.getId(),
                imovel.getCodigoCar(),
                imovel.getMunicipio() != null ? imovel.getMunicipio().getNome() : null,
                imovel.getAreaTotal(),
                geometria,
                indicadores);
    }

    private IndicadorVigenteResponse toIndicador(ResultadoIndicador r, boolean comFeatures) {
        var indicador = r.getIndicador();
        String versaoRegra = r.getRegraCalculo() != null ? r.getRegraCalculo().getVersao() : null;
        return new IndicadorVigenteResponse(
                indicador.getSigla(),
                indicador.getNome(),
                indicador.getUnidade(),
                r.getSituacao(),
                r.getValorPercentual(),
                r.getValorHectares(),
                r.getValorAbsoluto(),
                versaoRegra,
                comFeatures ? r.getFeatures() : null);
    }

    // converte o CLOB do TO_GEOJSON em texto
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
                throw new RuntimeException("Falha ao ler a geometria (CLOB)", e);
            }
        }
        return valor.toString();
    }

}