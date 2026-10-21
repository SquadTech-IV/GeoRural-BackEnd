package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.ReprovacaoRequest;
import br.com.fatec.georural.dto.response.versao.VersaoResponse;
import br.com.fatec.georural.service.PublicacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/versoes")
public class VersaoController {

    private final PublicacaoService publicacaoService;

    public VersaoController(PublicacaoService publicacaoService) {
        this.publicacaoService = publicacaoService;
    }

    /** Publica a versao . 404 se nao existe, 409 se a qualidade reprovar. */
    @PostMapping("/{id}/publicar")
    public ResponseEntity<VersaoResponse> publicar(@PathVariable Long id) {
        return ResponseEntity.ok(publicacaoService.publicar(id));
    }

    /** Reprova manualmente a versao . 409 se ja publicada. */
    @PostMapping("/{id}/reprovar")
    public ResponseEntity<VersaoResponse> reprovar(@PathVariable Long id,
                                                   @RequestBody ReprovacaoRequest requisicao) {
        return ResponseEntity.ok(publicacaoService.reprovar(id, requisicao.motivo()));
    }
}