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
@Table(name = "MUNICIPIO")
public class Municipio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mun_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_bruto_id")
    private ArquivoBruto arquivoBruto;

    @Column(name = "mun_codigo_origem", nullable = false)
    private String codigoOrigem;

    @Column(name = "mun_nome", nullable = false)
    private String nome;

    @Column(name = "mun_uf")
    private String uf;

    @Column(name = "mun_area_ha")
    private BigDecimal areaHa;

    @Column(name = "mun_geometria")
    private Geometry geometria;
}
