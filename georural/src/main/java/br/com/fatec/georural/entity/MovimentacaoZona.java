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
@Table(name = "MOVIMENTACAO_ZONA")
public class MovimentacaoZona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mov_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_bruto_id", nullable = false)
    private ArquivoBruto arquivoBruto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exe_id")
    private ExecucaoPipeline execucao;

    @Column(name = "mov_zona", nullable = false)
    private String zona;

    @Column(name = "mov_status")
    private String status;

    @Column(name = "mov_obs")
    private String observacao;

    @Column(name = "mov_data", nullable = false)
    private LocalDateTime data;
}
