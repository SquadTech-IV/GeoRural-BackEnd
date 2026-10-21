package br.com.fatec.georural.dto.response.trajetoria;

import java.util.List;

public record ConjuntoTrajetoriaResponse(
        Long conjuntoId,
        String conjuntoNome,
        List<ArquivoTrajetoriaResponse> arquivos
) {}
