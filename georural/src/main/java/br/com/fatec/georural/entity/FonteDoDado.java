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
@Table(name = "FONTE_DO_DADO")
public class FonteDoDado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fon_id")
    private Long id;

    @Column(name = "fon_nome", nullable = false)
    private String nome;

    @Column(name = "fon_sigla")
    private String sigla;

    @Column(name = "fon_esfera")
    private String esfera;

    @Column(name = "fon_status", nullable = false)
    private String status = "S";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usu_id")
    private Usuario usuario;

    @Column(name = "fon_data_cadastro", nullable = false)
    private LocalDateTime dataCadastro;
}
