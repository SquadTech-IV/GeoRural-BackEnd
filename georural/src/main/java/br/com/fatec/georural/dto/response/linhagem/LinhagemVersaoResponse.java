package br.com.fatec.georural.dto.response.linhagem;

import java.util.List;
import java.util.Map;

// A linhagem de uma versao: o que entrou e o que foi feito para gera-la.
public record LinhagemVersaoResponse(
        VersaoLinhagemResponse versao,
        EtapaExecucaoResponse execucaoQueGerou,
        PipelineLinhagemResponse pipeline,
        List<EntradaLinhagemResponse> entradas,
        List<TransformacaoLinhagemResponse> transformacoes,
        List<ValidacaoLinhagemResponse> qualidade,
        List<ArquivoOrigemResponse> origem,
        long resultadosGerados,
        boolean completa,
        List<String> lacunas,
        Map<String, String> links
) {}
