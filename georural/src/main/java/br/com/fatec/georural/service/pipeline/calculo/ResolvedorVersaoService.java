package br.com.fatec.georural.service.pipeline.calculo;

import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.service.pipeline.ContextoExecucao;
import br.com.fatec.georural.service.pipeline.Etapa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

// descobre em qual versao o calculo deve gravar
@Service
@RequiredArgsConstructor
public class ResolvedorVersaoService {

    private final ExecucaoPipelineRepository execucaoRepository;

    @Transactional(readOnly = true)
    public Long versaoDaRodada(ContextoExecucao ctx) {
        if (ctx.getVersaoGeradaId() != null) {
            return ctx.getVersaoGeradaId();
        }
        String informada = ctx.getParametros().get("versaoId");
        if (informada != null && !informada.isBlank()) {
            return Long.parseLong(informada.trim());
        }
        return execucaoRepository.findByDagRunIdOrderByInicioAsc(ctx.getDagRunId()).stream()
                .filter(e -> Etapa.TRATAMENTO.name().equals(e.getEtapa()))
                .filter(e -> e.getVersaoGerada() != null)
                .max(Comparator.comparing(ExecucaoPipeline::getId))
                .map(e -> e.getVersaoGerada().getId())
                .orElseThrow(() -> new VersaoNaoResolvidaException(
                        "Nao foi possivel descobrir a versao desta rodada. Rode o TRATAMENTO "
                                + "antes, ou informe o parametro versaoId."));
    }


    public static class VersaoNaoResolvidaException extends RuntimeException {
        public VersaoNaoResolvidaException(String mensagem) {
            super(mensagem);
        }
    }
}
