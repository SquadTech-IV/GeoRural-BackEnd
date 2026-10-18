package br.com.fatec.georural.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Lob;

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
    private TipoConjunto tipoConjunto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usu_id")
    private Usuario usuario;

    @Column(name = "con_nome", nullable = false, length = 160)
    private String nome;

    @Column(name = "con_tipo_camada", nullable = false, length = 30)
    private String tipoCamada;

    @Column(name = "con_descricao", length = 400)
    private String descricao;

    @Lob
    @Column(name = "con_esquema_tecnico")
    private String esquemaTecnico;

    @Column(name = "con_srs")
    private Integer srs;

    @Column(name = "con_competencia", length = 40)
    private String competencia;

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

    @JsonIgnore
    @Column(name = "con_api_auth", length = 200)
    private String autenticacao;

    @Transient
    @JsonIgnore
    public TipoConjunto getTipo() {
        return tipoConjunto;
    }

    public void setTipo(TipoConjunto tipo) {
        this.tipoConjunto = tipo;
    }

    @Transient
    public String getApiEndpoint() {
        return endpoint;
    }

    public void setApiEndpoint(String apiEndpoint) {
        this.endpoint = apiEndpoint;
    }

    @Transient
    public String getApiFormato() {
        return formatoApi;
    }

    public void setApiFormato(String apiFormato) {
        this.formatoApi = apiFormato;
    }

    @Transient
    @JsonIgnore
    public String getApiAuth() {
        return autenticacao;
    }

    public void setApiAuth(String apiAuth) {
        this.autenticacao = apiAuth;
    }

    @Transient
    public String getTipoCamada() {
        return tipoConjunto == null
                ? null
                : tipoConjunto.getCodigo();
    }

    @Transient
    public String getEsquemaTecnico() {
        return tipoConjunto == null
                ? null
                : tipoConjunto.getEsquema();
    }

    @Transient
    public String getCompetencia() {
        return versaoVigente == null
                ? null
                : versaoVigente.getCompetencia();
    }

    @Transient
    public boolean permiteUpload() {
        return "UPLOAD".equals(formaEntrada)
                || "AMBOS".equals(formaEntrada);
    }

    @Transient
    public boolean permiteApi() {
        return "API".equals(formaEntrada)
                || "AMBOS".equals(formaEntrada);
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