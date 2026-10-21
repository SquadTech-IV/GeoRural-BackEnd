package br.com.fatec.georural.service;

import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 Todo servico de calculo (IAE, IRL, ...) deve salvar o resultado por aqui.
 */
@Service
@RequiredArgsConstructor
public class ResultadoIndicadorService {

    private final ResultadoIndicadorRepository repository;

    @Transactional
    public ResultadoIndicador salvarComoVigente(ResultadoIndicador resultado) {
        Long imovelId = resultado.getImovel().getId();
        Indicador indicador = resultado.getIndicador();

        repository.findByImovelIdAndIndicadorAndVigente(imovelId, indicador, "S")
                .filter(atual -> !atual.getId().equals(resultado.getId()))   // nao invalida ele mesmo
                .ifPresent(atual -> {
                    atual.setVigente("N");
                    repository.saveAndFlush(atual);
                });

        resultado.setVigente("S");
        if (resultado.getDataCalculo() == null) {
            resultado.setDataCalculo(LocalDateTime.now());
        }
        return repository.save(resultado);
    }
}