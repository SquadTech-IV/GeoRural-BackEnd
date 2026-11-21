package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.comparacao.ComparacaoVersaoResponse;
import br.com.fatec.georural.service.comparacao.ComparacaoVersaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * US#19 - Comparacao de duas versoes publicadas de um indicador.
 * Fica sob /api/v1/linhagem, entao ja segue a regra de seguranca de linhagem
 * (ADMIN, GESTOR, OPERADOR e AUDITOR; ANALISTA e bloqueado).
 */
@RestController
@RequestMapping("/api/v1/linhagem/comparacao")
@RequiredArgsConstructor
@Tag(name = "Comparação de versões", description = "Compara duas versões publicadas de um indicador e explica o motivo da mudança (US#19)")
public class ComparacaoVersaoController {

    private final ComparacaoVersaoService service;

    @GetMapping
    @Operation(summary = "Compara imóvel a imóvel duas versões publicadas de um indicador, com o motivo da mudança")
    public ComparacaoVersaoResponse comparar(
            @Parameter(description = "Sigla do indicador (ex.: IAE)")
            @RequestParam String indicador,
            @Parameter(description = "Identificador da versão de referência (A)")
            @RequestParam Long versaoA,
            @Parameter(description = "Identificador da versão comparada (B)")
            @RequestParam Long versaoB,
            @Parameter(description = "Se verdadeiro, lista só imóveis alterados ou presentes em uma única versão")
            @RequestParam(defaultValue = "false") boolean apenasAlterados,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return service.comparar(indicador, versaoA, versaoB, apenasAlterados, pagina, tamanho);
    }
}
