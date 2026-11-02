package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Geometry;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "IMOVEL_RURAL")
public class ImovelRural {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "imo_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mun_id")
    private Municipio municipio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_bruto_id")
    private ArquivoBruto arquivoBruto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id")
    private Versao versao;

    @Column(name = "imo_codigo_car", nullable = false, unique = true)
    private String codigoCar;

    // nome do imovel, quando existe; o SICAR publico nao traz
    @Column(name = "imo_nome", length = 200)
    private String nome;

    // ARQUIVO, MANUAL ou PLANILHA
    @Column(name = "imo_nome_origem", length = 12)
    private String nomeOrigem;

    @Column(name = "imo_geometria")
    private Geometry geometria;

    @Column(name = "imo_area_total")
    private BigDecimal areaTotal;

    @Column(name = "imo_data_informada")
    private LocalDateTime dataInformada;

}
