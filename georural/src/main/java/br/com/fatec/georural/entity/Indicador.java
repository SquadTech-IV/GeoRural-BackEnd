package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "INDICADOR")
public class Indicador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ind_id")
    private Long id;

    @Column(name = "ind_sigla", nullable = false)
    private String sigla;

    @Column(name = "ind_nome", nullable = false)
    private String nome;

    @Column(name = "ind_descricao")
    private String descricao;

    @Column(name = "ind_unidade")
    private String unidade;
}
