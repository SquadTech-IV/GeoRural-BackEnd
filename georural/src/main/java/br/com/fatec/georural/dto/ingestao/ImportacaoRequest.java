package br.com.fatec.georural.dto.ingestao;

import java.util.List;

public record ImportacaoRequest(List<String> identificadores) {

    public ImportacaoRequest {
        identificadores = (identificadores == null) ? List.of() : List.copyOf(identificadores);
    }
}