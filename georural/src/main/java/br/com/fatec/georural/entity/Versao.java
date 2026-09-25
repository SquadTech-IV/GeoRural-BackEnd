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
@Table(name = "VERSAO")
public class Versao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ver_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "con_id", nullable = false)
    private ConjuntoDados conjunto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usu_id")
    private Usuario usuario;

    @Column(name = "ver_numero", nullable = false)
    private Long numero;

    @Column(name = "ver_hash", nullable = false)
    private String hash;

    @Column(name = "ver_competencia")
    private String competencia;

    @Column(name = "ver_situacao", nullable = false)
    private String situacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ver_exe_id")
    private ExecucaoPipeline execucao;

    @Column(name = "ver_data_publicacao")
    private LocalDateTime dataPublicacao;

    @Column(name = "ver_data_criacao", nullable = false)
    private LocalDateTime dataCriacao;
}
