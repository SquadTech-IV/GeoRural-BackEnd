package br.com.fatec.georural.service.treatment.property_area.state;

import java.time.LocalDateTime;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.dto.request.regra.ProcessedProperty;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;

public class PropertyAreaSavingState
        implements TreatmentState {

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea =
                (PropertyAreaTreatment) baseContext;

        ImovelRuralRepository imovelRepository =
                SpringContextHolder.getBean(
                        ImovelRuralRepository.class);

        VersaoRepository versaoRepository =
                SpringContextHolder.getBean(
                        VersaoRepository.class);

        ConjuntoDadosRepository conjuntoRepository =
                SpringContextHolder.getBean(
                        ConjuntoDadosRepository.class);

        ArquivoBruto arquivoBruto =
                propertyArea.getArquivoBruto();

        Versao versao =
                propertyArea.getVersao();

        if (arquivoBruto == null ||
                versao == null) {

            throw new IllegalStateException(
                    "ArquivoBruto/Versao ausentes");
        }

        int totalSalvos = 0;

        for (ProcessedProperty property :
                propertyArea
                        .getProcessedProperties()
                        .values()) {

            ImovelRural imovel =
                    new ImovelRural();

            imovel.setCodigoCar(
                    property.getRecibo());

            imovel.setGeometria(
                    property.getGeometry4674());

            imovel.setAreaTotal(
                    property.getAreaHa());

            imovel.setArquivoBruto(
                    arquivoBruto);

            imovel.setVersao(
                    versao);

            imovelRepository.save(
                    imovel);

            totalSalvos++;
        }

        versao.setSituacao(
                "VIGENTE");

        versao.setDataPublicacao(
                LocalDateTime.now());

        versaoRepository.save(
                versao);

        ConjuntoDados conjunto =
                versao.getConjunto();

        conjunto.setVersaoVigente(
                versao);

        conjunto.setSituacao(
                "PUBLICADO");

        conjuntoRepository.save(
                conjunto);

        System.out.println(
                "PropertyAreaSavingState: "
                        + totalSalvos
                        + " imóveis salvos. Versão "
                        + versao.getNumero()
                        + " publicada.");

        propertyArea.setState(null);
    }
}