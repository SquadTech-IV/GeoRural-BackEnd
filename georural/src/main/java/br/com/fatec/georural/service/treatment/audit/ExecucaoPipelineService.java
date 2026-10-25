package br.com.fatec.georural.service.treatment.audit;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExecucaoPipelineService {

    private final ExecucaoPipelineRepository repository;

    public ExecucaoPipeline iniciar(
            ConjuntoDados conjunto,
            String etapa) {

        ExecucaoPipeline execucao =
                new ExecucaoPipeline();

        execucao.setConjunto(
                conjunto);

        execucao.setEtapa(
                etapa);

        execucao.setStatus(
                "EM_ANDAMENTO");

        execucao.setInicio(
                LocalDateTime.now());

        return repository.save(
                execucao);
    }
}