package br.com.fatec.georural;

import br.com.fatec.georural.service.CalculoIRLService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;

@Configuration
public class CheckIRLTemp {
    @Bean
    CommandLineRunner checkIRL(CalculoIRLService calc) {
        return args -> {
            var r = calc.calcular(new BigDecimal("15"), new BigDecimal("100")); // RL 15ha, imovel 100ha
            System.out.println(">>> US14.10 IRL (RL=15ha, imovel=100ha)");
            System.out.println("    minimo da regra vigente: " + r.minimoPercent() + "% (versao " + r.versaoRegra() + ")");
            System.out.println("    IRL=" + r.percentualRL() + "% | area minima=" + r.areaMinimaHa() + "ha | saldo=" + r.saldoHa() + "ha");
        };
    }
}