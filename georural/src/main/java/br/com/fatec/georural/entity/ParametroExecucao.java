package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "PARAMETRO_EXECUCAO")
public class ParametroExecucao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "par_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exe_id", nullable = false)
    private ExecucaoPipeline execucao;

    @Column(name = "par_nome", nullable = false)
    private String nome;

    @Column(name = "par_valor")
    private String valor;
}
