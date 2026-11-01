package br.com.fatec.georural.service.pipeline;

public interface EtapaPipeline {

    Etapa etapa();

    ResultadoEtapa executar(ContextoExecucao contexto);
}
