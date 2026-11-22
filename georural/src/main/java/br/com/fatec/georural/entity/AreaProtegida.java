package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "AREA_PROTEGIDA")
public class AreaProtegida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "apr_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_bruto_id")
    private ArquivoBruto arquivoBruto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id")
    private Versao versao;

    @Column(name = "apr_tipo", nullable = false)
    private String tipo;

    @Column(name = "apr_categoria", length = 30)
    private String categoria;

    @Column(name = "apr_subcategoria", length = 80)
    private String subcategoria;

    @Column(name = "apr_nome")
    private String nome;

    @Column(name = "apr_codigo_origem", length = 80)
    private String codigoOrigem;

    @Column(name = "apr_esfera", length = 20)
    private String esfera;

    @Column(name = "apr_area_ha")
    private BigDecimal areaHa;

    @Column(name = "apr_geometria")
    private Geometry geometria;
}
