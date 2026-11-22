package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.imovel.ImovelIndicadoresResponse;
import br.com.fatec.georural.dto.response.imovel.IndicadorVigenteResponse;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.entity.ResultadoIsapResumo;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import br.com.fatec.georural.repository.ResultadoIsapResumoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Clob;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImovelIndicadoresService {

    private final ImovelRuralRepository imovelRepository;
    private final ResultadoIndicadorRepository resultadoRepository;
    private final ResultadoIsapResumoRepository isapResumoRepository;
    private final IndicadorRepository indicadorRepository;

    public ImovelIndicadoresService(ImovelRuralRepository imovelRepository,
                                    ResultadoIndicadorRepository resultadoRepository) {
        this(imovelRepository, resultadoRepository, null, null);
    }

    @Autowired
    public ImovelIndicadoresService(ImovelRuralRepository imovelRepository,
                                    ResultadoIndicadorRepository resultadoRepository,
                                    ResultadoIsapResumoRepository isapResumoRepository,
                                    IndicadorRepository indicadorRepository) {
        this.imovelRepository = imovelRepository;
        this.resultadoRepository = resultadoRepository;
        this.isapResumoRepository = isapResumoRepository;
        this.indicadorRepository = indicadorRepository;
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

    private List<IndicadorVigenteResponse> listarIndicadoresVigentes(Long imovelId, boolean comFeatures) {
        List<IndicadorVigenteResponse> lista = new ArrayList<>(
                resultadoRepository.buscarVigentesDoImovel(imovelId).stream()
                        .map(r -> toIndicador(r, comFeatures))
                        .toList());

        if (isapResumoRepository != null) {
            isapResumoRepository.findVigentesPorImovelId(imovelId)
                    .stream()
                    .filter(r -> "TOTAL".equalsIgnoreCase(r.getCategoria()))
                    .findFirst()
                    .ifPresent(isap -> lista.add(toIndicadorIsap(isap)));
        }

        return lista;
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

    private IndicadorVigenteResponse toIndicadorIsap(ResultadoIsapResumo r) {
        Indicador ind = indicadorRepository != null
                ? indicadorRepository.findBySigla("ISAP").orElse(null)
                : null;

        String nome = ind != null ? ind.getNome() : "Índice de Sobreposição a Áreas Protegidas";
        String unidade = ind != null ? ind.getUnidade() : "%";
        String versaoRegra = r.getRegraCalculo() != null ? r.getRegraCalculo().getVersao() : null;

        return new IndicadorVigenteResponse(
                "ISAP",
                nome,
                unidade,
                r.getStatus(),
                r.getPctSobreposicao(),
                r.getAreaSobrepostaHa(),
                r.getAreaSobrepostaHa(),
                versaoRegra,
                null);
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