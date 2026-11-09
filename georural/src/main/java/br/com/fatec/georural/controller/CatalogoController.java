package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.catalogo.CatalogoDetalheResponse;
import br.com.fatec.georural.dto.response.catalogo.CatalogoItemResponse;
import br.com.fatec.georural.dto.response.catalogo.FiltrosCatalogoResponse;
import br.com.fatec.georural.service.catalogo.ConsultaCatalogoService;
import br.com.fatec.georural.service.catalogo.FiltroCatalogo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Consulta do catalogo de dados para integracao externa (US#17.01).
@RestController
@RequestMapping("/api/v1/catalogo")
@Tag(
        name = "Catalogo de dados",
        description = "Consulta somente leitura dos conjuntos de dados da plataforma, "
                + "com fonte, tipo de camada, versao vigente e acervo (US#17.01)"
)
public class CatalogoController {

    private final ConsultaCatalogoService servico;

    public CatalogoController(ConsultaCatalogoService servico) {
        this.servico = servico;
    }

    @GetMapping
    @Operation(
            summary = "Lista os conjuntos de dados do catalogo",
            description = "Retorna uma pagina de conjuntos com fonte, tipo de camada, "
                    + "versao vigente, acervo e indicadores que consomem o conjunto. "
                    + "Por padrao oculta fontes inativas e conjuntos arquivados."
    )
    public PaginaResponse<CatalogoItemResponse> listar(
            @Parameter(description = "Identificador da fonte do dado")
            @RequestParam(required = false) Long fonteId,

            @Parameter(description = "Sigla da fonte do dado, por exemplo SICAR")
            @RequestParam(required = false) String fonteSigla,

            @Parameter(description = "Codigo do tipo de camada, por exemplo IMOVEL ou EMBARGO")
            @RequestParam(required = false) String tipo,

            @Parameter(description = "Situacao do conjunto: RASCUNHO, EM_VALIDACAO, PUBLICADO ou ARQUIVADO")
            @RequestParam(required = false) String situacao,

            @Parameter(description = "Forma de entrada do dado: UPLOAD, API ou AMBOS")
            @RequestParam(required = false) String formaEntrada,

            @Parameter(description = "Cobertura territorial cadastrada no conjunto")
            @RequestParam(required = false) String cobertura,

            @Parameter(description = "Competencia no formato AAAA-MM; traz apenas conjuntos com arquivo nessa competencia")
            @RequestParam(required = false) String competencia,

            @Parameter(description = "Sigla de indicador; traz os conjuntos que alimentam esse indicador")
            @RequestParam(required = false) String indicador,

            @Parameter(description = "Busca livre no nome e na descricao do conjunto, da fonte e do tipo")
            @RequestParam(required = false) String q,

            @Parameter(description = "Traz apenas conjuntos que ja possuem versao vigente")
            @RequestParam(required = false) Boolean comVersaoVigente,

            @Parameter(description = "Inclui fontes inativas e conjuntos arquivados")
            @RequestParam(required = false) Boolean incluirInativos,

            @Parameter(description = "Pagina, comecando em 0")
            @RequestParam(required = false) Integer pagina,

            @Parameter(description = "Itens por pagina, de 1 a 200. Padrao 20")
            @RequestParam(required = false) Integer tamanho,

            @Parameter(description = "Ordenacao: nome, atualizacao, acervo, situacao ou cadastro")
            @RequestParam(required = false) String ordenar
    ) {
        FiltroCatalogo filtro = FiltroCatalogo.montar(
                fonteId,
                fonteSigla,
                tipo,
                situacao,
                formaEntrada,
                cobertura,
                competencia,
                indicador,
                q,
                comVersaoVigente,
                incluirInativos,
                pagina,
                tamanho,
                ordenar
        );

        return servico.listar(filtro);
    }

    @GetMapping("/filtros")
    @Operation(
            summary = "Valores aceitos pelos filtros da listagem",
            description = "Fontes, tipos de camada, situacoes, formas de entrada, "
                    + "coberturas e ordenacoes disponiveis."
    )
    public FiltrosCatalogoResponse filtros() {
        return servico.filtros();
    }

    @GetMapping("/{conjuntoId}")
    @Operation(
            summary = "Detalha um conjunto do catalogo",
            description = "Traz o item do catalogo com o esquema tecnico da camada, "
                    + "a coleta por API quando configurada, as competencias ja recebidas, "
                    + "o historico de versoes e as rotas relacionadas."
    )
    public CatalogoDetalheResponse detalhar(
            @Parameter(description = "Identificador do conjunto de dados")
            @PathVariable Long conjuntoId
    ) {
        return servico.detalhar(conjuntoId);
    }
}
