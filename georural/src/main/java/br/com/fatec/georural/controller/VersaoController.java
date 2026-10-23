package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.request.PublicacaoRequest;
import br.com.fatec.georural.dto.request.ReprovacaoRequest;
import br.com.fatec.georural.dto.response.versao.VersaoResponse;
import br.com.fatec.georural.dto.response.versao.VersaoResumoResponse;
import br.com.fatec.georural.service.ConsultaVersaoService;
import br.com.fatec.georural.service.PublicacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/versoes")
public class VersaoController {

    private final PublicacaoService publicacaoService;
    private final ConsultaVersaoService consultaVersaoService;

    public VersaoController(PublicacaoService publicacaoService,
                            ConsultaVersaoService consultaVersaoService) {
        this.publicacaoService = publicacaoService;
        this.consultaVersaoService = consultaVersaoService;
    }

    /** Lista as versoes para a fila de conferencia/publicacao. Filtro opcional */
    @GetMapping
    public ResponseEntity<List<VersaoResumoResponse>> listar(
            @RequestParam(required = false) String situacao) {
        return ResponseEntity.ok(consultaVersaoService.listar(situacao));
    }

    /**
     * Publica a versao ). 404 se nao existe; 409 se a qualidade reprovar ou se houver quarentena sem justificativa.
     */
    @PostMapping("/{id}/publicar")
    public ResponseEntity<VersaoResponse> publicar(@PathVariable Long id,
                                                   @RequestBody(required = false) PublicacaoRequest requisicao) {
        String justificativa = requisicao == null ? null : requisicao.justificativa();
        return ResponseEntity.ok(publicacaoService.publicar(id, justificativa));
    }

    /** Reprova manualmente a versao. 409 se ja publicada. */
    @PostMapping("/{id}/reprovar")
    public ResponseEntity<VersaoResponse> reprovar(@PathVariable Long id,
                                                   @RequestBody ReprovacaoRequest requisicao) {
        return ResponseEntity.ok(publicacaoService.reprovar(id, requisicao.motivo()));
    }
}
