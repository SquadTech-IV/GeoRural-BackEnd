package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.inventario.ArquivoInventarioResponse;
import br.com.fatec.georural.dto.response.inventario.PreviaArquivoResponse;
import br.com.fatec.georural.service.pipeline.inventario.ConsultaInventarioService;
import br.com.fatec.georural.service.pipeline.leitor.PreviaArquivoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final ConsultaInventarioService consulta;
    private final PreviaArquivoService previa;

    /** Arquivos enviados para um conjunto, cada um com os conjuntos de dados de dentro. */
    @GetMapping("/conjuntos/{conjuntoId}")
    public List<ArquivoInventarioResponse> doConjunto(@PathVariable Long conjuntoId) {
        return consulta.doConjunto(conjuntoId);
    }

    /** Primeiras feicoes do arquivo, lidas de verdade, sem processar nada. */
    @GetMapping("/arquivos/{arquivoId}/previa")
    public PreviaArquivoResponse previa(@PathVariable Long arquivoId,
                                        @RequestParam(required = false) Integer limite) {
        return previa.previa(arquivoId, limite);
    }
}
