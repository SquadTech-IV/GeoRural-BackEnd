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
@Table(name = "RESULTADO_ISAP_RESUMO")
public class ResultadoIsapResumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "isr_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imo_id", nullable = false)
    private ImovelRural imovel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id", nullable = false)
    private Versao versao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exe_id")
    private ExecucaoPipeline execucao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rca_id")
    private RegraCalculo regraCalculo;

    @Column(name = "isr_area_imovel_ha", nullable = false)
    private BigDecimal areaImovelHa;

    @Column(name = "isr_area_sobreposta_total_ha", nullable = false)
    private BigDecimal areaSobrepostaTotalHa;

    @Column(name = "isr_percentual_total", nullable = false)
    private BigDecimal percentualTotal;

    @Column(name = "isr_situacao", nullable = false, length = 25)
    private String situacao;

    @Column(name = "isr_qtd_categorias", nullable = false)
    private Integer qtdCategorias;

    @Column(name = "isr_qtd_areas", nullable = false)
    private Integer qtdAreas;

    @Column(name = "isr_vigente", nullable = false, length = 1)
    private String vigente;

    @Column(name = "isr_data_calculo", nullable = false)
    private LocalDateTime dataCalculo;
}