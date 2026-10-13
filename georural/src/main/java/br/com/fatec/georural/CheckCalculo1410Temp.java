// TEMPORARIO - so para conferir a US14.10 completa. Apague depois.
package br.com.fatec.georural;

import br.com.fatec.georural.service.CalculoIRLService;
import br.com.fatec.georural.service.CalculoIDesmatService;
import br.com.fatec.georural.service.CalculoIDesmatService.EventoDesmatamento;
import br.com.fatec.georural.service.CalculoIFCService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Configuration
public class CheckCalculo1410Temp {
    @Bean
    CommandLineRunner checkCalculo(CalculoIRLService irl, CalculoIDesmatService desmat, CalculoIFCService ifc) {
        return args -> {
            var rIrl = irl.calcular(new BigDecimal("15"), new BigDecimal("100"));
            System.out.println(">>> IRL: minimo=" + rIrl.minimoPercent() + "% (v " + rIrl.versaoRegra()
                    + ") | IRL=" + rIrl.percentualRL() + "% | saldo=" + rIrl.saldoHa() + "ha");

            var eventos = List.of(
                    new EventoDesmatamento(LocalDate.of(2010,5,10), new BigDecimal("2.5")),
                    new EventoDesmatamento(LocalDate.of(2019,8,15), new BigDecimal("1.0")),
                    new EventoDesmatamento(LocalDate.of(2021,3,20), new BigDecimal("0.5")));
            var rDes = desmat.calcular(eventos, new BigDecimal("100"));
            System.out.println(">>> IDesmat (v " + rDes.versaoRegra() + "):");
            rDes.marcos().forEach(m -> System.out.println("    " + m.marco() + " (" + m.data() + "): "
                    + m.areaHa() + "ha / " + m.percentual() + "%"));

            var focos = List.of(LocalDate.now().minusMonths(1), LocalDate.now().minusMonths(3), LocalDate.now().minusMonths(6));
            var rIfc = ifc.calcular(focos, new BigDecimal("500"), LocalDate.now());
            System.out.println(">>> IFC: fator=" + rIfc.fator() + " periodo=" + rIfc.periodoMeses()
                    + "m | focos=" + rIfc.focosNoPeriodo() + " | indice=" + rIfc.indice());
        };
    }
}