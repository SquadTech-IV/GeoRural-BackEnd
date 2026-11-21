package br.com.fatec.georural.service.monitoramento;

import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;

// a propria API: se esta respondendo, esta no ar. O que vale e o tempo de pe
@Service
public class VerificadorApi implements VerificadorComponente {

    public static final String NOME = "api";

    @Override
    public String nome() {
        return NOME;
    }

    @Override
    public ResultadoVerificacao verificar() {
        Duration pe = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        return ResultadoVerificacao.noAr(NOME, "De pe ha " + humano(pe) + ".", 0L);
    }

    private static String humano(Duration d) {
        long dias = d.toDays();
        if (dias > 0) {
            return dias + "d " + d.toHoursPart() + "h";
        }
        if (d.toHours() > 0) {
            return d.toHours() + "h " + d.toMinutesPart() + "min";
        }
        if (d.toMinutes() > 0) {
            return d.toMinutes() + "min";
        }
        return d.toSeconds() + "s";
    }
}
