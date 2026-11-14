package br.com.fatec.georural.dto.response.catalogo;

import java.util.List;
import java.util.Map;

// Detalhe de um conjunto: o item do catalogo mais esquema, versoes e competencias.
public record CatalogoDetalheResponse(
        CatalogoItemResponse conjunto,
        String esquemaTecnico,
        EntradaApiResumo entradaApi,
        List<String> competencias,
        List<VersaoResumo> versoes,
        Map<String, String> links
) {}
