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
@Table(name = "ARQUIVO_BRUTO")
public class ArquivoBruto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "arq_bruto_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "con_id", nullable = false)
    private ConjuntoDados conjunto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usu_id")
    private Usuario usuario;

    @Column(name = "arq_nome", nullable = false)
    private String nome;

    @Column(name = "arq_formato")
    private String formato;

    @Column(name = "arq_path_armazenamento", nullable = false)
    private String pathArmazenamento;

    @Column(name = "arq_tamanho_bytes")
    private Long tamanhoBytes;

    @Column(name = "arq_status")
    private String status;

    @Column(name = "arq_hash", nullable = false)
    private String hash;

    @Column(name = "arq_data_upload", nullable = false)
    private LocalDateTime dataUpload;
}
