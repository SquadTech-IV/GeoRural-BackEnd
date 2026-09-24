package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.locationtech.jts.geom.Geometry;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "FOCO_CALOR")
public class FocoCalor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "foc_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_bruto_id")
    private ArquivoBruto arquivoBruto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id")
    private Versao versao;

    @Column(name = "foc_data")
    private LocalDate data;

    @Column(name = "foc_latitude")
    private BigDecimal latitude;

    @Column(name = "foc_longitude")
    private BigDecimal longitude;

    @Column(name = "foc_geometria")
    private Geometry geometria;
}
