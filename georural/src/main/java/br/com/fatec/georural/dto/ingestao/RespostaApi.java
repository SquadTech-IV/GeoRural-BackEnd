package br.com.fatec.georural.dto.ingestao;

import java.nio.charset.StandardCharsets;

public record RespostaApi(int status, String contentType, byte[] conteudo) {

    public boolean sucesso() {
        return status >= 200 && status < 300;
    }

    public long tamanhoBytes() {
        return conteudo == null ? 0L : conteudo.length;
    }

    public String corpoTexto() {
        return conteudo == null ? "" : new String(conteudo, StandardCharsets.UTF_8);
    }
}