package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.ResultadoIFC;
import br.com.fatec.georural.service.indicador.PeriodoApuracaoIfc;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Regra do IFC (indice de focos de calor) escrita em Java: {@code focos / area_ha * fator}.
 *
 * <p>A execucao em producao e o {@code PKG_CALCULO.calcular_ifc}, no Oracle, que trabalha sobre
 * milhares de imoveis com indice espacial. Esta classe e a referencia executavel da mesma
 * regra: e contra ela que os casos de borda (imovel sem focos, foco na divisa, periodo sem
 * dados) sao testados sem precisar de banco. Quem mudar a regra muda os dois.
 */
@Service
@RequiredArgsConstructor
public class CalculoIFCService {

    private final ParametrosRegraService parametrosRegraService;

    /** Um foco de calor: o dia da deteccao e o ponto. */
    public record FocoDetectado(LocalDate data, Geometry ponto) {
    }

    /** Conta pelas datas ja filtradas por imovel; usa a janela de {@code periodo_meses}. */
    public ResultadoIFC calcular(List<LocalDate> datasFocos, BigDecimal areaImovelHa,
                                 LocalDate dataReferencia) {
        ParametrosVigentes p = parametrosRegraService.daRegraVigente("IFC");
        BigDecimal fator = p.numero("fator_area");
        int periodoMeses = p.inteiro("periodo_meses");

        PeriodoApuracaoIfc periodo = PeriodoApuracaoIfc.terminandoEm(dataReferencia, periodoMeses);
        long focos = datasFocos.stream().filter(periodo::contem).count();

        return new ResultadoIFC(p.versao(), periodoMeses, fator, focos,
                indice(focos, areaImovelHa, fator));
    }

    /** Mesma conta, decidindo pela geometria quais focos caem dentro do imovel. */
    public ResultadoIFC calcularNoImovel(Geometry imovel, List<FocoDetectado> focos,
                                         BigDecimal areaImovelHa, LocalDate dataReferencia) {
        ParametrosVigentes p = parametrosRegraService.daRegraVigente("IFC");
        BigDecimal fator = p.numero("fator_area");
        int periodoMeses = p.inteiro("periodo_meses");

        PeriodoApuracaoIfc periodo = PeriodoApuracaoIfc.terminandoEm(dataReferencia, periodoMeses);
        long contados = focos.stream()
                .filter(f -> periodo.contem(f.data()))
                .filter(f -> noImovel(imovel, f.ponto()))
                .count();

        return new ResultadoIFC(p.versao(), periodoMeses, fator, contados,
                indice(contados, areaImovelHa, fator));
    }

    /**
     * Foco na divisa conta como dentro (equivale ao {@code mask=ANYINTERACT} do SDO_RELATE).
     * Um foco exatamente sobre a divisa entre dois imoveis conta nos dois.
     */
    public static boolean noImovel(Geometry imovel, Geometry ponto) {
        return imovel != null && ponto != null && imovel.intersects(ponto);
    }

    /**
     * {@code focos / area * fator}, com 4 casas. Imovel sem area valida nao tem indice: devolve
     * {@code null} em vez de um zero que pareceria "sem ocorrencia".
     */
    public static BigDecimal indice(long focos, BigDecimal areaImovelHa, BigDecimal fator) {
        if (areaImovelHa == null || areaImovelHa.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return BigDecimal.valueOf(focos)
                .divide(areaImovelHa, 10, RoundingMode.HALF_UP)
                .multiply(fator)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
