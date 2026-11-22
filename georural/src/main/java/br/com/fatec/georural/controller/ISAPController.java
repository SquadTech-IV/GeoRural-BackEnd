package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.isap.IsapImovelConsultaResponse;
import br.com.fatec.georural.dto.response.isap.IsapListaItemResponse;
import br.com.fatec.georural.service.indicador.IsapConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/isap")
@RequiredArgsConstructor
@Tag(name = "ISAP - Áreas Protegidas",
     description = "Consulta do Índice de Sobreposição a Áreas Protegidas por imóvel e listagem paginada (US#24.10)")
public class IsapController {

    private final IsapConsultaService service;

    @GetMapping
    @Operation(summary = "Lista paginada dos resultados vigentes do ISAP (linha TOTAL por imóvel)")
    public PaginaResponse<IsapListaItemResponse> listar(
            @Parameter(description = "Filtro por status: COM_SOBREPOSICAO, SEM_SOBREPOSICAO ou CAMADA_INDISPONIVEL")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filtro opcional por ID do município")
            @RequestParam(required = false) Long municipioId,
            @Parameter(description = "Busca por código CAR ou nome do imóvel")
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "dataCalculo") Pageable pageable) {
        return service.listar(status, municipioId, busca, pageable);
    }

    @GetMapping("/imoveis/{imovelId}")
    @Operation(summary = "Detalha o ISAP de um imóvel por ID (4 categorias + TOTAL e lista de feições atingidas)")
    public IsapImovelConsultaResponse detalharPorId(
            @PathVariable Long imovelId,
            @Parameter(description = "Versão opcional; se omitida, retorna os resultados vigentes")
            @RequestParam(required = false) Long versaoId) {
        return service.consultarPorImovelId(imovelId, versaoId);
    }

    @GetMapping("/imoveis/car/{codigoCar}")
    @Operation(summary = "Detalha o ISAP de um imóvel pelo código CAR (4 categorias + TOTAL e lista de feições atingidas)")
    public IsapImovelConsultaResponse detalharPorCar(
            @PathVariable String codigoCar,
            @Parameter(description = "Versão opcional; se omitida, retorna os resultados vigentes")
            @RequestParam(required = false) Long versaoId) {
        return service.consultarPorCodigoCar(codigoCar, versaoId);
    }
}