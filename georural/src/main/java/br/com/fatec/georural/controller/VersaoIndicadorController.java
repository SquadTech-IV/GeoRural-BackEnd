package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.versao.IndicadoresDaVersaoResponse;
import br.com.fatec.georural.service.IndicadorVersaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// o que a publicacao vai ligar: os indicadores ja calculados da versao
@RestController
@RequestMapping("/api/versoes")
@RequiredArgsConstructor
public class VersaoIndicadorController {

    private final IndicadorVersaoService indicadores;

    @GetMapping("/{versaoId}/indicadores")
    public IndicadoresDaVersaoResponse daVersao(@PathVariable Long versaoId) {
        return indicadores.daVersao(versaoId);
    }
}
