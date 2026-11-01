package br.com.fatec.georural.service.pipeline.validacao;

import br.com.fatec.georural.service.pipeline.leitor.FeicaoLida;
import br.com.fatec.georural.service.pipeline.perfil.CampoCamada;
import br.com.fatec.georural.service.pipeline.perfil.PerfilCamada;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// traduz a feicao crua para o esquema do GeoRural e lista os problemas dela
@Component
@RequiredArgsConstructor
public class PadronizadorFeicao {

    // envelope do Parana com folga, igual ao SDO_DIM registrado no V1
    private static final Envelope ENVELOPE_PR = new Envelope(-55.0, -48.0, -27.0, -22.0);

    private final ReprojetorFeicao reprojetor;

    public FeicaoPadronizada padronizar(PerfilCamada perfil, FeicaoLida feicao, Integer srsOrigem) {
        List<ProblemaFeicao> problemas = new ArrayList<>();
        Map<String, Object> brutos = indexarPorNomeMinusculo(feicao.atributos());
        Map<String, Object> padronizados = new LinkedHashMap<>();

        for (CampoCamada campo : perfil.campos()) {
            Object bruto = primeiroValor(brutos, campo);
            if (bruto == null) {
                padronizados.put(campo.nome(), null);
                problemas.add(campo.obrigatorio()
                        ? ProblemaFeicao.erro(RegrasQualidade.CAMPO_OBRIGATORIO_AUSENTE,
                                campo.nome(), null, campo.nomesAceitos().toString(),
                                "A camada " + perfil.rotulo() + " exige o campo '" + campo.nome()
                                        + "'. Nenhum dos nomes aceitos foi encontrado no arquivo.")
                        : ProblemaFeicao.aviso(RegrasQualidade.CAMPO_OPCIONAL_AUSENTE,
                                campo.nome(), null, campo.nomesAceitos().toString(),
                                "Campo opcional '" + campo.nome() + "' nao veio no arquivo."));
                continue;
            }
            try {
                padronizados.put(campo.nome(), campo.tipo().converter(bruto));
            } catch (RuntimeException e) {
                padronizados.put(campo.nome(), null);
                problemas.add(ProblemaFeicao.erro(RegrasQualidade.TIPO_INVALIDO,
                        campo.nome(), String.valueOf(bruto), campo.tipo().exemploEsperado(),
                        "O campo '" + campo.nome() + "' deveria ser " + campo.tipo().descricao()
                                + " e veio como '" + bruto + "'."));
            }
        }

        String identificador = texto(padronizados.get(perfil.campoIdentificador()));
        Geometry geometria = avaliarGeometria(perfil, feicao, srsOrigem, identificador, problemas);

        return new FeicaoPadronizada(identificador, feicao.linha(), padronizados,
                feicao.atributos(), geometria, problemas);
    }

    // verifica presenca, tipo e posicao da geometria, e reprojeta para 4674
    private Geometry avaliarGeometria(PerfilCamada perfil, FeicaoLida feicao, Integer srsOrigem,
                                      String identificador, List<ProblemaFeicao> problemas) {
        if (!feicao.temGeometria()) {
            if (perfil.exigeGeometria()) {
                problemas.add(ProblemaFeicao.erro(RegrasQualidade.GEOMETRIA_AUSENTE,
                        "geometria", null, perfil.geometriaEsperada().descricao(),
                        "A feicao " + rotuloFeicao(identificador, feicao.linha())
                                + " nao tem geometria, e a camada " + perfil.rotulo() + " exige."));
            }
            return null;
        }

        Geometry original = feicao.geometria();
        if (!perfil.geometriaEsperada().aceita(original)) {
            problemas.add(ProblemaFeicao.erro(RegrasQualidade.GEOMETRIA_INVALIDA,
                    "geometria", original.getGeometryType(), perfil.geometriaEsperada().descricao(),
                    "A camada " + perfil.rotulo() + " espera " + perfil.geometriaEsperada().descricao()
                            + " e a feicao veio como " + original.getGeometryType() + "."));
            return null;
        }

        Geometry em4674;
        try {
            em4674 = reprojetor.para4674(original, srsOrigem);
        } catch (ReprojetorFeicao.ReprojecaoFalhouException e) {
            problemas.add(ProblemaFeicao.erro(RegrasQualidade.SRC_AUSENTE_OU_DIVERGENTE,
                    "geometria", "EPSG:" + srsOrigem, "EPSG:" + ReprojetorFeicao.SRID_DESTINO,
                    e.getMessage()));
            return null;
        }

        if (perfil.exigeDentroDoParana() && !ENVELOPE_PR.intersects(em4674.getEnvelopeInternal())) {
            problemas.add(ProblemaFeicao.erro(RegrasQualidade.FORA_DA_AREA_DE_INTERESSE,
                    "geometria", resumo(em4674), "longitude -55 a -48 e latitude -27 a -22",
                    "A feicao " + rotuloFeicao(identificador, feicao.linha())
                            + " esta fora da faixa de coordenadas do Parana. Quando a diferenca e"
                            + " grande, normalmente o SRC do arquivo nao e o informado."));
            return null;
        }
        return em4674;
    }

    // mapa de atributos com as chaves em minusculo, para casar com os aliases
    private Map<String, Object> indexarPorNomeMinusculo(Map<String, Object> atributos) {
        Map<String, Object> indexado = new LinkedHashMap<>();
        if (atributos != null) {
            atributos.forEach((chave, valor) -> {
                if (chave != null) {
                    indexado.putIfAbsent(chave.trim().toLowerCase(Locale.ROOT), valor);
                }
            });
        }
        return indexado;
    }

    // primeiro nome aceito que exista no arquivo e tenha valor
    private Object primeiroValor(Map<String, Object> brutos, CampoCamada campo) {
        for (String nome : campo.nomesAceitos()) {
            Object valor = brutos.get(nome);
            if (valor != null && !String.valueOf(valor).trim().isEmpty()) {
                return valor;
            }
        }
        return null;
    }

    private String rotuloFeicao(String identificador, long linha) {
        return identificador != null ? "'" + identificador + "'" : "da linha " + linha;
    }

    private String resumo(Geometry geometria) {
        Envelope e = geometria.getEnvelopeInternal();
        return String.format(Locale.ROOT, "lon %.4f a %.4f, lat %.4f a %.4f",
                e.getMinX(), e.getMaxX(), e.getMinY(), e.getMaxY());
    }

    private String texto(Object valor) {
        return valor == null ? null : String.valueOf(valor).trim();
    }
}
