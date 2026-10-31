package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;
import java.time.LocalDateTime;


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

    /** PIPELINE ou CONFERENCIA. */
    @Column(name = "stg_origem", nullable = false, length = 12)
    private String origem = "PIPELINE";

    /** Identificador natural da feicao (CAR, codigo do embargo...). */
    @Column(name = "stg_identificador", length = 120)
    private String identificador;

    /** Numero da feicao no arquivo de origem, para apontar o erro na quarentena. */
    @Column(name = "stg_linha")
    private Long linha;

    /** JSON com os atributos como vieram do arquivo, sem padronizacao. */
    @Lob
    @Column(name = "stg_atributos")
    private String atributos;

    @Column(name = "stg_geometria")
    private Geometry geometria;

    @Column(name = "stg_data", nullable = false)
    private LocalDateTime data;
}
