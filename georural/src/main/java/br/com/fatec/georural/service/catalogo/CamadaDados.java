package br.com.fatec.georural.service.catalogo;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Onde ficam os dados tratados de cada tipo de camada, e quais campos a API publica.
public enum CamadaDados {

    IMOVEL("IMOVEL_RURAL", "imo_id", "ver_id", "imo_geometria", "imo_id", List.of(
            Campo.inteiro("arquivoId", "arq_bruto_id"),
            Campo.inteiro("versaoId", "ver_id"),
            Campo.texto("codigoCar", "imo_codigo_car"),
            Campo.texto("nome", "imo_nome"),
            Campo.texto("nomeOrigem", "imo_nome_origem"),
            Campo.numero("areaTotalHa", "imo_area_total"),
            Campo.dataHora("dataInformada", "imo_data_informada"),
            Campo.inteiro("municipioId", "mun_id")
    )),

    APP("APP", "app_id", "ver_id", "app_geometria", "imo_id", List.of(
            Campo.inteiro("arquivoId", "arq_bruto_id"),
            Campo.inteiro("versaoId", "ver_id"),
            Campo.inteiro("imovelId", "imo_id"),
            Campo.numero("areaHa", "app_area_ha")
    )),

    RESERVA_LEGAL("RESERVA_LEGAL", "rle_id", "ver_id", "rle_geometria", "imo_id", List.of(
            Campo.inteiro("arquivoId", "arq_bruto_id"),
            Campo.inteiro("versaoId", "ver_id"),
            Campo.inteiro("imovelId", "imo_id"),
            Campo.numero("areaHa", "rle_area_ha")
    )),

    VEGETACAO("VEGETACAO_NATIVA", "veg_id", "ver_id", "veg_geometria", null, List.of(
            Campo.inteiro("arquivoId", "arq_bruto_id"),
            Campo.inteiro("versaoId", "ver_id"),
            Campo.numero("areaHa", "veg_area_ha")
    )),

    BIOMA("BIOMA", "bio_id", null, "bio_geometria", null, List.of(
            Campo.inteiro("arquivoId", "arq_bruto_id"),
            Campo.texto("nome", "bio_nome"),
            Campo.numero("percentualMinimoRl", "bio_perc_minimo_rl")
    )),

    AREA_PROTEGIDA("AREA_PROTEGIDA", "apr_id", "ver_id", "apr_geometria", null, List.of(
            Campo.inteiro("arquivoId", "arq_bruto_id"),
            Campo.inteiro("versaoId", "ver_id"),
            Campo.texto("tipo", "apr_tipo"),
            Campo.texto("nome", "apr_nome")
    )),

    EMBARGO("EMBARGO", "emb_id", "ver_id", "emb_geometria", null, List.of(
            Campo.inteiro("arquivoId", "arq_bruto_id"),
            Campo.inteiro("versaoId", "ver_id"),
            Campo.texto("identificador", "emb_identificador")
    )),

    DESMATAMENTO("DESMATAMENTO", "des_id", "ver_id", "des_geometria", null, List.of(
            Campo.inteiro("arquivoId", "arq_bruto_id"),
            Campo.inteiro("versaoId", "ver_id"),
            Campo.inteiro("anoReferencia", "des_ano_ref"),
            Campo.data("dataCorte", "des_data_corte")
    )),

    FOCO_CALOR("FOCO_CALOR", "foc_id", "ver_id", "foc_geometria", null, List.of(
            Campo.inteiro("arquivoId", "arq_bruto_id"),
            Campo.inteiro("versaoId", "ver_id"),
            Campo.data("data", "foc_data"),
            Campo.numero("latitude", "foc_latitude"),
            Campo.numero("longitude", "foc_longitude")
    )),

    MALHA_MUNICIPAL("MUNICIPIO", "mun_id", null, "mun_geometria", null, List.of(
            Campo.inteiro("arquivoId", "arq_bruto_id"),
            Campo.texto("codigoOrigem", "mun_codigo_origem"),
            Campo.texto("nome", "mun_nome"),
            Campo.texto("uf", "mun_uf"),
            Campo.numero("areaHa", "mun_area_ha")
    ));

    // Tipo de dado do campo, que decide como ele e lido e se entra na busca textual.
    public enum Tipo { TEXTO, NUMERO, INTEIRO, DATA, DATA_HORA }

    public record Campo(String nome, String coluna, Tipo tipo) {

        static Campo texto(String nome, String coluna) {
            return new Campo(nome, coluna, Tipo.TEXTO);
        }

        static Campo numero(String nome, String coluna) {
            return new Campo(nome, coluna, Tipo.NUMERO);
        }

        static Campo inteiro(String nome, String coluna) {
            return new Campo(nome, coluna, Tipo.INTEIRO);
        }

        static Campo data(String nome, String coluna) {
            return new Campo(nome, coluna, Tipo.DATA);
        }

        static Campo dataHora(String nome, String coluna) {
            return new Campo(nome, coluna, Tipo.DATA_HORA);
        }
    }

    private final String tabela;
    private final String chave;
    private final String colunaVersao;
    private final String colunaGeometria;
    private final String colunaImovel;
    private final List<Campo> campos;

    CamadaDados(
            String tabela,
            String chave,
            String colunaVersao,
            String colunaGeometria,
            String colunaImovel,
            List<Campo> campos) {
        this.tabela = tabela;
        this.chave = chave;
        this.colunaVersao = colunaVersao;
        this.colunaGeometria = colunaGeometria;
        this.colunaImovel = colunaImovel;
        this.campos = campos;
    }

    public String tabela() {
        return tabela;
    }

    public String chave() {
        return chave;
    }

    public String colunaVersao() {
        return colunaVersao;
    }

    public String colunaGeometria() {
        return colunaGeometria;
    }

    public String colunaImovel() {
        return colunaImovel;
    }

    public List<Campo> campos() {
        return campos;
    }

    public boolean versionada() {
        return colunaVersao != null;
    }

    public boolean filtraPorImovel() {
        return colunaImovel != null;
    }

    public List<Campo> camposDeTexto() {
        return campos.stream()
                .filter(campo -> campo.tipo() == Tipo.TEXTO)
                .toList();
    }

    // Camada correspondente ao codigo do TIPO_CONJUNTO.
    public static Optional<CamadaDados> porTipoConjunto(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }

        String procurado = codigo.trim().toUpperCase(Locale.ROOT);

        return Arrays.stream(values())
                .filter(camada -> camada.name().equals(procurado))
                .findFirst();
    }
}
