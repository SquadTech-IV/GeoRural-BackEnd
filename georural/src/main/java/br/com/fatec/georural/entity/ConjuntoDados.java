package br.com.fatec.georural.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Transient;
import jakarta.persistence.ManyToOne;
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
@Table(name = "CONJUNTO_DADOS")
public class ConjuntoDados {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "con_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fon_id", nullable = false)
    private FonteDoDado fonte;

    @Column(name = "con_nome", nullable = false, length = 160)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tip_id", nullable = false)
    private TipoConjunto tipoConjunto;

    @Column(name = "con_descricao", length = 400)
    private String descricao;

    @Column(name = "con_srs")
    private Integer srs;

    @Column(name = "con_cobertura", length = 30)
    private String cobertura;

    @Column(name = "con_periodicidade", length = 20)
    private String periodicidade;

    @Column(name = "con_situacao", nullable = false, length = 15)
    private String situacao = "RASCUNHO";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "con_ver_vigente_id")
    private Versao versaoVigente;

    @Column(name = "con_data_cadastro", nullable = false)
    private LocalDateTime dataCadastro;

    @Column(name = "con_forma_entrada", nullable = false, length = 10)
    private String formaEntrada = "UPLOAD";

    @Column(name = "con_api_endpoint", length = 500)
    private String endpoint;

    @Column(name = "con_api_formato", length = 20)
    private String formatoApi;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "con_api_auth", length = 200)
    private String autenticacao;

    @Transient
    public String getTipoCamada() {
        return tipoConjunto == null ? null : tipoConjunto.getCodigo();
    }

    @Transient
    public String getEsquemaTecnico() {
        return tipoConjunto == null ? null : tipoConjunto.getEsquema();
    }

    @Transient
    public String getCompetencia() {
        return versaoVigente == null ? null : versaoVigente.getCompetencia();
    }

    @Transient
    public boolean permiteUpload() {
        return "UPLOAD".equals(formaEntrada) || "AMBOS".equals(formaEntrada);
    }

    @Transient
    public boolean permiteApi() {
        return "API".equals(formaEntrada) || "AMBOS".equals(formaEntrada);
    }

    @PrePersist
    void prepararCadastro() {
        if (dataCadastro == null) {
            dataCadastro = LocalDateTime.now();
        }

        if (situacao == null || situacao.isBlank()) {
            situacao = "RASCUNHO";
        }
    }
}