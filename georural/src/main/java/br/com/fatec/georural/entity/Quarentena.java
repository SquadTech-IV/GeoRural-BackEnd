package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;


@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "QUARENTENA")
public class Quarentena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qua_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "val_id", nullable = false)
    private Validacao validacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reg_id")
    private RegraQualidade regra;

    /** INGESTAO, VALIDACAO, TRATAMENTO, CALCULO ou QUALIDADE. */
    @Column(name = "qua_etapa", length = 15)
    private String etapa;

    /** Identificador da feicao rejeitada. */
    @Column(name = "qua_identificador", length = 120)
    private String identificador;

    @Column(name = "qua_linha_origem")
    private Long linhaOrigem;

    @Column(name = "qua_coluna_erro")
    private String colunaErro;

    @Column(name = "qua_valor_encontrado", length = 400)
    private String valorEncontrado;

    @Column(name = "qua_valor_esperado", length = 400)
    private String valorEsperado;

    @Column(name = "qua_motivo_erro", nullable = false)
    private String motivoErro;

    @Lob
    @Column(name = "qua_dado_original")
    private String dadoOriginal;

    @Column(name = "qua_situacao", nullable = false)
    private String situacao;

    @Column(name = "qua_data_registro", nullable = false)
    private LocalDateTime dataRegistro;

    @Column(name = "qua_data_resolucao")
    private LocalDateTime dataResolucao;
}
