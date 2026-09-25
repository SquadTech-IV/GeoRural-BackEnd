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
@Table(name = "EXECUCAO_PIPELINE")
public class ExecucaoPipeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exe_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "con_id", nullable = false)
    private ConjuntoDados conjunto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usu_id")
    private Usuario usuario;

    @Column(name = "exe_etapa", nullable = false)
    private String etapa;

    @Column(name = "exe_dag_run_id")
    private String dagRunId;

    @Column(name = "exe_status", nullable = false)
    private String status;

    @Column(name = "exe_registros_processados")
    private Long registrosProcessados;

    @Column(name = "exe_registros_rejeitados")
    private Long registrosRejeitados;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exe_ver_gerada_id")
    private Versao versaoGerada;

    @Column(name = "exe_inicio", nullable = false)
    private LocalDateTime inicio;

    @Column(name = "exe_fim")
    private LocalDateTime fim;

    @Column(name = "exe_duracao_seg")
    private Long duracaoSeg;
}
