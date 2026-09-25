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
@Table(name = "TRANSFORMACAO")
public class Transformacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tra_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exe_id", nullable = false)
    private ExecucaoPipeline execucao;

    @Column(name = "tra_ordem", nullable = false)
    private Integer ordem;

    @Column(name = "tra_tipo")
    private String tipo;

    @Column(name = "tra_descricao", nullable = false)
    private String descricao;

    @Column(name = "tra_data", nullable = false)
    private LocalDateTime data;
}
