package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "RESULTADO_MUNICIPIO")
public class ResultadoMunicipio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rmu_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mun_id", nullable = false)
    private Municipio municipio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ind_id", nullable = false)
    private Indicador indicador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id", nullable = false)
    private Versao versao;

    @Column(name = "rmu_valor_agregado")
    private BigDecimal valorAgregado;

    @Column(name = "rmu_data_calculo", nullable = false)
    private LocalDateTime dataCalculo;
}
