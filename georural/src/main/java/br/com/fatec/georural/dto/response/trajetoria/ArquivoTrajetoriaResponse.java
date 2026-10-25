package br.com.fatec.georural.dto.response.trajetoria;

import br.com.fatec.georural.dto.response.indicador.IndicadorResponse;

import java.time.LocalDateTime;
import java.util.List;

public record ArquivoTrajetoriaResponse(
        Long arquivoId,
        String nome,
        String fonte,
        LocalDateTime dataUpload,
        String zonaAtual,
        List<PassagemZonaResponse> passagens,
        List<IndicadorResponse> indicadoresGerados
) {}
