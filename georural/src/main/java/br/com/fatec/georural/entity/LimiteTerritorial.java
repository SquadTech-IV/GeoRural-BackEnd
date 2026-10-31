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
@Table(name = "LIMITE_TERRITORIAL")
public class LimiteTerritorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lim_id")
    private Long id;

    @Column(name = "lim_tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "lim_codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "lim_nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "lim_geometria")
    private Geometry geometria;
}
