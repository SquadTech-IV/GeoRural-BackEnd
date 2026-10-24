package br.com.fatec.georural.service.treatment.audit;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Transformacao;
import br.com.fatec.georural.repository.TransformacaoRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransformacaoService {

    private final TransformacaoRepository repository;

    public void registrar(
            ExecucaoPipeline execucao,
            Integer ordem,   // ignorado: a ordem é calculada no banco
            String tipo,
            String descricao) {

        if (execucao == null) {
            return;
        }

        int ordemReal = (int) repository.maxOrdem(execucao.getId()) + 1;

        Transformacao transformacao = new Transformacao();

        transformacao.setExecucao(execucao);
        transformacao.setOrdem(ordemReal);
        transformacao.setTipo(tipo);
        transformacao.setDescricao(descricao);
        transformacao.setData(LocalDateTime.now());

        repository.save(transformacao);
    }
}