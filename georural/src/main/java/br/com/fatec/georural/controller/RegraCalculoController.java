package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.regra.CriarVersaoRegraRequest;
import br.com.fatec.georural.dto.response.regra.RegraCalculoResponse;
import br.com.fatec.georural.service.RegraCalculoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/indicadores/{sigla}/regras")
public class RegraCalculoController {

    private final RegraCalculoService regraCalculoService;

    public RegraCalculoController(RegraCalculoService regraCalculoService) {
        this.regraCalculoService = regraCalculoService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegraCalculoResponse criarNovaVersao(
            @PathVariable String sigla,
            @RequestBody CriarVersaoRegraRequest request) {
        return regraCalculoService.criarNovaVersao(sigla, request);
    }
}