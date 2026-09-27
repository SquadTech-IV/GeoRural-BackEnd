package br.com.fatec.georural.service.treatment.property_area.state;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Municipio;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.MunicipioRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.treatment.property_area.PropertyArea;

public class PropertyAreaSavingState implements PropertyAreaState {

    private static final String TEMA_AREA_BRUTA = "Área do Imóvel";

    @Override
    public void execute(PropertyArea propertyArea) {

        ImovelRuralRepository imovelRepository = SpringContextHolder.getBean(ImovelRuralRepository.class);
        MunicipioRepository municipioRepository = SpringContextHolder.getBean(MunicipioRepository.class);
        VersaoRepository versaoRepository = SpringContextHolder.getBean(VersaoRepository.class);
        ConjuntoDadosRepository conjuntoRepository = SpringContextHolder.getBean(ConjuntoDadosRepository.class);

        ArquivoBruto arquivoBruto = propertyArea.getArquivoBruto();
        Versao versao = propertyArea.getVersao();

        if (arquivoBruto == null || versao == null) {
            throw new IllegalStateException(
                    "ArquivoBruto/Versao ausentes — MetadataResolutionState deveria ter rodado antes");
        }

        int totalSalvos = 0;

        for (SimpleFeature feature : propertyArea.getFeatures()) {

            String tema = (String) feature.getAttribute("tema");
            if (!TEMA_AREA_BRUTA.equals(tema)) {
                continue; // ignora "Área Líquida do Imóvel"
            }

            String nomeMunicipio = (String) feature.getAttribute("municipio");
            Municipio municipio = municipioRepository.findByNome(nomeMunicipio).orElseGet(() -> {
                Municipio novo = new Municipio();
                novo.setNome(nomeMunicipio);

                // Garante limite estrito de 7 caracteres para a coluna mun_codigo_origem CHAR(7)
                String codigoTemp = nomeMunicipio.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
                if (codigoTemp.length() > 7) {
                    codigoTemp = codigoTemp.substring(0, 7);
                } else if (codigoTemp.length() < 7) {
                    codigoTemp = String.format("%-7s", codigoTemp).replace(' ', '0');
                }

                novo.setCodigoOrigem(codigoTemp);
                novo.setUf("PR");
                novo.setArquivoBruto(arquivoBruto);
                return municipioRepository.save(novo);
            });

            ImovelRural imovel = new ImovelRural();
            imovel.setCodigoCar((String) feature.getAttribute("recibo"));
            imovel.setGeometria((Geometry) feature.getDefaultGeometry());

            Number area = (Number) feature.getAttribute("area");
            imovel.setAreaTotal(BigDecimal.valueOf(area.doubleValue()));

            imovel.setMunicipio(municipio);
            imovel.setArquivoBruto(arquivoBruto);
            imovel.setVersao(versao);

            imovelRepository.save(imovel);
            totalSalvos++;
        }

        // Fecha a versão: marca como VIGENTE e a torna a versão ativa do conjunto
        versao.setSituacao("VIGENTE"); // Aceite pela CK_VER_SITUACAO
        versao.setDataPublicacao(LocalDateTime.now());
        versaoRepository.save(versao);

        ConjuntoDados conjunto = versao.getConjunto();
        conjunto.setVersaoVigente(versao);
        conjunto.setSituacao("PUBLICADO"); // Aceite pela CK_CON_SITUACAO
        conjuntoRepository.save(conjunto);

        System.out.println("PropertyAreaSavingState: " + totalSalvos
                + " imóveis salvos. Versão " + versao.getNumero() + " publicada.");
    }
}