package br.com.fatec.georural.dto.response.pipeline;

import br.com.fatec.georural.dto.response.linhagem.EntradaLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.EtapaExecucaoResponse;
import br.com.fatec.georural.dto.response.linhagem.MovimentacaoZonaResponse;
import br.com.fatec.georural.dto.response.linhagem.TransformacaoLinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.ValidacaoLinhagemResponse;

import java.util.List;
import java.util.Map;

// Detalhe de uma execucao, com o que ela consumiu, fez e moveu.
public record ExecucaoDetalheResponse(
        ExecucaoItemResponse execucao,
        List<EtapaExecucaoResponse> irmasDaRodada,
        List<EntradaLinhagemResponse> entradas,
        List<TransformacaoLinhagemResponse> transformacoes,
        List<MovimentacaoZonaResponse> movimentacoes,
        List<ValidacaoLinhagemResponse> validacoes,
        Map<String, String> links
) {}
