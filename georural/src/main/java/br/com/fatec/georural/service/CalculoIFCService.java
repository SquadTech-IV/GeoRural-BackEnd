package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.ResultadoIFC;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalculoIFCService {

    private final ParametrosRegraService parametrosRegraService;

    public ResultadoIFC calcular(List<LocalDate> datasFocos, BigDecimal areaImovelHa, LocalDate dataReferencia) {
        ParametrosVigentes p = parametrosRegraService.daRegraVigente("IFC");
        BigDecimal fator = p.numero("fator_area");
        int periodoMeses = p.inteiro("periodo_meses");

        LocalDate inicio = dataReferencia.minusMonths(periodoMeses);
        long focos = datasFocos.stream()
                .filter(data -> !data.isBefore(inicio) && !data.isAfter(dataReferencia))
                .count();

        BigDecimal indice = areaImovelHa.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(focos)
                .divide(areaImovelHa, 10, RoundingMode.HALF_UP)
                .multiply(fator).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new ResultadoIFC(p.versao(), periodoMeses, fator, focos, indice);
    }
}