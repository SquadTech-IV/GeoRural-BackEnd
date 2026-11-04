package br.com.fatec.georural.service.pipeline;

public record ResultadoEtapa(
        long recebidos,
        long aprovados,
        long rejeitados,
        String mensagem,
        Long versaoGeradaId) {

    public static ResultadoEtapa de(long recebidos, long rejeitados, String mensagem) {
        return new ResultadoEtapa(recebidos, recebidos - rejeitados, rejeitados, mensagem, null);
    }

    public static ResultadoEtapa semFeicoes(String mensagem) {
        return new ResultadoEtapa(0, 0, 0, mensagem, null);
    }

    public ResultadoEtapa comVersao(Long versaoId) {
        return new ResultadoEtapa(recebidos, aprovados, rejeitados, mensagem, versaoId);
    }

    /** Houve rejeicao? A etapa fecha como COM_RESSALVA. */
    public boolean comRessalva() {
        return rejeitados > 0;
    }
}
