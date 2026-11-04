package br.com.fatec.georural.service.pipeline.validacao;

import lombok.extern.slf4j.Slf4j;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// leva a geometria para SIRGAS 2000 (EPSG:4674), que e o SRC do banco
@Slf4j
@Component
public class ReprojetorFeicao {

    public static final int SRID_DESTINO = 4674;

    private final Map<Integer, MathTransform> transformacoes = new ConcurrentHashMap<>();

    public Geometry para4674(Geometry geometria, Integer srsOrigem) {
        if (geometria == null) {
            return null;
        }
        int origem = srsOrigem == null ? SRID_DESTINO : srsOrigem;
        if (origem == SRID_DESTINO) {
            geometria.setSRID(SRID_DESTINO);
            return geometria;
        }
        try {
            Geometry convertida = JTS.transform(geometria, transformacao(origem));
            convertida.setSRID(SRID_DESTINO);
            return convertida;
        } catch (Exception e) {
            throw new ReprojecaoFalhouException(origem, e);
        }
    }

    private MathTransform transformacao(int origem) {
        return transformacoes.computeIfAbsent(origem, srid -> {
            try {
                CoordinateReferenceSystem de = CRS.decode("EPSG:" + srid, true);
                CoordinateReferenceSystem para = CRS.decode("EPSG:" + SRID_DESTINO, true);
                log.info("Reprojecao EPSG:{} -> EPSG:{} preparada.", srid, SRID_DESTINO);
                return CRS.findMathTransform(de, para, true);
            } catch (Exception e) {
                throw new ReprojecaoFalhouException(srid, e);
            }
        });
    }

    public static class ReprojecaoFalhouException extends RuntimeException {
        public ReprojecaoFalhouException(int srid, Throwable causa) {
            super("Nao foi possivel reprojetar de EPSG:" + srid + " para EPSG:" + SRID_DESTINO
                    + ". Confira o SRC do arquivo e o cadastro do conjunto. Detalhe: "
                    + causa.getMessage(), causa);
        }
    }
}
