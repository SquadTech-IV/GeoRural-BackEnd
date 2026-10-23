package br.com.fatec.georural.dto.response.qualidade;

public record RegraAvaliadaResponse(
        String nomeRegra,
        String tipo,
        String severidade,
        boolean aprovada,
        long registrosAfetados
) {
}
