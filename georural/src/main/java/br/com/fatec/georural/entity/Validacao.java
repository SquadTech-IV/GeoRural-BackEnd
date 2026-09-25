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
@Table(name = "VALIDACAO")
public class Validacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "val_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_bruto_id", nullable = false)
    private ArquivoBruto arquivoBruto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exe_id")
    private ExecucaoPipeline execucao;

    @Column(name = "val_total_registros")
    private Long totalRegistros;

    @Column(name = "val_registros_validos")
    private Long registrosValidos;

    @Column(name = "val_registros_invalidos")
    private Long registrosInvalidos;

    @Column(name = "val_status_final")
    private String statusFinal;

    @Column(name = "val_data_execucao", nullable = false)
    private LocalDateTime dataExecucao;
}
