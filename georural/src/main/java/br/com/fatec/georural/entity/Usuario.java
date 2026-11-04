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
@Table(name = "USUARIO")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usu_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "per_id", nullable = false)
    private Perfil perfil;

    @Column(name = "usu_keycloak_id", length = 36)
    private String keycloakId;

    @Column(name = "usu_nome", nullable = false)
    private String nome;

    @Column(name = "usu_email", nullable = false)
    private String email;

    @Column(name = "usu_status", nullable = false)
    private String status;

    @Column(name = "usu_ultimo_login")
    private LocalDateTime ultimoLogin;

    @Column(name = "usu_data_cadastro", nullable = false)
    private LocalDateTime dataCadastro;
}
