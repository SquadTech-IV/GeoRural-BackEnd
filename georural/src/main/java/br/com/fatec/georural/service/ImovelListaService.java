package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.imovel.ImovelListaResponse;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ImovelListaService {

    private static final String SIGLA_IAE = "IAE";

    private final ImovelRuralRepository imovelRepository;
    private final ResultadoIndicadorRepository resultadoRepository;
    private final IndicadorRepository indicadorRepository;

    public ImovelListaService(ImovelRuralRepository imovelRepository,
                              ResultadoIndicadorRepository resultadoRepository,
                              IndicadorRepository indicadorRepository) {
        this.imovelRepository = imovelRepository;
        this.resultadoRepository = resultadoRepository;
        this.indicadorRepository = indicadorRepository;
    }

    public List<ImovelListaResponse> listar() {

        var indicadorIae = indicadorRepository.findBySigla(SIGLA_IAE).orElse(null);

        return imovelRepository.findAll().stream()
                .map(imovel -> montar(imovel, indicadorIae))
                .toList();
    }

    private ImovelListaResponse montar(ImovelRural imovel, br.com.fatec.georural.entity.Indicador indicadorIae) {
        BigDecimal iaePercentual = null;

        if (indicadorIae != null) {
            iaePercentual = resultadoRepository
                    .findTopByImovelIdAndIndicadorOrderByDataCalculoDesc(imovel.getId(), indicadorIae)
                    .map(r -> r.getValorPercentual())
                    .orElse(null);
        }

        return new ImovelListaResponse(
                imovel.getId(),
                imovel.getCodigoCar(),
                imovel.getMunicipio() != null ? imovel.getMunicipio().getNome() : null,
                imovel.getAreaTotal(),
                iaePercentual);
    }
}