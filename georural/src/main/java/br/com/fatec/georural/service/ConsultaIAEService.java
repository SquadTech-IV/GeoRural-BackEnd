package br.com.fatec.georural.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import br.com.fatec.georural.mapper.IAEMapper;
import br.com.fatec.georural.exception.EstadoInconsistenteException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultaIAEService {

    private final ResultadoIndicadorRepository resultadoIndicadorRepository;
    private final IndicadorRepository indicadorRepository;
    private final ImovelRuralRepository imovelRuralRepository;
    private final IAEMapper mapper;

    public IAEResponse consultarPorImovel(Long imovelId) {
        Indicador indicador = indicadorRepository.findBySigla("IAE")
            .orElseThrow(() -> new EstadoInconsistenteException("Indicador IAE não cadastrado"));

        ResultadoIndicador resultado = resultadoIndicadorRepository
            .findTopByImovelIdAndIndicadorOrderByDataCalculoDesc(imovelId, indicador)
            .orElseThrow(() -> new EstadoInconsistenteException(
                "Nenhum resultado de IAE calculado para o imóvel " + imovelId));

        List<Object[]> embargosRaw = imovelRuralRepository.encontrarEmbargosIntersectantes(imovelId);
        return mapper.toResponse(resultado, embargosRaw);
    }
}
