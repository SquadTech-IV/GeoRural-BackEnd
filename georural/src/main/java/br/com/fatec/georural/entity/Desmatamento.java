package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import org.locationtech.jts.geom.Geometry;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "DESMATAMENTO")
public class Desmatamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "des_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_bruto_id")
    private ArquivoBruto arquivoBruto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id")
    private Versao versao;

    @Column(name = "des_ano_ref")
    private Integer anoRef;

    @Column(name = "des_data_corte")
    private LocalDate dataCorte;

    @Column(name = "des_geometria")
    private Geometry geometria;
}
