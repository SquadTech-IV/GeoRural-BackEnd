package br.com.fatec.georural.dto.response.monitoramento;

import br.com.fatec.georural.service.monitoramento.EstadoComponente;
import br.com.fatec.georural.service.monitoramento.ResultadoVerificacao;

import java.time.LocalDateTime;
import java.util.List;

// estado da plataforma: o pior dos componentes, mais o detalhe de cada um
public record SaudePlataformaResponse(
        EstadoComponente estadoGeral,
        String estadoGeralRotulo,
        LocalDateTime verificadoEm,
        List<Componente> componentes
) {

    public record Componente(
            String nome,
            EstadoComponente estado,
            String estadoRotulo,
            String detalhe,
            long latenciaMs,
            LocalDateTime verificadoEm
    ) {
    }

    public static SaudePlataformaResponse de(List<ResultadoVerificacao> resultados) {
        EstadoComponente geral = pior(resultados);
        return new SaudePlataformaResponse(geral, geral.rotulo(), LocalDateTime.now(),
                resultados.stream()
                        .map(r -> new Componente(r.componente(), r.estado(), r.estado().rotulo(),
                                r.detalhe(), r.latenciaMs(), r.verificadoEm()))
                        .toList());
    }

    // FORA ganha de DEGRADADO, que ganha de NO_AR.
    // NAO_CONFIGURADO nao conta: Airflow sem endereco nao pode deixar a plataforma
    // inteira parecendo quebrada. So vira o estado geral se TODOS estiverem assim.
    private static EstadoComponente pior(List<ResultadoVerificacao> resultados) {
        if (resultados.isEmpty()) {
            return EstadoComponente.NAO_CONFIGURADO;
        }
        boolean temFora = false;
        boolean temDegradado = false;
        boolean temConhecido = false;

        for (ResultadoVerificacao r : resultados) {
            switch (r.estado()) {
                case FORA -> {
                    temFora = true;
                    temConhecido = true;
                }
                case DEGRADADO -> {
                    temDegradado = true;
                    temConhecido = true;
                }
                case NO_AR -> temConhecido = true;
                case NAO_CONFIGURADO -> { }
            }
        }

        if (!temConhecido) {
            return EstadoComponente.NAO_CONFIGURADO;
        }
        if (temFora) {
            return EstadoComponente.FORA;
        }
        return temDegradado ? EstadoComponente.DEGRADADO : EstadoComponente.NO_AR;
    }
}
