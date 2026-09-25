package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;

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

    @Column(name = "apr_nome")
    private String nome;

    @Column(name = "apr_geometria")
    private Geometry geometria;
}
