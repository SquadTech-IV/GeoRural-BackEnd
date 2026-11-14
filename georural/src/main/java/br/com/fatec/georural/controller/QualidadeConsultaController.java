package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.qualidade.RegraQualidadeResponse;
import br.com.fatec.georural.dto.response.qualidade.RelatorioQualidadeDetalheResponse;
import br.com.fatec.georural.dto.response.qualidade.RelatorioQualidadeResponse;
import br.com.fatec.georural.dto.response.qualidade.ResumoQualidadeResponse;
import br.com.fatec.georural.service.qualidade.ConsultaQualidadeService;
import br.com.fatec.georural.service.qualidade.FiltroQualidade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// Consulta dos relatorios de qualidade dos dados (US#17.04).
@RestController
@RequestMapping("/api/v1/qualidade")
@Tag(
        name = "Relatorios de qualidade",
        description = "Consulta somente leitura dos relatorios de qualidade, do resumo "
                + "acumulado e do catalogo de regras (US#17.04)"
)
public class QualidadeConsultaController {

    private final ConsultaQualidadeService servico;

    public QualidadeConsultaController(ConsultaQualidadeService servico) {
        this.servico = servico;
    }

    @GetMapping("/relatorios")
    @Operation(
            summary = "Lista os relatorios de qualidade",
            description = "Um relatorio por validacao de arquivo, com totais, percentual "
                    + "de aprovacao e quantos registros foram para a quarentena."
    )
    public PaginaResponse<RelatorioQualidadeResponse> relatorios(
            @Parameter(description = "Identificador do conjunto de dados")
            @RequestParam(required = false) Long conjuntoId,

            @Parameter(description = "Identificador da execucao do pipeline")
            @RequestParam(required = false) Long execucaoId,

            @Parameter(description = "Identificador do arquivo bruto")
            @RequestParam(required = false) Long arquivoId,

            @Parameter(description = "Status final da validacao: APROVADO ou REJEITADO")
            @RequestParam(required = false) String status,

            @Parameter(description = "Competencia do arquivo, no formato AAAA-MM")
            @RequestParam(required = false) String competencia,

            @Parameter(description = "Sigla da fonte do dado")
            @RequestParam(required = false) String fonteSigla,

            @Parameter(description = "Codigo do tipo de camada")
            @RequestParam(required = false) String tipo,

            @Parameter(description = "Traz so os relatorios com violacao dessa severidade: ERRO ou AVISO")
            @RequestParam(required = false) String severidade,

            @Parameter(description = "Data inicial da validacao, no formato AAAA-MM-DD")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,

            @Parameter(description = "Data final da validacao, incluida por inteiro")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,

            @Parameter(description = "true traz so quem tem registro em quarentena, false so quem nao tem")
            @RequestParam(required = false) Boolean comQuarentena,

            @Parameter(description = "Busca pelo nome do arquivo")
            @RequestParam(required = false) String q,

            @Parameter(description = "Pagina, comecando em 0")
            @RequestParam(required = false) Integer pagina,

            @Parameter(description = "Itens por pagina, de 1 a 200. Padrao 20")
            @RequestParam(required = false) Integer tamanho,

            @Parameter(description = "Ordenacao: data, aprovacao, invalidos ou arquivo. "
                    + "aprovacao ordena da pior para a melhor")
            @RequestParam(required = false) String ordenar
    ) {
        return servico.relatorios(filtro(conjuntoId, execucaoId, arquivoId, status,
                competencia, fonteSigla, tipo, severidade, desde, ate,
                comQuarentena, q, pagina, tamanho, ordenar));
    }

    @GetMapping("/relatorios/{validacaoId}")
    @Operation(
            summary = "Detalha um relatorio de qualidade",
            description = "Traz o relatorio com as regras violadas, a quebra por etapa, "
                    + "situacao e severidade, e uma amostra dos registros barrados."
    )
    public RelatorioQualidadeDetalheResponse detalhar(
            @Parameter(description = "Identificador da validacao")
            @PathVariable Long validacaoId,

            @Parameter(description = "Quantos registros da quarentena trazer na amostra, de 0 a 200. Padrao 20")
            @RequestParam(required = false) Integer amostra,

            @Parameter(description = "Inclui o JSON do registro original de cada item da amostra")
            @RequestParam(required = false) Boolean incluirDadoOriginal
    ) {
        return servico.detalhar(
                validacaoId,
                amostra,
                Boolean.TRUE.equals(incluirDadoOriginal)
        );
    }

    @GetMapping("/resumo")
    @Operation(
            summary = "Qualidade acumulada sob os filtros pedidos",
            description = "Totais gerais, a quebra por conjunto de dados e as dez regras "
                    + "mais violadas no recorte. Aceita os mesmos filtros da listagem."
    )
    public ResumoQualidadeResponse resumo(
            @Parameter(description = "Identificador do conjunto de dados")
            @RequestParam(required = false) Long conjuntoId,

            @Parameter(description = "Identificador da execucao do pipeline")
            @RequestParam(required = false) Long execucaoId,

            @Parameter(description = "Status final da validacao: APROVADO ou REJEITADO")
            @RequestParam(required = false) String status,

            @Parameter(description = "Competencia do arquivo, no formato AAAA-MM")
            @RequestParam(required = false) String competencia,

            @Parameter(description = "Sigla da fonte do dado")
            @RequestParam(required = false) String fonteSigla,

            @Parameter(description = "Codigo do tipo de camada")
            @RequestParam(required = false) String tipo,

            @Parameter(description = "Severidade da violacao: ERRO ou AVISO")
            @RequestParam(required = false) String severidade,

            @Parameter(description = "Data inicial da validacao, no formato AAAA-MM-DD")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,

            @Parameter(description = "Data final da validacao, incluida por inteiro")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate
    ) {
        return servico.resumo(filtro(conjuntoId, execucaoId, null, status,
                competencia, fonteSigla, tipo, severidade, desde, ate,
                null, null, 0, FiltroQualidade.TAMANHO_PADRAO, null));
    }

    @GetMapping("/regras")
    @Operation(
            summary = "Catalogo das regras de qualidade",
            description = "Nome, tipo, severidade e descricao de cada regra, "
                    + "com quantas violacoes ela ja acumulou."
    )
    public List<RegraQualidadeResponse> regras(
            @Parameter(description = "true traz so as regras ativas, false so as inativas")
            @RequestParam(required = false) Boolean apenasAtivas
    ) {
        return servico.regras(apenasAtivas);
    }

    private FiltroQualidade filtro(
            Long conjuntoId,
            Long execucaoId,
            Long arquivoId,
            String status,
            String competencia,
            String fonteSigla,
            String tipo,
            String severidade,
            LocalDate desde,
            LocalDate ate,
            Boolean comQuarentena,
            String texto,
            Integer pagina,
            Integer tamanho,
            String ordenar) {
        return FiltroQualidade.montar(conjuntoId, execucaoId, arquivoId, status,
                competencia, fonteSigla, tipo, severidade, desde, ate,
                comQuarentena, texto, pagina, tamanho, ordenar);
    }
}
