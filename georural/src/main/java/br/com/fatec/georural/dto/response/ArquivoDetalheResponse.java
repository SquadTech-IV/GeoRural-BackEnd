package br.com.fatec.georural.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record ArquivoDetalheResponse(
        Long id,
        String nome,
        String nomeArquivo,
        String fonte,
        String formato,
        Integer srid,
        Long tamanhoBytes,
        Integer qtdArquivos,
        String caminho,
        String situacao,
        String descricao,
        LocalDateTime recebidoEm,
        List<ArquivoItemResponse> arquivos
) {}