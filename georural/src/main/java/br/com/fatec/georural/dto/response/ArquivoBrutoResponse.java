package br.com.fatec.georural.dto.response;

import br.com.fatec.georural.entity.ArquivoBruto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Resposta do recebimento de um arquivo na zona bruta. Não expõe o endereço do objeto no armazenamento.
 */
@Schema(description = "Registro do arquivo recebido na zona bruta")
public record ArquivoBrutoResponse(
        @Schema(description = "Identificador do registro") Long id,
        @Schema(description = "Conjunto de dados de destino") Long conjuntoId,
        @Schema(description = "Nome original do arquivo") String nome,
        @Schema(description = "Formato identificado", example = "SHP") String formato,
        @Schema(description = "Tamanho em bytes") Long tamanhoBytes,
        @Schema(description = "Hash do conteúdo, em hexadecimal minúsculo") String hash,
        @Schema(description = "Algoritmo do hash", example = "SHA-256") String algoritmoHash,
        @Schema(description = "Via de entrada", example = "UPLOAD") String canalEntrada,
        @Schema(description = "Situação do registro", example = "RECEBIDO") String status,
        @Schema(description = "Data e hora do recebimento") LocalDateTime dataUpload
) {

    public static ArquivoBrutoResponse de(ArquivoBruto arquivo) {
        return new ArquivoBrutoResponse(
                arquivo.getId(),
                arquivo.getConjunto().getId(),
                arquivo.getNome(),
                arquivo.getFormato(),
                arquivo.getTamanhoBytes(),
                arquivo.getHash(),
                arquivo.getAlgoritmoHash(),
                arquivo.getCanalEntrada(),
                arquivo.getStatus(),
                arquivo.getDataUpload());
    }
}