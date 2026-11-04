package br.com.fatec.georural.service.pipeline;

import br.com.fatec.georural.entity.ExecucaoPipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PipelineExecutor {

    private final AuditoriaEtapaService auditoria;
    private final Map<Etapa, EtapaPipeline> etapas = new EnumMap<>(Etapa.class);

    public PipelineExecutor(AuditoriaEtapaService auditoria, List<EtapaPipeline> implementacoes) {
        this.auditoria = auditoria;
        for (EtapaPipeline impl : implementacoes) {
            EtapaPipeline anterior = etapas.put(impl.etapa(), impl);
            if (anterior != null) {
                throw new IllegalStateException(
                        "Ha duas implementacoes para a etapa " + impl.etapa() + ": "
                                + anterior.getClass().getSimpleName() + " e "
                                + impl.getClass().getSimpleName());
            }
        }
        log.info("Pipeline montado. Etapas implementadas: {}", etapas.keySet());
    }

    public boolean implementada(Etapa etapa) {
        return etapas.containsKey(etapa);
    }

    public List<Etapa> implementadas() {
        return new ArrayList<>(etapas.keySet());
    }

    public ExecucaoPipeline executarEtapa(Etapa etapa, ContextoExecucao ctx) {
        EtapaPipeline impl = etapas.get(etapa);
        if (impl == null) {
            throw new EtapaNaoImplementadaException(etapa);
        }

        ExecucaoPipeline execucao = auditoria.abrir(etapa, ctx);
        ctx.setExecucaoIdAtual(execucao.getId());

        try {
            ResultadoEtapa resultado = impl.executar(ctx);
            if (resultado.versaoGeradaId() != null) {
                ctx.setVersaoGeradaId(resultado.versaoGeradaId());
            }
            return auditoria.fechar(execucao.getId(), resultado);
        } catch (RuntimeException e) {
            auditoria.falhar(execucao.getId(), e);
            throw e;
        }
    }

    /** Etapas 1 a 5, em ordem. Para na primeira que falhar. */
    public List<ExecucaoPipeline> executarAutomatico(ContextoExecucao ctx) {
        List<ExecucaoPipeline> feitas = new ArrayList<>();
        for (Etapa etapa : Etapa.values()) {
            if (!etapa.automatica()) {
                continue;
            }
            if (!implementada(etapa)) {
                log.warn("[{}] etapa {} ainda nao implementada; pipeline interrompido aqui.",
                        ctx.getDagRunId(), etapa);
                break;
            }
            feitas.add(executarEtapa(etapa, ctx));
        }
        return feitas;
    }

    public static class EtapaNaoImplementadaException extends RuntimeException {
        public EtapaNaoImplementadaException(Etapa etapa) {
            super("A etapa " + etapa + " (" + etapa.descricao() + ") ainda nao foi implementada.");
        }
    }
}
