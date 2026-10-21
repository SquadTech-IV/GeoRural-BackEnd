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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tip_id", nullable = false)
    private TipoConjunto tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usu_id")
    private Usuario usuario;

    @Column(name = "con_nome", nullable = false)
    private String nome;

    @Column(name = "con_descricao")
    private String descricao;

    @Column(name = "con_srs")
    private Integer srs = 4674;

    @Column(name = "con_cobertura")
    private String cobertura;

    @Column(name = "con_periodicidade")
    private String periodicidade;

    @Column(name = "con_forma_entrada", nullable = false)
    private String formaEntrada = "UPLOAD";

    @Column(name = "con_api_endpoint")
    private String apiEndpoint;

    @Column(name = "con_api_formato")
    private String apiFormato;

    @Column(name = "con_api_auth")
    private String apiAuth;

    @Column(name = "con_situacao", nullable = false)
    private String situacao = "RASCUNHO";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "con_ver_vigente_id")
    private Versao versaoVigente;

    @Column(name = "con_data_cadastro", nullable = false)
    private LocalDateTime dataCadastro;
}
