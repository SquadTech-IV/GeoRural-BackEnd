package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "TIPO_CONJUNTO")
public class TipoConjunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tip_id")
    private Long id;

    @Column(name = "tip_codigo", nullable = false)
    private String codigo;

    @Column(name = "tip_nome", nullable = false)
    private String nome;

    @Lob
    @Column(name = "tip_esquema")
    private String esquema;

    @Column(name = "tip_formatos")
    private String formatos;

    @Column(name = "tip_indicadores")
    private String indicadores;
}
