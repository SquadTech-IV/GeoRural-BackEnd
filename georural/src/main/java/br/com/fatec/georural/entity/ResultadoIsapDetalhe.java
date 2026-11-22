package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "RESULTADO_ISAP_DETALHE")
public class ResultadoIsapDetalhe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "isd_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "isr_id", nullable = false)
    private ResultadoIsapResumo resumo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apr_id", nullable = false)
    private AreaProtegida areaProtegida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_camada_id", nullable = false)
    private Versao versaoCamada;

    @Column(name = "isd_categoria", nullable = false, length = 30)
    private String categoria;

    @Column(name = "isd_subcategoria", length = 80)
    private String subcategoria;

    @Column(name = "isd_nome_area", nullable = false, length = 200)
    private String nomeArea;

    @Column(name = "isd_codigo_origem", length = 80)
    private String codigoOrigem;

    @Column(name = "isd_esfera", length = 20)
    private String esfera;

    @Column(name = "isd_area_sobreposta_ha", nullable = false)
    private BigDecimal areaSobrepostaHa;

    @Column(name = "isd_percentual_imovel", nullable = false)
    private BigDecimal percentualImovel;
}