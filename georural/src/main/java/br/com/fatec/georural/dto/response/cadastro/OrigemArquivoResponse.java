package br.com.fatec.georural.dto.response.cadastro;

import java.time.LocalDateTime;

public record OrigemArquivoResponse(
    Long arquivoId,
    String nomeArquivo,
    Long fonteId,
    String fonteNomeNoRecebimento,
    Long conjuntoId,
    String conjuntoNomeAtual,
    String tipoCamada,
    Integer srs,
    String competencia,
    String cobertura,
    LocalDateTime recebidoEm
) {}