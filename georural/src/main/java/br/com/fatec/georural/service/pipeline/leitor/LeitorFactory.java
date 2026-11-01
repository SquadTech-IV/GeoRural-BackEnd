package br.com.fatec.georural.service.pipeline.leitor;

import br.com.fatec.georural.service.pipeline.formato.FormatoArquivo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
public class LeitorFactory {

    private final List<LeitorFeicoes> leitores;

    public LeitorFactory(List<LeitorFeicoes> leitores) {
        this.leitores = leitores;
        log.info("Leitores de feicoes registrados: {}",
                leitores.stream().map(l -> l.getClass().getSimpleName()).toList());
    }

    public LeitorFeicoes para(FormatoArquivo formato) {
        return leitores.stream()
                .filter(l -> l.suporta(formato))
                .findFirst()
                .orElseThrow(() -> new FormatoSemLeitorException(formato));
    }

    public boolean temLeitor(FormatoArquivo formato) {
        return leitores.stream().anyMatch(l -> l.suporta(formato));
    }

    public static class FormatoSemLeitorException extends RuntimeException {
        public FormatoSemLeitorException(FormatoArquivo formato) {
            super(formato == FormatoArquivo.GEOTIFF
                    ? "GeoTIFF e raster: a leitura de feicoes nao se aplica. O calculo sobre raster "
                      + "(estatistica zonal) entra junto com o indicador ICV."
                    : "Nenhum leitor para o formato " + formato + " (" + formato.rotulo() + ").");
        }
    }
}
