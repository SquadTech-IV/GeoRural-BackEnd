package br.com.fatec.georural.dto.response.linhagem;

import java.util.List;
import java.util.Map;

// A cadeia inteira, do resultado ate o arquivo de origem.
public record LinhagemCompletaResponse(
        ResultadoLinhagemResponse resultado,
        RegraLinhagemResponse regra,
        VersaoLinhagemResponse versao,
        EtapaExecucaoResponse execucaoDoCalculo,
        PipelineLinhagemResponse pipeline,
        List<EntradaLinhagemResponse> entradas,
        List<TransformacaoLinhagemResponse> transformacoes,
        List<ValidacaoLinhagemResponse> qualidade,
        List<ArquivoOrigemResponse> origem,
        boolean completa,
        List<String> lacunas,
        Map<String, String> links
) {}
