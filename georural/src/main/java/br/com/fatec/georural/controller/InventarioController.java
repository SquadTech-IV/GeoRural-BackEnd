package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.inventario.ArquivoInventarioResponse;
import br.com.fatec.georural.dto.response.inventario.ImovelDoArquivoResponse;
import br.com.fatec.georural.dto.response.inventario.MunicipioDoArquivoResponse;
import br.com.fatec.georural.dto.response.inventario.PreviaArquivoResponse;
import br.com.fatec.georural.service.pipeline.inventario.ConsultaInventarioService;
import br.com.fatec.georural.service.pipeline.inventario.ConsultaProducaoArquivoService;
import br.com.fatec.georural.service.pipeline.leitor.PreviaArquivoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// o que ja foi processado e o que ainda nao
@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final ConsultaInventarioService consulta;
    private final PreviaArquivoService previa;
    private final ConsultaProducaoArquivoService producao;

    // arquivos de um conjunto, cada um com as camadas de dentro
    @GetMapping("/conjuntos/{conjuntoId}")
    public List<ArquivoInventarioResponse> doConjunto(@PathVariable Long conjuntoId) {
        return consulta.doConjunto(conjuntoId);
    }

    // primeiras feicoes do arquivo, lidas de verdade, sem processar nada
    @GetMapping("/arquivos/{arquivoId}/previa")
    public PreviaArquivoResponse previa(@PathVariable Long arquivoId,
                                        @RequestParam(required = false) Integer limite) {
        return previa.previa(arquivoId, limite);
    }

    // de quais municipios este arquivo trouxe imoveis, e quantos de cada
    @GetMapping("/arquivos/{arquivoId}/municipios")
    public List<MunicipioDoArquivoResponse> municipios(@PathVariable Long arquivoId) {
        return producao.municipios(arquivoId);
    }

    // imoveis que este arquivo produziu, paginados, com filtro por municipio
    @GetMapping("/arquivos/{arquivoId}/imoveis")
    public ImovelDoArquivoResponse imoveis(
            @PathVariable Long arquivoId,
            @RequestParam(required = false) String municipio,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamanho) {
        return producao.imoveis(arquivoId, municipio, busca, pagina, tamanho);
    }
}
