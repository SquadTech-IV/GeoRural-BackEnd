package br.com.fatec.georural.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

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

    @Column(name = "fon_nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "fon_sigla", length = 20)
    private String sigla;

    @Column(name = "fon_esfera", length = 20)
    private String esfera;

    @Column(name = "fon_status", nullable = false, length = 1)
    private String status = "S";

    @Column(name = "fon_data_cadastro", nullable = false)
    private LocalDateTime dataCadastro;

    @PrePersist
    void prepararCadastro() {
        if (dataCadastro == null) {
            dataCadastro = LocalDateTime.now();
        }

        if (status == null || status.isBlank()) {
            status = "S";
        }
    }
}
