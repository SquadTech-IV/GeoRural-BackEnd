package br.com.fatec.georural.service.pipeline.formato;

public record FormatoDetectado(
        FormatoArquivo formato,
        String mimeDetectado,
        String extensao,
        Integer srsEpsg,
        String observacao) {

    public static FormatoDetectado de(FormatoArquivo formato, String mime, String extensao) {
        return new FormatoDetectado(formato, mime, extensao, null, null);
    }

    public FormatoDetectado comSrs(Integer epsg) {
        return new FormatoDetectado(formato, mimeDetectado, extensao, epsg, observacao);
    }

    public FormatoDetectado comObservacao(String texto) {
        return new FormatoDetectado(formato, mimeDetectado, extensao, srsEpsg, texto);
    }

    public boolean srsDeclarado() {
        return srsEpsg != null;
    }
}
