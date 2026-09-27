package br.com.fatec.georural.service;

import org.springframework.stereotype.Service;

import br.com.fatec.georural.dto.response.ImovelIaeGeoJsonResponse;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.mapper.ImovelIaeMapper;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;

@Service
public class ImovelIaeService {

    private static final String SIGLA_IAE = "IAE";

    private final ImovelRuralRepository imovelRepository;
    private final ResultadoIndicadorRepository resultadoIndicadorRepository;
    private final ImovelIaeMapper mapper;

    public ImovelIaeService(ImovelRuralRepository imovelRepository,
                             ResultadoIndicadorRepository resultadoIndicadorRepository,
                             ImovelIaeMapper mapper) {
        this.imovelRepository = imovelRepository;
        this.resultadoIndicadorRepository = resultadoIndicadorRepository;
        this.mapper = mapper;
    }

    public ImovelIaeGeoJsonResponse consultarIae(String codigoCar) {
        if (codigoCar == null || codigoCar.isBlank()) {
            throw new IllegalArgumentException("Codigo do CAR nao pode ser vazio");
        }

        ImovelRural imovel = buscarImovel(codigoCar);

        ResultadoIndicador resultado = resultadoIndicadorRepository
                .findFirstByImovel_IdAndIndicador_SiglaOrderByDataCalculoDesc(imovel.getId(), SIGLA_IAE)
                .orElse(null);

        return mapper.toGeoJson(imovel, resultado);
    }

    private ImovelRural buscarImovel(String codigoCar) {
        return imovelRepository.findByCodigoCar(codigoCar)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Imovel rural nao encontrado para o codigo CAR: " + codigoCar));
    }
}