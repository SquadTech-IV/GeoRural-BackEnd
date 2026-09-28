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
@Table(name = "REGRA_CALCULO")
public class RegraCalculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rca_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ind_id", nullable = false)
    private Indicador indicador;

    @Column(name = "rca_versao", nullable = false)
    private String versao;

    @Column(name = "rca_formula", nullable = false)
    private String formula;

    @Column(name = "rca_descricao")
    private String descricao;

    @Column(name = "rca_vigente", nullable = false)
    private String vigente;

    @Column(name = "rca_data_criacao", nullable = false)
    private LocalDateTime dataCriacao;
}
