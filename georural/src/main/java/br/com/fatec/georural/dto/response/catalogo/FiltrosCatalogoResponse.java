package br.com.fatec.georural.dto.response.catalogo;

import java.util.List;

// Valores aceitos pelos filtros da listagem do catalogo.
public record FiltrosCatalogoResponse(
        List<FonteResumo> fontes,
        List<TipoResumo> tipos,
        List<String> situacoes,
        List<String> formasEntrada,
        List<String> coberturas,
        List<String> ordenacoes
) {}
