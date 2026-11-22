package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.ErroResponse;
import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.isap.IsapImovelConsultaResponse;
import br.com.fatec.georural.dto.response.isap.IsapListaItemResponse;
import br.com.fatec.georural.service.indicador.IsapConsultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/isap")
@RequiredArgsConstructor
@Tag(
        name = "ISAP - Áreas Protegidas",
        description = "Consulta do Índice de Sobreposição a Áreas Protegidas (UC, TERRA_INDIGENA, QUILOMBOLA, FLORESTA_PUBLICA e TOTAL) "
                + "com resumo dissolvido por categoria e detalhamento por feição atingida. Documentação técnica em docs/features/indicador-isap.md."
)
public class IsapController {

    private final IsapConsultaService service;

    @GetMapping
    @Operation(
            summary = "Lista paginada dos resultados vigentes do ISAP (linha TOTAL por imóvel)",
            description = "Retorna o consolidado TOTAL vigente de cada imóvel calculado pela união geométrica das quatro categorias "
                    + "(UC, TERRA_INDIGENA, QUILOMBOLA e FLORESTA_PUBLICA). Permite filtrar por status, município e código CAR ou nome."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de resultados vigentes retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public PaginaResponse<IsapListaItemResponse> listar(
            @Parameter(description = "Filtro por status: COM_SOBREPOSICAO, SEM_SOBREPOSICAO ou CAMADA_INDISPONIVEL",
                    example = "COM_SOBREPOSICAO")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filtro opcional por ID interno do município", example = "4109401")
            @RequestParam(required = false) Long municipioId,
            @Parameter(description = "Busca textual case-insensitive por código CAR ou nome do imóvel", example = "PR-4109401")
            @RequestParam(required = false) String busca,
            @PageableDefault(size = 20, sort = "dataCalculo") Pageable pageable) {
        return service.listar(status, municipioId, busca, pageable);
    }

    @GetMapping("/imoveis/{imovelId}")
    @Operation(
            summary = "Detalha o ISAP de um imóvel por ID",
            description = "Retorna o resumo dissolvido das 4 categorias (UC, TERRA_INDIGENA, QUILOMBOLA, FLORESTA_PUBLICA) mais TOTAL, "
                    + "a rastreabilidade da camada/regra/filtro aplicado e a lista de feições individuais atingidas, acompanhada do aviso de dupla contagem."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo e detalhamento por feição retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Imóvel não encontrado ou sem resultado ISAP na versão solicitada",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public IsapImovelConsultaResponse detalharPorId(
            @Parameter(description = "Identificador interno do imóvel rural (imo_id)", example = "10")
            @PathVariable Long imovelId,
            @Parameter(description = "ID opcional da versão; se omitido, retorna os resultados vigentes (isr_vigente = 'S')")
            @RequestParam(required = false) Long versaoId) {
        return service.consultarPorImovelId(imovelId, versaoId);
    }

    @GetMapping("/imoveis/car/{codigoCar}")
    @Operation(
            summary = "Detalha o ISAP de um imóvel pelo código CAR",
            description = "Mesmo contrato detalhado de /imoveis/{imovelId}, localizado pelo código oficial do Cadastro Ambiental Rural (imo_codigo_car)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumo e detalhamento por feição retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Código CAR vazio ou inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "401", description = "Token JWT ausente ou inválido",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class))),
            @ApiResponse(responseCode = "404", description = "Imóvel não encontrado para o CAR informado ou sem cálculo ISAP",
                    content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    })
    public IsapImovelConsultaResponse detalharPorCar(
            @Parameter(description = "Código CAR oficial do imóvel rural", example = "PR-4109401-4B2C9E1F8A7D4E3B9C1A2F5E8D7C6B4A")
            @PathVariable String codigoCar,
            @Parameter(description = "ID opcional da versão; se omitido, retorna os resultados vigentes (isr_vigente = 'S')")
            @RequestParam(required = false) Long versaoId) {
        return service.consultarPorCodigoCar(codigoCar, versaoId);
    }
}