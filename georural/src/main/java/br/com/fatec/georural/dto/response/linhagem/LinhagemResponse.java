package br.com.fatec.georural.dto.response.linhagem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LinhagemResponse(
        ResultadoInfo resultado,
        RegraInfo regra,
        ExecucaoInfo execucao,
        List<TransformacaoInfo> transformacoes,
        List<OrigemInfo> origem
) {
    public record ResultadoInfo(Long id, String codigoCar, String municipio, BigDecimal areaTotalHa,
                                String indicadorSigla, String indicadorNome,
                                BigDecimal valorPercentual, BigDecimal valorHectares, BigDecimal valorAbsoluto,
                                String situacao, Long versaoNumero, String versaoSituacao,
                                LocalDateTime calculadoEm, LocalDateTime publicadoEm, String publicadoPor) {}

    public record ParametroInfo(String nome, String valor, String unidade) {}

    public record RegraInfo(Long id, String versao, String descricao, String autor, String motivo,
                            boolean vigente, LocalDateTime criadaEm, List<ParametroInfo> parametros) {}

    public record ExecucaoInfo(Long id, String etapa, String status, String dagRunId, String disparadoPor,
                               LocalDateTime inicio, LocalDateTime fim, Long duracaoSeg,
                               Long registrosProcessados, Long registrosRejeitados, List<String> etapas) {}

    public record TransformacaoInfo(Integer ordem, String tipo, String descricao, LocalDateTime data) {}

    public record OrigemInfo(Long arquivoId, String nome, String formaEntrada, String fonte, String enviadoPor,
                             LocalDateTime dataUpload, String formato, Long tamanhoBytes, String hash) {}
}