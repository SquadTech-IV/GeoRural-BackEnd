package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemArquivoResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemCompletaResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemItemResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemVersaoResponse;
import br.com.fatec.georural.service.linhagem.ConsultaLinhagemService;
import br.com.fatec.georural.service.linhagem.FiltroLinhagem;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Consulta da linhagem dos dados (US#17.05).
@RestController
@RequestMapping("/api/v1/linhagem")
@Tag(
        name = "Linhagem dos dados",
        description = "Percorre a cadeia de um resultado ate o arquivo de origem, "
                + "a linhagem de uma versao e a linhagem reversa de um arquivo (US#17.05)"
)
public class LinhagemConsultaController {

    private final ConsultaLinhagemService servico;

    public LinhagemConsultaController(ConsultaLinhagemService servico) {
        this.servico = servico;
    }

    @GetMapping("/resultados")
    @Operation(
            summary = "Lista os resultados com linhagem consultavel",
            description = "Pagina de resultados com o indicador, o imovel, a versao e a "
                    + "execucao, mais um indicador de se a origem esta rastreada. "
                    + "Sem filtro traz os resultados de todas as versoes."
    )
    public PaginaResponse<LinhagemItemResponse> listar(
            @Parameter(description = "Sigla do indicador")
            @RequestParam(required = false) String indicador,

            @Parameter(description = "Identificador da versao")
            @RequestParam(required = false) Long versaoId,

            @Parameter(description = "Identificador do imovel")
            @RequestParam(required = false) Long imovelId,

            @Parameter(description = "Codigo do CAR do imovel")
            @RequestParam(required = false) String codigoCar,

            @Parameter(description = "Identificador do municipio")
            @RequestParam(required = false) Long municipioId,

            @Parameter(description = "Competencia da versao, no formato AAAA-MM")
            @RequestParam(required = false) String competencia,

            @Parameter(description = "Situacao do resultado")
            @RequestParam(required = false) String situacao,

            @Parameter(description = "true traz so os resultados vigentes")
            @RequestParam(required = false) Boolean vigente,

            @Parameter(description = "true traz so os resultados de versoes publicadas")
            @RequestParam(required = false) Boolean apenasPublicadas,

            @Parameter(description = "Busca livre no codigo do CAR, no nome do imovel e no municipio")
            @RequestParam(required = false) String q,

            @Parameter(description = "Pagina, comecando em 0")
            @RequestParam(required = false) Integer pagina,

            @Parameter(description = "Itens por pagina, de 1 a 200. Padrao 20")
            @RequestParam(required = false) Integer tamanho,

            @Parameter(description = "Ordenacao: calculo, imovel, indicador ou versao")
            @RequestParam(required = false) String ordenar
    ) {
        return servico.listar(FiltroLinhagem.montar(
                indicador, versaoId, imovelId, codigoCar, municipioId, competencia,
                situacao, vigente, apenasPublicadas, q, pagina, tamanho, ordenar
        ));
    }

    @GetMapping("/resultados/{resultadoId}")
    @Operation(
            summary = "Linhagem completa de um resultado",
            description = "A cadeia inteira: o resultado, a regra e os parametros aplicados, "
                    + "a versao, a execucao do calculo, todas as etapas do mesmo DAG run, "
                    + "as versoes que entraram como insumo, as transformacoes com entrada e "
                    + "saida, as validacoes de qualidade e os arquivos de origem com a "
                    + "trajetoria pelas zonas do data lake. O campo completa diz se a cadeia "
                    + "fechou, e lacunas lista o que faltou."
    )
    public LinhagemCompletaResponse doResultado(
            @Parameter(description = "Identificador do resultado do indicador")
            @PathVariable Long resultadoId
    ) {
        return servico.doResultado(resultadoId);
    }

    @GetMapping("/versoes/{versaoId}")
    @Operation(
            summary = "Linhagem de uma versao",
            description = "O que entrou e o que foi feito para gerar a versao, com a "
                    + "execucao geradora, as entradas, as transformacoes, as validacoes "
                    + "e os arquivos de origem."
    )
    public LinhagemVersaoResponse daVersao(
            @Parameter(description = "Identificador da versao")
            @PathVariable Long versaoId
    ) {
        return servico.daVersao(versaoId);
    }

    @GetMapping("/arquivos/{arquivoId}")
    @Operation(
            summary = "Linhagem reversa de um arquivo",
            description = "O caminho contrario: o que o arquivo produziu. Traz as "
                    + "validacoes, as execucoes que o tocaram, as transformacoes, as "
                    + "versoes geradas, quantos registros ele carregou e quais indicadores "
                    + "dependem dele."
    )
    public LinhagemArquivoResponse doArquivo(
            @Parameter(description = "Identificador do arquivo bruto")
            @PathVariable Long arquivoId
    ) {
        return servico.doArquivo(arquivoId);
    }
}
