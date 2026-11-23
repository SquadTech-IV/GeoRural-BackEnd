package br.com.fatec.georural.dto.response.linhagem;

import java.util.List;
import java.util.Map;

// Linhagem reversa: o que um arquivo de origem produziu.
public record LinhagemArquivoResponse(
        ArquivoOrigemResponse arquivo,
        List<ValidacaoLinhagemResponse> qualidade,
        List<EtapaExecucaoResponse> execucoes,
        List<TransformacaoLinhagemResponse> transformacoes,
        List<VersaoLinhagemResponse> versoesGeradas,
        long registrosCarregados,
        long resultadosDerivados,
        List<String> indicadoresAfetados,
        Map<String, String> links
) {}
