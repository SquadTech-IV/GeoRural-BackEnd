package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "REGRA_QUALIDADE")
public class RegraQualidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reg_id")
    private Long id;

    @Column(name = "reg_nome", nullable = false)
    private String nome;

    @Column(name = "reg_tipo", nullable = false)
    private String tipo;

    @Column(name = "reg_descricao")
    private String descricao;

    @Column(name = "reg_severidade", nullable = false)
    private String severidade;

    @Column(name = "reg_ativa", nullable = false)
    private String ativa;
}
