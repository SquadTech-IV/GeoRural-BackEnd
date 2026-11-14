package br.com.fatec.georural.dto.response.linhagem;

// Versao que entrou como insumo de uma execucao.
public record EntradaLinhagemResponse(
        Long versaoId,
        Integer numero,
        String competencia,
        String situacao,
        String papel,
        Long conjuntoId,
        String conjunto,
        String tipoCamada,
        Long execucaoQueGerou,
        Long arquivoId
) {}
