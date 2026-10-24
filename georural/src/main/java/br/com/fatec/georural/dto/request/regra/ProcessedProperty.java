package br.com.fatec.georural.dto.request.regra;

import java.math.BigDecimal;

import org.locationtech.jts.geom.Geometry;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProcessedProperty {

    private String recibo;

    private String municipio;

    private String estado;

    private String tema;

    private Geometry geometry4674;

    private BigDecimal areaHa;
}
