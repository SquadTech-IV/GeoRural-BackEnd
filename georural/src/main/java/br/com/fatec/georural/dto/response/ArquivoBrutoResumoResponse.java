package br.com.fatec.georural.dto.response;

import br.com.fatec.georural.entity.ArquivoBruto;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Item da listagem de arquivos brutos. Não expõe o endereço do objeto no armazenamento.
 */
@Schema(description = "Arquivo registrado na zona bruta")
public record ArquivoBrutoResumoResponse(
        @Schema(description = "Identificador do registro") Long id,
        @Schema(description = "Conjunto de dados de destino") Long conjuntoId,
        @Schema(description = "Nome do conjunto de dados") String conjuntoNome,
        @Schema(description = "Nome original do arquivo") String nome,
        @Schema(description = "Formato identificado", example = "SHP") String formato,
        @Schema(description = "Tamanho em bytes") Long tamanhoBytes,
        @Schema(description = "Hash do conteúdo, em hexadecimal minúsculo") String hash,
        @Schema(description = "Algoritmo do hash", example = "SHA-256") String algoritmoHash,
        @Schema(description = "Via de entrada", example = "UPLOAD") String canalEntrada,
        @Schema(description = "Situação do registro", example = "RECEBIDO") String status,
        @Schema(description = "Responsável pelo envio; nulo em registros sem usuário") Long responsavelId,
        @Schema(description = "Nome do responsável; nulo em registros sem usuário") String responsavelNome,
        @Schema(description = "Data e hora do recebimento") LocalDateTime dataUpload
) {

    public static ArquivoBrutoResumoResponse de(ArquivoBruto arquivo) {
        return new ArquivoBrutoResumoResponse(
                arquivo.getId(),
                arquivo.getConjunto() != null ? arquivo.getConjunto().getId() : null,
                arquivo.getConjunto() != null ? arquivo.getConjunto().getNome() : null,
                arquivo.getNome(),
                arquivo.getFormato(),
                arquivo.getTamanhoBytes(),
                arquivo.getHash(),
                arquivo.getAlgoritmoHash(),
                arquivo.getCanalEntrada(),
                arquivo.getStatus(),
                arquivo.getUsuario() != null ? arquivo.getUsuario().getId() : null,
                arquivo.getUsuario() != null ? arquivo.getUsuario().getNome() : null,
                arquivo.getDataUpload());
    }
}