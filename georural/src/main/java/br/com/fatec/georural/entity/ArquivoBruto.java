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

    @Column(name = "arq_formato_detectado", length = 15)
    private String formatoDetectado;

    @Column(name = "arq_srs_declarado")
    private Integer srsDeclarado;

    @Column(name = "arq_srs_utilizado")
    private Integer srsUtilizado;

    @Column(name = "arq_path_armazenamento", nullable = false)
    private String pathArmazenamento;

    @Column(name = "arq_tamanho_bytes")
    private Long tamanhoBytes;

    @Column(name = "arq_status")
    private String status;

    @Column(name = "arq_hash", nullable = false)
    private String hash;

    @Column(name = "arq_algoritmo_hash", nullable = false, length = 10)
    private String algoritmoHash = "SHA-256";

    @Column(name = "arq_canal_entrada", nullable = false, length = 10)
    private String canalEntrada = "UPLOAD";

    @Column(name = "arq_competencia", nullable = false, length = 20)
    private String competencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arq_pai_id")
    private ArquivoBruto pai;

    @Column(name = "arq_caminho_interno", length = 500)
    private String caminhoInterno;

    @Column(name = "arq_data_upload", nullable = false)
    private LocalDateTime dataUpload;
}
