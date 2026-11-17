package br.com.fatec.georural.controller;

import br.com.fatec.georural.dto.response.monitoramento.RecursosResponse;
import br.com.fatec.georural.dto.response.monitoramento.SaudePlataformaResponse;
import br.com.fatec.georural.service.monitoramento.ConsumoRecursosService;
import br.com.fatec.georural.service.monitoramento.SaudePlataformaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoramento")
@Tag(name = "Monitoramento", description = "Estado da plataforma para o portal")
public class MonitoramentoController {

    private final SaudePlataformaService saudePlataforma;
    private final ConsumoRecursosService consumoRecursos;

    public MonitoramentoController(SaudePlataformaService saudePlataforma,
                                   ConsumoRecursosService consumoRecursos) {
        this.saudePlataforma = saudePlataforma;
        this.consumoRecursos = consumoRecursos;
    }

    // estado de portal, API, banco, armazenamento e Airflow
    @GetMapping("/saude")
    @Operation(summary = "Estado dos componentes da plataforma",
            description = "Devolve o pior estado entre os componentes e o detalhe de cada um. "
                    + "O resultado e reaproveitado por alguns segundos "
                    + "(georural.monitoramento.cache-segundos) para a tela poder recarregar "
                    + "sem bater no Airflow a cada vez.")
    public SaudePlataformaResponse saude(
            @Parameter(description = "true ignora o cache e verifica agora")
            @RequestParam(value = "agora", required = false, defaultValue = "false") boolean agora) {
        return agora ? saudePlataforma.consultarAgora() : saudePlataforma.consultar();
    }

    // CPU, memoria, disco e tamanho do acervo
    @GetMapping("/recursos")
    @Operation(summary = "Consumo de recursos",
            description = "Medido de dentro da aplicacao (java.lang.management) e do catalogo "
                    + "ARQUIVO_BRUTO. O campo naoMedido lista o que depende do provedor de nuvem.")
    public RecursosResponse recursos() {
        return consumoRecursos.consultar();
    }
}
