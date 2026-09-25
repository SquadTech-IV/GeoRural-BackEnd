package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import org.locationtech.jts.geom.Geometry;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "VEGETACAO_NATIVA")
public class VegetacaoNativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "veg_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_bruto_id")
    private ArquivoBruto arquivoBruto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id")
    private Versao versao;

    @Column(name = "veg_area_ha")
    private BigDecimal areaHa;

    @Column(name = "veg_geometria")
    private Geometry geometria;
}
