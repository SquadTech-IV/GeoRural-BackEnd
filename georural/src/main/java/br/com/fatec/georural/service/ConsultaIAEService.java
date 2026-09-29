package br.com.fatec.georural.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.mapper.IAEMapper;
import br.com.fatec.georural.repository.IndicadorRepository;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultaIAEService {

    private final ResultadoIndicadorRepository resultadoIndicadorRepository;
    private final IndicadorRepository indicadorRepository;
    private final ImovelRuralRepository imovelRuralRepository;
    private final IAEMapper mapper;
    private final VersaoEmbargoService versaoEmbargoService;

    public IAEResponse consultarPorImovel(Long imovelId) {

        Indicador indicador = indicadorRepository.findBySigla("IAE")
            .orElseThrow(() -> new EstadoInconsistenteException(
                "Indicador IAE não cadastrado"
            ));

        ResultadoIndicador resultado = resultadoIndicadorRepository
            .findTopByImovelIdAndIndicadorOrderByDataCalculoDesc(
                imovelId,
                indicador
            )
            .orElseThrow(() -> new RecursoNaoEncontradoException(
                "Nenhum resultado de IAE calculado para o imóvel " + imovelId
            ));

        Long versaoId = versaoEmbargoService.resolverId();

        List<Object[]> embargosRaw =
            imovelRuralRepository.encontrarEmbargosIntersectantes(
                imovelId,
                versaoId
            );

        return mapper.toResponse(resultado, embargosRaw);
    }

    public IAEResponse consultarPorCAR(String carId) {

        Indicador indicador = indicadorRepository.findBySigla("IAE")
            .orElseThrow(() -> new EstadoInconsistenteException(
                "Indicador IAE não cadastrado"
            ));

        Long imovelId = imovelRuralRepository
            .findByCodigoCar(carId)
            .orElseThrow(() -> new RecursoNaoEncontradoException(
                "Imóvel não encontrado com o CAR: " + carId
            ))
            .getId();

        ResultadoIndicador resultado = resultadoIndicadorRepository
            .findTopByImovelIdAndIndicadorOrderByDataCalculoDesc(
                imovelId,
                indicador
            )
            .orElseThrow(() -> new RecursoNaoEncontradoException(
                "Nenhum resultado de IAE calculado para o imóvel com o CAR: " + carId
            ));

        Long versaoId = versaoEmbargoService.resolverId();

        List<Object[]> embargosRaw =
            imovelRuralRepository.encontrarEmbargosIntersectantes(
                imovelId,
                versaoId
            );

        return mapper.toResponse(resultado, embargosRaw);
    }

    public List<IAEResponse> listarTodosImoveisIAE() {

        Long versaoId = versaoEmbargoService.resolverId();

        return resultadoIndicadorRepository
            .listarImoveisComIAE()
            .stream()
            .map(resultado -> {

                Long imovelId = resultado.getImovel().getId();

                List<Object[]> embargosRaw =
                    imovelRuralRepository.encontrarEmbargosIntersectantes(
                        imovelId,
                        versaoId
                    );

                return mapper.toResponse(resultado, embargosRaw);
            })
            .toList();
    }
}