package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// area de pouso das feicoes lidas do arquivo
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "FEICAO_STAGING")
public class FeicaoStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stg_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exe_id", nullable = false)
    private ExecucaoPipeline execucao;

    // camada de onde a feicao veio, dentro do zip
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stg_arq_bruto_id")
    private ArquivoBruto arquivoBruto;

    // PIPELINE ou CONFERENCIA
    @Column(name = "stg_origem", nullable = false, length = 12)
    private String origem = "PIPELINE";

    // identificador natural da feicao (CAR, codigo do embargo...)
    @Column(name = "stg_identificador", length = 120)
    private String identificador;

    // numero da feicao no arquivo de origem
    @Column(name = "stg_linha")
    private Long linha;

    // JSON com os atributos ja padronizados pelo perfil da camada
    @Lob
    @Column(name = "stg_atributos")
    private String atributos;

    @Column(name = "stg_geometria")
    private Geometry geometria;

    // area em hectares, calculada uma vez no tratamento
    @Column(name = "stg_area_ha")
    private BigDecimal areaHa;

    @Column(name = "stg_data", nullable = false)
    private LocalDateTime data;
}
