package br.com.fatec.georural.dto.response;

import java.time.LocalDateTime;

public record ArquivoResumoResponse(
        Long id,
        String nome,
        String nomeArquivo,
        String fonte,
        String formato,
        Integer qtdArquivos,
        String situacao,
        LocalDateTime recebidoEm
) {}