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
@Table(name = "ARQUIVO_DATALAKE")
public class ArquivoDatalake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Column(name = "fonte", nullable = false)
    private String fonte;

    @Column(name = "formato", nullable = false)
    private String formato;

    @Column(name = "srid")
    private Integer srid;

    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    @Column(name = "qtd_arquivos", nullable = false)
    private Integer qtdArquivos;

    @Column(name = "caminho")
    private String caminho;

    @Column(name = "situacao", nullable = false)
    private String situacao;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "recebido_em", nullable = false)
    private LocalDateTime recebidoEm;
}