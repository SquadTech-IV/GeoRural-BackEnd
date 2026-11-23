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
    @JoinColumn(name = "isr_camada_ver_id")
    private Versao camadaVersao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exe_id")
    private ExecucaoPipeline execucao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rca_id")
    private RegraCalculo regraCalculo;

    @Column(name = "isr_categoria", nullable = false, length = 20)
    private String categoria;

    @Column(name = "isr_sobrepoe", length = 1)
    private String sobrepoe;

    @Column(name = "isr_area_sobreposta_ha")
    private BigDecimal areaSobrepostaHa;

    @Column(name = "isr_pct_sobreposicao")
    private BigDecimal pctSobreposicao;

    @Column(name = "isr_area_imovel_ha", nullable = false)
    private BigDecimal areaImovelHa;

    @Column(name = "isr_filtro_aplicado", length = 500)
    private String filtroAplicado;

    @Column(name = "isr_competencia_camada", length = 20)
    private String competenciaCamada;

    @Column(name = "isr_status", nullable = false, length = 25)
    private String status;

    @Column(name = "isr_vigente", nullable = false, length = 1)
    private String vigente;

    @Column(name = "isr_data_calculo", nullable = false)
    private LocalDateTime dataCalculo;
}