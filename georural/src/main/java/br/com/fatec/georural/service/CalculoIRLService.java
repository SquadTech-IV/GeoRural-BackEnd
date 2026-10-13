package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.ResultadoIRL;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class CalculoIRLService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final ParametrosRegraService parametrosRegraService;

    public ResultadoIRL calcular(BigDecimal areaReservaLegalHa, BigDecimal areaImovelHa) {
        ParametrosVigentes p = parametrosRegraService.daRegraVigente("IRL");
        BigDecimal minimoPercent = p.numero("minimo_rl_percent");

        BigDecimal percentualRL = areaImovelHa.compareTo(BigDecimal.ZERO) > 0
                ? areaReservaLegalHa.divide(areaImovelHa, 10, RoundingMode.HALF_UP)
                .multiply(CEM).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal areaMinimaHa = areaImovelHa.multiply(minimoPercent)
                .divide(CEM, 4, RoundingMode.HALF_UP);

        BigDecimal saldoHa = areaReservaLegalHa.subtract(areaMinimaHa)
                .setScale(4, RoundingMode.HALF_UP);

        return new ResultadoIRL(p.versao(), minimoPercent, percentualRL, areaMinimaHa, saldoHa);
    }
}