package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.qualidade.QualidadeResponse;
import br.com.fatec.georural.service.qualidade.QualidadeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/execucoes")
public class ExecucaoController {

    private final QualidadeService qualidadeService;

    public ExecucaoController(QualidadeService qualidadeService) {
        this.qualidadeService = qualidadeService;
    }

    /** Relatorio de qualidade da execucao. Retorna 404 se a execucao nao existir. */
    @GetMapping("/{id}/qualidade")
    public ResponseEntity<QualidadeResponse> consultarQualidade(@PathVariable Long id) {
        return ResponseEntity.ok(qualidadeService.avaliar(id));
    }
}