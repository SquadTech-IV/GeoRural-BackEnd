package br.com.fatec.georural.service.pipeline.validacao;

// um problema encontrado em uma feicao
public record ProblemaFeicao(
        String regra,
        Severidade severidade,
        String campo,
        String valorEncontrado,
        String valorEsperado,
        String motivo) {

    public static ProblemaFeicao erro(String regra, String campo, String encontrado,
                                      String esperado, String motivo) {
        return new ProblemaFeicao(regra, Severidade.ERRO, campo, encontrado, esperado, motivo);
    }

    public static ProblemaFeicao aviso(String regra, String campo, String encontrado,
                                       String esperado, String motivo) {
        return new ProblemaFeicao(regra, Severidade.AVISO, campo, encontrado, esperado, motivo);
    }

    public boolean rejeita() {
        return severidade == Severidade.ERRO;
    }
}
