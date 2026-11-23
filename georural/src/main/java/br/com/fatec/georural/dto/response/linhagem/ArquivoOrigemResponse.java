package br.com.fatec.georural.dto.response.linhagem;

import java.time.LocalDateTime;
import java.util.List;

// Arquivo de origem do dado, com a trajetoria dele pelas zonas.
public record ArquivoOrigemResponse(
        Long arquivoId,
        String nome,
        String formato,
        String formatoDetectado,
        String competencia,
        Long tamanhoBytes,
        String hash,
        String algoritmoHash,
        Integer srsDeclarado,
        Integer srsUtilizado,
        String status,
        String canalEntrada,
        String caminho,
        String caminhoInterno,
        Long arquivoPaiId,
        String enviadoPor,
        LocalDateTime dataUpload,
        Long conjuntoId,
        String conjunto,
        String tipoCamada,
        String fonteSigla,
        String fonte,
        String esfera,
        String formaEntrada,
        List<MovimentacaoZonaResponse> zonas
) {}
