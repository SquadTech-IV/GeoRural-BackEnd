package br.com.fatec.georural.dto.response.catalogo;

// Coleta automatica configurada no conjunto (sem expor credencial).
public record EntradaApiResumo(
        String endpoint,
        String formato,
        boolean autenticacaoConfigurada
) {}
