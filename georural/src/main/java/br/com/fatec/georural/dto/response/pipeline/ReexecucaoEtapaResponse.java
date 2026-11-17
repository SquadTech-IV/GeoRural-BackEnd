package br.com.fatec.georural.dto.response.pipeline;

// Resultado de reexecutar uma etapa de uma rodada existente.
public record ReexecucaoEtapaResponse(
        String dagRunId,
        String etapa,
        int tentativa,
        boolean forcada,
        TentativaAnteriorResponse tentativaAnterior,
        ContextoRecuperadoResponse contextoRecuperado,
        EtapaExecutadaResponse execucao) {
}
