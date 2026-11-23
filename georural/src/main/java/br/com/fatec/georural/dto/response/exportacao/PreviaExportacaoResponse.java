package br.com.fatec.georural.dto.response.exportacao;

public record PreviaExportacaoResponse(
        Long versaoId,
        Integer versaoNumero,
        String competencia,
        String situacao,
        String conjunto,
        String indicador,
        long linhas,
        long imoveis,
        boolean comGeometria,
        String tamanhoEstimado,
        String aviso
) {}
