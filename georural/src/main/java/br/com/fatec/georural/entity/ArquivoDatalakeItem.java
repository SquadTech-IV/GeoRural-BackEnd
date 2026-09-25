package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ARQUIVO_DATALAKE_ITEM")
public class ArquivoDatalakeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arquivo_id", nullable = false)
    private ArquivoDatalake arquivo;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "formato")
    private String formato;

    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    @Column(name = "descricao")
    private String descricao;
}