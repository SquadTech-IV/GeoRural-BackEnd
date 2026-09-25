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
@Table(name = "AUDITORIA")
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aud_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usu_id")
    private Usuario usuario;

    @Column(name = "aud_operacao", nullable = false)
    private String operacao;

    @Column(name = "aud_entidade_afetada", nullable = false)
    private String entidadeAfetada;

    @Column(name = "aud_registro_id")
    private Long registroId;

    @Lob
    @Column(name = "aud_dados_antigos")
    private String dadosAntigos;

    @Lob
    @Column(name = "aud_dados_novos")
    private String dadosNovos;

    @Column(name = "aud_ip_origem")
    private String ipOrigem;

    @Column(name = "aud_data_hora", nullable = false)
    private LocalDateTime dataHora;
}
