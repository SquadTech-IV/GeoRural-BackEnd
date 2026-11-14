package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.indicador.IndicadorCatalogoResponse;
import br.com.fatec.georural.dto.response.indicador.MetricaIndicadorResponse;
import br.com.fatec.georural.dto.response.indicador.ResultadoImovelResponse;
import br.com.fatec.georural.dto.response.indicador.ResultadoMunicipioResponse;
import br.com.fatec.georural.service.indicador.ConsultaIndicadorService;
import br.com.fatec.georural.service.indicador.FiltroIndicador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

// Consulta dos indicadores, dos resultados e das metricas (US#17.03).
@RestController
@RequestMapping("/api/v1/indicadores")
@Tag(
        name = "Indicadores e metricas",
        description = "Consulta somente leitura dos indicadores, dos resultados por "
                + "imovel e por municipio, e das metricas agregadas de cada "
                + "indicador por versao (US#17.03)"
)
public class IndicadorConsultaController {

    private final ConsultaIndicadorService servico;

    public IndicadorConsultaController(ConsultaIndicadorService servico) {
        this.servico = servico;
    }

    @GetMapping
    @Operation(
            summary = "Lista os indicadores com a regra de calculo em vigor",
            description = "Para cada indicador devolve sigla, nome, unidade, a regra "
                    + "vigente com formula e parametros, e quantos resultados ja existem."
    )
    public List<IndicadorCatalogoResponse> catalogo() {
        return servico.catalogo();
    }

    @GetMapping("/resultados")
    @Operation(
            summary = "Resultados dos indicadores por imovel",
            description = "Pagina de resultados com imovel, municipio, versao, execucao, "
                    + "regra aplicada e os valores em percentual, hectares e absoluto. "
                    + "Sem o filtro vigente a consulta devolve os resultados de todas as versoes."
    )
    public PaginaResponse<ResultadoImovelResponse> resultados(
            @Parameter(description = "Sigla do indicador, ou varias separadas por virgula")
            @RequestParam(required = false) String indicador,

            @Parameter(description = "Identificador da versao")
            @RequestParam(required = false) Long versaoId,

            @Parameter(description = "Identificador do imovel")
            @RequestParam(required = false) Long imovelId,

            @Parameter(description = "Codigo do CAR do imovel")
            @RequestParam(required = false) String codigoCar,

            @Parameter(description = "Identificador do municipio")
            @RequestParam(required = false) Long municipioId,

            @Parameter(description = "Parte do nome do municipio")
            @RequestParam(required = false) String municipio,

            @Parameter(description = "Competencia da versao, no formato AAAA-MM")
            @RequestParam(required = false) String competencia,

            @Parameter(description = "Situacao do resultado, por exemplo CONFORME")
            @RequestParam(required = false) String situacao,

            @Parameter(description = "true traz so os resultados vigentes, false so os nao vigentes")
            @RequestParam(required = false) Boolean vigente,

            @Parameter(description = "Valor percentual minimo")
            @RequestParam(required = false) BigDecimal valorMinimo,

            @Parameter(description = "Valor percentual maximo")
            @RequestParam(required = false) BigDecimal valorMaximo,

            @Parameter(description = "Inclui o JSON de detalhes do calculo")
            @RequestParam(required = false) Boolean incluirDetalhes,

            @Parameter(description = "Pagina, comecando em 0")
            @RequestParam(required = false) Integer pagina,

            @Parameter(description = "Itens por pagina, de 1 a 500. Padrao 50")
            @RequestParam(required = false) Integer tamanho,

            @Parameter(description = "Ordenacao: imovel, valor, valorDesc, calculo ou municipio")
            @RequestParam(required = false) String ordenar
    ) {
        return servico.resultados(FiltroIndicador.montar(
                indicador, versaoId, imovelId, codigoCar, municipioId, municipio,
                competencia, situacao, vigente, valorMinimo, valorMaximo,
                incluirDetalhes, pagina, tamanho, ordenar,
                FiltroIndicador.ORDENACOES
        ));
    }

    @GetMapping("/resultados/municipios")
    @Operation(
            summary = "Resultados dos indicadores agregados por municipio",
            description = "Pagina dos valores consolidados por municipio e versao. "
                    + "Os filtros valorMinimo e valorMaximo aqui comparam o valor agregado."
    )
    public PaginaResponse<ResultadoMunicipioResponse> resultadosPorMunicipio(
            @Parameter(description = "Sigla do indicador, ou varias separadas por virgula")
            @RequestParam(required = false) String indicador,

            @Parameter(description = "Identificador da versao")
            @RequestParam(required = false) Long versaoId,

            @Parameter(description = "Identificador do municipio")
            @RequestParam(required = false) Long municipioId,

            @Parameter(description = "Parte do nome do municipio")
            @RequestParam(required = false) String municipio,

            @Parameter(description = "Competencia da versao, no formato AAAA-MM")
            @RequestParam(required = false) String competencia,

            @Parameter(description = "Situacao do resultado agregado")
            @RequestParam(required = false) String situacao,

            @Parameter(description = "true traz so os resultados vigentes, false so os nao vigentes")
            @RequestParam(required = false) Boolean vigente,

            @Parameter(description = "Valor agregado minimo")
            @RequestParam(required = false) BigDecimal valorMinimo,

            @Parameter(description = "Valor agregado maximo")
            @RequestParam(required = false) BigDecimal valorMaximo,

            @Parameter(description = "Inclui o JSON de detalhes da agregacao")
            @RequestParam(required = false) Boolean incluirDetalhes,

            @Parameter(description = "Pagina, comecando em 0")
            @RequestParam(required = false) Integer pagina,

            @Parameter(description = "Itens por pagina, de 1 a 500. Padrao 50")
            @RequestParam(required = false) Integer tamanho,

            @Parameter(description = "Ordenacao: municipio, valor, valorDesc ou calculo")
            @RequestParam(required = false) String ordenar
    ) {
        return servico.resultadosPorMunicipio(FiltroIndicador.montar(
                indicador, versaoId, null, null, municipioId, municipio,
                competencia, situacao, vigente, valorMinimo, valorMaximo,
                incluirDetalhes, pagina, tamanho, ordenar,
                FiltroIndicador.ORDENACOES_MUNICIPIO
        ));
    }

    @GetMapping("/metricas")
    @Operation(
            summary = "Metricas dos dados por indicador e versao",
            description = "Para cada par indicador e versao devolve quantos resultados e "
                    + "quantos imoveis, o minimo, maximo, media e mediana do percentual, "
                    + "o total em hectares e a distribuicao por situacao. "
                    + "Sem filtro devolve todas as combinacoes, ate 1000."
    )
    public List<MetricaIndicadorResponse> metricas(
            @Parameter(description = "Sigla do indicador, ou varias separadas por virgula")
            @RequestParam(required = false) String indicador,

            @Parameter(description = "Identificador da versao")
            @RequestParam(required = false) Long versaoId,

            @Parameter(description = "Identificador do municipio")
            @RequestParam(required = false) Long municipioId,

            @Parameter(description = "Parte do nome do municipio")
            @RequestParam(required = false) String municipio,

            @Parameter(description = "Competencia da versao, no formato AAAA-MM")
            @RequestParam(required = false) String competencia,

            @Parameter(description = "Situacao do resultado")
            @RequestParam(required = false) String situacao,

            @Parameter(description = "true considera so os resultados vigentes")
            @RequestParam(required = false) Boolean vigente
    ) {
        return servico.metricas(FiltroIndicador.montar(
                indicador, versaoId, null, null, municipioId, municipio,
                competencia, situacao, vigente, null, null,
                false, 0, FiltroIndicador.TAMANHO_PADRAO, null,
                FiltroIndicador.ORDENACOES
        ));
    }
}
