package br.com.fatec.georural.dto.response.qualidade;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuarentenaConsultaResponse {

    private Long quarentenaId;
    private Long execucaoId;
    private Long conjuntoId;
    private String nomeConjunto;
    private Long arquivoBrutoId;
    private String nomeArquivoBruto;
    private Long linhaOrigem;
    private String colunaErro;
    private String codigoRegra;
    private String nomeRegra;
    private String motivoErro;
    private String dadoOriginal;
    private String situacao;
    private LocalDateTime dataRegistro;
}