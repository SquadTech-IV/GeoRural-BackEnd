package br.com.fatec.georural.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

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

    @Column(name = "tip_codigo", nullable = false, length = 30)
    private String codigo;

    @Column(name = "tip_nome", nullable = false, length = 120)
    private String nome;

    @Lob
    @Column(name = "tip_esquema")
    private String esquema;

    @Column(name = "tip_formatos", length = 100)
    private String formatos;

    @Column(name = "tip_indicadores", length = 60)
    private String indicadores;
}