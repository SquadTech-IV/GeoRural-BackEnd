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
@Table(name = "APP")
public class App {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "app_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imo_id", nullable = false)
    private ImovelRural imovel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_bruto_id")
    private ArquivoBruto arquivoBruto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id")
    private Versao versao;

    @Column(name = "app_area_ha")
    private BigDecimal areaHa;

    @Column(name = "app_geometria")
    private Geometry geometria;
}
