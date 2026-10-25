package br.com.fatec.georural.dto.ingestao;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArquivoQuarentenaResponse {

    private Long execucaoId;
    private Long validacaoId;
    private Long arquivoBrutoId;
    private String nomeArquivoBruto;
    private LocalDateTime dataGeracao;
    private Long totalRegistrosRejeitados;
    private List<ItemRejeitadoJson> registros;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRejeitadoJson {
        private Long quarentenaId;
        private Long linhaOrigem;
        private String colunaErro;
        private String codigoRegra;
        private String nomeRegra;
        private String motivoErro;
        
        @JsonRawValue
        private String dadoOriginal;
    }
}
