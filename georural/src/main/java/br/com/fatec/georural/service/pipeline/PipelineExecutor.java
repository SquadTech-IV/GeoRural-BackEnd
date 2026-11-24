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

    /** Etapas 1 a 5, em ordem. Para na primeira que falhar ou que nao aprovar nada. */
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

            ExecucaoPipeline execucao = executarEtapa(etapa, ctx);
            feitas.add(execucao);

            // A etapa recebeu feicao e nao aprovou nenhuma: nao sobrou nada para as
            // proximas. Antes o pipeline seguia e o CALCULO estourava com "Rode o
            // TRATAMENTO antes", que e enganoso -- o tratamento rodou, so nao aprovou
            // nada, entao nao criou versao. Encerra aqui, sem erro.
            if (nadaAprovado(execucao)) {
                log.info("[{}] etapa {} nao aprovou nenhuma das {} feicao(oes) recebida(s);"
                                + " pipeline encerrado aqui. Confira a quarentena.",
                        ctx.getDagRunId(), etapa, numero(execucao.getRegistrosRecebidos()));
                break;
            }
        }
        return feitas;
    }

    // Recebeu dado e nao aprovou nada. Zero recebido e outra coisa: a etapa nao tinha
    // o que fazer (a malha municipal nao gera indicador, e o CALCULO dela fecha com
    // tudo zero) e a rodada segue normalmente.
    private static boolean nadaAprovado(ExecucaoPipeline execucao) {
        return numero(execucao.getRegistrosRecebidos()) > 0
                && numero(execucao.getRegistrosAprovados()) == 0;
    }

    private static long numero(Long valor) {
        return valor == null ? 0L : valor;
    }

    public static class EtapaNaoImplementadaException extends RuntimeException {
        public EtapaNaoImplementadaException(Etapa etapa) {
            super("A etapa " + etapa + " (" + etapa.descricao() + ") ainda nao foi implementada.");
        }
    }
}
