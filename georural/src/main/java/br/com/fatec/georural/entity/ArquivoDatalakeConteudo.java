package br.com.fatec.georural.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "ARQUIVO_DATALAKE_CONTEUDO")
public class ArquivoDatalakeConteudo {

    @Id
    @Column(name = "arquivo_id")
    private Long arquivoId;           // mesmo id do ARQUIVO_DATALAKE (1:1)

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Lob
    @Column(name = "conteudo", nullable = false)
    private byte[] conteudo;
}