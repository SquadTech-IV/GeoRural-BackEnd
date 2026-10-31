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
@Table(name = "COLETA_API")
public class ColetaApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "col_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_bruto_id", nullable = false, unique = true)
    private ArquivoBruto arquivoBruto;

    @Column(name = "col_endpoint", nullable = false, length = 1000)
    private String endpoint;

    @Column(name = "col_metodo", nullable = false, length = 10)
    private String metodo = "GET";

    @Lob
    @Column(name = "col_parametros")
    private String parametros;

    @Column(name = "col_competencia", length = 20)
    private String competencia;

    @Column(name = "col_http_status")
    private Integer httpStatus;

    @Column(name = "col_registros")
    private Long registros;

    @Column(name = "col_data_coleta", nullable = false)
    private LocalDateTime dataColeta;
}
