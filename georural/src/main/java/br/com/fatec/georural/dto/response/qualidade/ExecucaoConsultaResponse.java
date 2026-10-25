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
public class ExecucaoConsultaResponse {

    private Long id;
    private Long conjuntoId;
    private String nomeConjunto;
    private Long arquivoBrutoId;
    private String nomeArquivoBruto;
    private String etapa;
    private String status;
    private Long registrosRecebidos;
    private Long registrosAprovados;
    private Long registrosRejeitados;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private Long duracaoSeg;
}