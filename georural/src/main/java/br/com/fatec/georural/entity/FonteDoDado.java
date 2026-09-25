package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "FONTE_DO_DADO")
public class FonteDoDado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fon_id")
    private Long id;

    @Column(name = "fon_nome", nullable = false)
    private String nome;

    @Column(name = "fon_orgao")
    private String orgao;

    @Lob
    @Column(name = "fon_esquema_tecnico")
    private String esquemaTecnico;

    @Column(name = "fon_crs_origem")
    private Integer crsOrigem;

    @Column(name = "fon_competencia")
    private String competencia;

    @Column(name = "fon_data_cadastro")
    private LocalDateTime dataCadastro;

    @Column(name = "fon_status", nullable = false)
    private String status;

    @Column(name = "fon_cobertura_temporal_inicio")
    private LocalDate coberturaTemporalInicio;

    @Column(name = "fon_cobertura_temporal_fim")
    private LocalDate coberturaTemporalFim;
}
