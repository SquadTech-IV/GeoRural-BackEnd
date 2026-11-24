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
                .map(r -> toIndicador(r, imovelId, comFeatures))
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

    private IndicadorVigenteResponse toIndicador(ResultadoIndicador r, Long imovelId,
                                                 boolean comFeatures) {
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
                comFeatures ? geometriaDoIndicador(r, imovelId) : null,
                r.getDetalhes());
    }

    // Geometria do indicador para desenhar no mapa (US#05).
    //
    // Antes isto lia r.getFeatures(), que e a coluna rin_features -- declarada no
    // schema e no DTO, e nunca escrita por lugar nenhum do sistema. O resultado era
    // que so o IAPP aparecia no mapa, porque ele tem endpoint proprio: os outros
    // seis chegavam com geometria nula e o front descartava a camada em silencio.
    //
    // A geometria vem da camada que originou o indicador, e NAO e filtrada pela
    // versao do resultado: a versao do resultado e a do calculo, a das linhas da
    // camada e a do tratamento, e as duas so coincidem por acaso. O escopo de cada
    // consulta esta explicado no ImovelRuralRepository.
    //
    // So e chamado quando comFeatures e true, ou seja no detalhe de um imovel. A
    // listagem do mapa (listarMapa) continua sem geometria de indicador: seriam sete
    // consultas espaciais por imovel, para desenhar camada que ninguem pediu ainda.
    private String geometriaDoIndicador(ResultadoIndicador r, Long imovelId) {
        String sigla = r.getIndicador().getSigla();
        sigla = sigla == null ? "" : sigla.trim().toUpperCase();

        Object geometria = switch (sigla) {
            case "IRL" -> imovelRepository.geometriaReservaLegalGeoJson(imovelId);
            case "IAPP" -> imovelRepository.geometriaAppGeoJson(imovelId);
            case "ICV" -> imovelRepository.geometriaVegetacaoGeoJson(imovelId);
            case "IAE" -> imovelRepository.geometriaEmbargoGeoJson(imovelId);
            case "ISAP" -> imovelRepository.geometriaAreaProtegidaGeoJson(imovelId);
            case "IDESMAT" -> imovelRepository.geometriaDesmatamentoGeoJson(imovelId);
            case "IFC" -> imovelRepository.geometriaFocoCalorGeoJson(imovelId, r.getId());
            default -> null;
        };

        return clobParaString(geometria);
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