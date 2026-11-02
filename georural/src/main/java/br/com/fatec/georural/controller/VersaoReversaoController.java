package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.versao.ReversaoRequest;
import br.com.fatec.georural.dto.response.versao.VersaoResponse;
import br.com.fatec.georural.service.ReversaoPublicacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// desfaz uma publicacao: a versao anterior volta a ser a vigente
@RestController
@RequestMapping("/api/versoes")
@RequiredArgsConstructor
public class VersaoReversaoController {

    private final ReversaoPublicacaoService reversao;

    @PostMapping("/{versaoId}/reverter")
    public VersaoResponse reverter(@PathVariable Long versaoId,
                                   @RequestBody(required = false) ReversaoRequest request) {
        return reversao.reverter(versaoId, request == null ? null : request.motivo());
    }
}
