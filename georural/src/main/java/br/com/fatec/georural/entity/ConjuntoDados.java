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
@Table(name = "CONJUNTO_DADOS")
public class ConjuntoDados {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "con_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fon_id", nullable = false)
    private FonteDoDado fonte;

    @Column(name = "con_nome", nullable = false)
    private String nome;

    @Column(name = "con_tipo_camada", nullable = false)
    private String tipoCamada;

    @Column(name = "con_descricao")
    private String descricao;

    @Column(name = "con_competencia")
    private String competencia;

    @Column(name = "con_situacao", nullable = false)
    private String situacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "con_ver_vigente_id")
    private Versao versaoVigente;

    @Column(name = "con_data_cadastro", nullable = false)
    private LocalDateTime dataCadastro;
}
