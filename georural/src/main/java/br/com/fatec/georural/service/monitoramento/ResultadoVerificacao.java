package br.com.fatec.georural.service.monitoramento;

import java.time.LocalDateTime;

// resultado de uma verificacao de componente
public record ResultadoVerificacao(
        String componente,
        EstadoComponente estado,
        String detalhe,
        long latenciaMs,
        LocalDateTime verificadoEm
) {

    public static ResultadoVerificacao noAr(String componente, String detalhe, long latenciaMs) {
        return new ResultadoVerificacao(componente, EstadoComponente.NO_AR, detalhe, latenciaMs,
                LocalDateTime.now());
    }

    public static ResultadoVerificacao degradado(String componente, String detalhe, long latenciaMs) {
        return new ResultadoVerificacao(componente, EstadoComponente.DEGRADADO, detalhe, latenciaMs,
                LocalDateTime.now());
    }

    public static ResultadoVerificacao fora(String componente, String detalhe, long latenciaMs) {
        return new ResultadoVerificacao(componente, EstadoComponente.FORA, detalhe, latenciaMs,
                LocalDateTime.now());
    }

    public static ResultadoVerificacao naoConfigurado(String componente, String detalhe) {
        return new ResultadoVerificacao(componente, EstadoComponente.NAO_CONFIGURADO, detalhe, 0L,
                LocalDateTime.now());
    }
}
