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
@Table(name = "RESULTADO_INDICADOR")
public class ResultadoIndicador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rin_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imo_id", nullable = false)
    private ImovelRural imovel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ind_id", nullable = false)
    private Indicador indicador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id", nullable = false)
    private Versao versao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exe_id")
    private ExecucaoPipeline execucao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rca_id")
    private RegraCalculo regraCalculo;

    @Column(name = "rin_valor_percentual")
    private BigDecimal valorPercentual;

    @Column(name = "rin_valor_hectares")
    private BigDecimal valorHectares;

    @Column(name = "rin_valor_absoluto")
    private BigDecimal valorAbsoluto;

    @Column(name = "rin_data_calculo", nullable = false)
    private LocalDateTime dataCalculo;
}
