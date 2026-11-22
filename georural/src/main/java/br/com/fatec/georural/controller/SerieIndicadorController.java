package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.indicador.SeriesIndicadorResponse;
import br.com.fatec.georural.service.indicador.FiltroSerie;
import br.com.fatec.georural.service.indicador.SerieIndicadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/indicadores")
@RequiredArgsConstructor
public class SerieIndicadorController {

    private final SerieIndicadorService servico;

    // os dois graficos da tela, com os mesmos filtros da tabela
    @GetMapping("/series")
    public SeriesIndicadorResponse series(
            @RequestParam String indicador,
            @RequestParam(required = false) Long versaoId,
            @RequestParam(required = false) String competencia,
            @RequestParam(required = false) Long municipioId,
            @RequestParam(required = false) String municipio,
            @RequestParam(required = false) String codigoCar,
            @RequestParam(required = false) String situacao,
            @RequestParam(required = false) Boolean vigente,
            @RequestParam(required = false) BigDecimal valorMinimo,
            @RequestParam(required = false) BigDecimal valorMaximo,
            @RequestParam(required = false) Integer qtdFaixas) {

        return servico.montar(new FiltroSerie(indicador, versaoId, competencia, municipioId,
                municipio, codigoCar, situacao, vigente, valorMinimo, valorMaximo, qtdFaixas));
    }
}
