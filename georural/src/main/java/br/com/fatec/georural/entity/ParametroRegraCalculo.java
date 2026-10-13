package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "PARAMETRO_REGRA_CALCULO")
public class ParametroRegraCalculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prc_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rca_id", nullable = false)
    private RegraCalculo regraCalculo;

    @Column(name = "prc_nome", nullable = false)
    private String nome;

    @Column(name = "prc_tipo", nullable = false)
    private String tipo;

    @Column(name = "prc_unidade")
    private String unidade;

    @Column(name = "prc_valor", nullable = false)
    private String valor;
}