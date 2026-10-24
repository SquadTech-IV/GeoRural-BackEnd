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
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;

public class PropertyAreaSavingState
                implements TreatmentState {

        @Override
        public void execute(BaseTreatment baseContext) {

                PropertyAreaTreatment propertyArea = (PropertyAreaTreatment) baseContext;

                ImovelRuralRepository imovelRepository = SpringContextHolder.getBean(
                                ImovelRuralRepository.class);

                VersaoRepository versaoRepository = SpringContextHolder.getBean(
                                VersaoRepository.class);

                ConjuntoDadosRepository conjuntoRepository = SpringContextHolder.getBean(
                                ConjuntoDadosRepository.class);

                ExecucaoPipelineRepository execucaoRepository = SpringContextHolder.getBean(
                                ExecucaoPipelineRepository.class);

                MovimentacaoZonaRepository movimentacaoRepository = SpringContextHolder.getBean(
                                MovimentacaoZonaRepository.class);

                ArquivoBruto arquivoBruto = propertyArea.getArquivoBruto();

                Versao versao = propertyArea.getVersao();

                ExecucaoPipeline execucao = propertyArea.getExecucaoPipeline();

                if (arquivoBruto == null ||
                                versao == null) {

                        throw new IllegalStateException(
                                        "ArquivoBruto/Versao ausentes");
                }

                int totalSalvos = 0;

                for (ProcessedProperty property : propertyArea
                                .getProcessedProperties()
                                .values()) {

                        ImovelRural imovel = new ImovelRural();

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

                versao.setExecucao(
                                execucao);

                versaoRepository.save(
                                versao);

                ConjuntoDados conjunto = versao.getConjunto();

                conjunto.setVersaoVigente(
                                versao);

                conjunto.setSituacao(
                                "PUBLICADO");

                conjuntoRepository.save(
                                conjunto);

                if (execucao != null) {

                        execucao.setStatus(
                                        "CONCLUIDA");

                        execucao.setFim(
                                        LocalDateTime.now());

                        execucaoRepository.save(
                                        execucao);

                        MovimentacaoZona movimentacao = new MovimentacaoZona();

                        movimentacao.setArquivoBruto(
                                        arquivoBruto);

                        movimentacao.setExecucao(
                                        execucao);

                        movimentacao.setZona(
                                        "TRATADA");

                        movimentacao.setStatus(
                                        "CONCLUIDO");

                        movimentacao.setObservacao(
                                        "Dados tratados publicados");

                        execucao.setStatus(
                                        "CONCLUIDA");

                        movimentacao.setObservacao(
                                        "Dados tratados publicados");

                        movimentacao.setData(
                                        LocalDateTime.now());

                        movimentacaoRepository.save(
                                        movimentacao);
                }

                System.out.println(
                                "PropertyAreaSavingState: "
                                                + totalSalvos
                                                + " imóveis salvos. Versão "
                                                + versao.getNumero()
                                                + " publicada.");

                propertyArea.setState(null);
        }
}