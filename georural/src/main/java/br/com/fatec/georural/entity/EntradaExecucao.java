package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ENTRADA_EXECUCAO")
public class EntradaExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ent_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exe_id", nullable = false)
    private ExecucaoPipeline execucao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_id", nullable = false)
    private Versao versao;

    @Column(name = "ent_papel")
    private String papel;
}
