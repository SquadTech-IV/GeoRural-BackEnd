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
    @JoinColumn(name = "imo_id", nullable = false)
    private ImovelRural imovel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id", nullable = false)
    private Versao versao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "apr_id")
    private AreaProtegida areaProtegida;

    @Column(name = "isd_categoria", nullable = false, length = 20)
    private String categoria;

    @Column(name = "isd_nome_feicao", length = 255)
    private String nomeFeicao;

    @Column(name = "isd_tipo_feicao", length = 100)
    private String tipoFeicao;

    @Column(name = "isd_codigo_feicao", length = 80)
    private String codigoFeicao;

    @Column(name = "isd_area_ha", nullable = false)
    private BigDecimal areaHa;

    @Column(name = "isd_pct_sobreposicao", nullable = false)
    private BigDecimal pctSobreposicao;
}