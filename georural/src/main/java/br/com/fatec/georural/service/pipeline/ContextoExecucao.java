package br.com.fatec.georural.service.pipeline;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class ContextoExecucao {

    private Long conjuntoId;
    private Long arquivoBrutoId;
    private Long usuarioId;
    private String competencia;
    private String dagRunId;

    private final Map<String, String> parametros = new LinkedHashMap<>();

    /** Preenchido pelas etapas conforme avancam. */
    private Long execucaoIdAtual;
    private Long versaoGeradaId;

    public static ContextoExecucao novo(Long conjuntoId, Long arquivoBrutoId, Long usuarioId) {
        ContextoExecucao ctx = new ContextoExecucao();
        ctx.setConjuntoId(conjuntoId);
        ctx.setArquivoBrutoId(arquivoBrutoId);
        ctx.setUsuarioId(usuarioId);
        ctx.setDagRunId("portal-" + UUID.randomUUID());
        return ctx;
    }

    public ContextoExecucao comParametro(String nome, String valor) {
        if (nome != null && valor != null) {
            parametros.put(nome, valor);
        }
        return this;
    }
}
