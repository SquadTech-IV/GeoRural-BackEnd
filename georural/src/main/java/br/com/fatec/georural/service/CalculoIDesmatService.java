package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.ResultadoIDesmat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalculoIDesmatService {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final ParametrosRegraService parametrosRegraService;

    public ResultadoIDesmat calcular(List<EventoDesmatamento> eventos, BigDecimal areaImovelHa) {
        ParametrosVigentes p = parametrosRegraService.daRegraVigente("IDESMAT");

        List<String> marcos = p.nomes().stream()
                .filter(nome -> nome.startsWith("marco"))
                .sorted(Comparator.comparing(p::data))
                .toList();

        List<ResultadoIDesmat.SupressaoPorMarco> porMarco = new ArrayList<>();
        for (String nome : marcos) {
            LocalDate marco = p.data(nome);

            BigDecimal areaBrutaHa = eventos == null ? BigDecimal.ZERO : eventos.stream()
                    .filter(evento -> evento != null && evento.data() != null && evento.areaHa() != null)
                    .filter(evento -> evento.data().isAfter(marco))
                    .map(EventoDesmatamento::areaHa)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal areaHa = areaImovelHa != null && areaImovelHa.compareTo(BigDecimal.ZERO) > 0
                    && areaBrutaHa.compareTo(areaImovelHa) > 0
                    ? areaImovelHa.setScale(4, RoundingMode.HALF_UP)
                    : areaBrutaHa.setScale(4, RoundingMode.HALF_UP);

            BigDecimal percentual = areaImovelHa != null && areaImovelHa.compareTo(BigDecimal.ZERO) > 0
                    ? areaHa.divide(areaImovelHa, 10, RoundingMode.HALF_UP)
                    .multiply(CEM)
                    .min(CEM)
                    .setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

            porMarco.add(new ResultadoIDesmat.SupressaoPorMarco(nome, marco, areaHa, percentual));
        }

        return new ResultadoIDesmat(p.versao(), porMarco);
    }

    public record EventoDesmatamento(LocalDate data, BigDecimal areaHa) {
    }
}