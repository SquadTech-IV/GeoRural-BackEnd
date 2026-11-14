package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.catalogo.CatalogoDetalheResponse;
import br.com.fatec.georural.dto.response.catalogo.CatalogoItemResponse;
import br.com.fatec.georural.dto.response.catalogo.DadosConjuntoResponse;
import br.com.fatec.georural.dto.response.catalogo.FiltrosCatalogoResponse;
import br.com.fatec.georural.dto.response.catalogo.HistoricoVersoesResponse;
import br.com.fatec.georural.service.catalogo.ConsultaCatalogoService;
import br.com.fatec.georural.service.catalogo.ConsultaDadosService;
import br.com.fatec.georural.service.catalogo.FiltroCatalogo;
import br.com.fatec.georural.service.catalogo.FiltroDados;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Consulta do catalogo, dos dados e das versoes para integracao externa (US#17.01 e US#17.02).
@RestController
@RequestMapping("/api/v1/catalogo")
@Tag(
        name = "Catalogo de dados",
        description = "Consulta somente leitura dos conjuntos de dados da plataforma, "
                + "com fonte, tipo de camada, versao vigente e acervo, mais os registros "
                + "tratados e o historico de versoes de cada conjunto (US#17.01 e US#17.02)"
)
public class CatalogoController {

    private final ConsultaCatalogoService servico;
    private final ConsultaDadosService dados;

    public CatalogoController(
            ConsultaCatalogoService servico,
            ConsultaDadosService dados) {
        this.servico = servico;
        this.dados = dados;
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

    @GetMapping("/{conjuntoId}/dados")
    @Operation(
            summary = "Lista os registros tratados do conjunto",
            description = "Devolve uma pagina dos registros ja tratados do conjunto, "
                    + "com a descricao dos campos de cada camada. A geometria so vem "
                    + "quando pedida, porque um poligono de imovel ocupa mais que a "
                    + "pagina inteira de atributos."
    )
    public DadosConjuntoResponse dados(
            @Parameter(description = "Identificador do conjunto de dados")
            @PathVariable Long conjuntoId,

            @Parameter(description = "Traz apenas os registros gravados por essa versao")
            @RequestParam(required = false) Long versaoId,

            @Parameter(description = "Traz apenas os registros desse imovel. "
                    + "Vale para as camadas IMOVEL, APP e RESERVA_LEGAL")
            @RequestParam(required = false) Long imovelId,

            @Parameter(description = "Busca livre nos campos de texto da camada")
            @RequestParam(required = false) String q,

            @Parameter(description = "Inclui a geometria de cada registro")
            @RequestParam(required = false) Boolean incluirGeometria,

            @Parameter(description = "Formato da geometria: geojson (padrao) ou wkt")
            @RequestParam(required = false) String formatoGeometria,

            @Parameter(description = "Pagina, comecando em 0")
            @RequestParam(required = false) Integer pagina,

            @Parameter(description = "Itens por pagina. Ate 500 sem geometria, ate 50 com geometria")
            @RequestParam(required = false) Integer tamanho
    ) {
        FiltroDados filtro = FiltroDados.montar(
                versaoId,
                imovelId,
                q,
                incluirGeometria,
                pagina,
                tamanho
        );

        return dados.dados(conjuntoId, formatoGeometria, filtro);
    }

    @GetMapping("/{conjuntoId}/versoes")
    @Operation(
            summary = "Historico de versoes do conjunto",
            description = "Lista as versoes do conjunto da mais recente para a mais antiga, "
                    + "com situacao, competencia, execucao que a gerou, justificativa e "
                    + "quantos registros cada uma gravou."
    )
    public HistoricoVersoesResponse versoes(
            @Parameter(description = "Identificador do conjunto de dados")
            @PathVariable Long conjuntoId,

            @Parameter(description = "Pagina, comecando em 0")
            @RequestParam(required = false) Integer pagina,

            @Parameter(description = "Itens por pagina, de 1 a 200. Padrao 20")
            @RequestParam(required = false) Integer tamanho
    ) {
        return dados.versoes(conjuntoId, pagina, tamanho);
    }
}
