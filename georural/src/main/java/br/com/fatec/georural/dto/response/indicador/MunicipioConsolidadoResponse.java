package br.com.fatec.georural.dto.response.indicador;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Um municipio na comparacao de um indicador.
 *
 * Municipio sem resultado para o indicador e a versao vem com temDados=false e
 * os numeros nulos, para a tabela listar os 399 sem buraco.
 */
public record MunicipioConsolidadoResponse(
        Long municipioId,
        String codigoIbge,
        String nome,
        String uf,
        BigDecimal areaHa,
        boolean temDados,
        // media de rin_valor_percentual dos imoveis do municipio
        BigDecimal valorAgregado,
        // de rmu_detalhes: quantos imoveis entraram na media
        Long imoveis,
        // de rmu_detalhes: quantos estavam na situacao boa do indicador
        Long conformes,
        // conformes / imoveis * 100, calculado aqui
        BigDecimal percentualConformes,
        // de rmu_detalhes: soma de rin_valor_hectares
        BigDecimal somaHectares,
        String situacao,
        Long versaoId,
        LocalDateTime dataCalculo) {
}
