package br.com.fatec.georural.service.treatment.property_area.state;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.support.TransactionTemplate;

import br.com.fatec.georural.config.SpringContextHolder;
import br.com.fatec.georural.dto.request.regra.ProcessedProperty;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.ExecucaoPipelineRepository;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import br.com.fatec.georural.service.treatment.core.BaseTreatment;
import br.com.fatec.georural.service.treatment.core.TreatmentState;
import br.com.fatec.georural.service.treatment.property_area.PropertyAreaTreatment;
import br.com.fatec.georural.service.treatment.storage.ZonaTratadaService;

public class PropertyAreaSavingState implements TreatmentState {

    @Override
    public void execute(BaseTreatment baseContext) {

        PropertyAreaTreatment propertyArea = (PropertyAreaTreatment) baseContext;
        ImovelRuralRepository imovelRepository = SpringContextHolder.getBean(ImovelRuralRepository.class);
        VersaoRepository versaoRepository = SpringContextHolder.getBean(VersaoRepository.class);
        ConjuntoDadosRepository conjuntoRepository = SpringContextHolder.getBean(ConjuntoDadosRepository.class);
        ExecucaoPipelineRepository execucaoRepository = SpringContextHolder.getBean(ExecucaoPipelineRepository.class);

        ArquivoBruto arquivoBruto = propertyArea.getArquivoBruto();
        Versao versao = propertyArea.getVersao();
        ExecucaoPipeline execucao = propertyArea.getExecucaoPipeline();

        if (arquivoBruto == null || versao == null) {
            throw new IllegalStateException("ArquivoBruto/Versao ausentes");
        }

        // 1) Encerra a validação: consolida tabela VALIDACAO e grava quarentena.json na OCI se houver rejeitados
        propertyArea.encerrarValidacao();

        Map<String, ProcessedProperty> processadas = propertyArea.getProcessedProperties();

        if (processadas == null || processadas.isEmpty()) {
            if (execucao != null) {
                execucao.setStatus(propertyArea.getTotalRejeitados() > 0 ? "COM_RESSALVA" : "CONCLUIDA");
                execucao.setFim(LocalDateTime.now());
                execucao.setRegistrosProcessados((long) propertyArea.getTotalRejeitados());
                execucao.setRegistrosAprovados(0L);
                execucao.setRegistrosRejeitados((long) propertyArea.getTotalRejeitados());

                if (execucao.getInicio() != null) {
                    execucao.setDuracaoSeg(Duration.between(execucao.getInicio(), LocalDateTime.now()).getSeconds());
                }

                execucaoRepository.save(execucao);
            }

            propertyArea.setState(null);
            return;
        }

        // 2) Zona Tratada (OCI Bucket georural-tratada + MOVIMENTACAO_ZONA): apenas para registros APROVADOS
        List<Map<String, Object>> linhas = new ArrayList<>();
        for (ProcessedProperty property : processadas.values()) {
            Map<String, Object> linha = new LinkedHashMap<>();
            linha.put("recibo", property.getRecibo());
            linha.put("municipio", property.getMunicipio());
            linha.put("estado", property.getEstado());
            linha.put("tema", property.getTema());
            linha.put("area_ha", property.getAreaHa());
            linha.put("geometria_wkt", property.getGeometry4674() != null ? property.getGeometry4674().toText() : null);
            linhas.add(linha);
        }

        if (execucao != null && !linhas.isEmpty()) {
            SpringContextHolder
                    .getBean(ZonaTratadaService.class)
                    .gravar(propertyArea, "IMOVEL", linhas);
        }

        // 3) Persistência dos APROVADOS no Oracle (Tabela IMOVEL_RURAL e VERSAO VIGENTE)
        TransactionTemplate tx = SpringContextHolder.getBean(TransactionTemplate.class);

        Integer salvos = tx.execute(status -> {
            Map<String, ImovelRural> existentes = new HashMap<>();
            List<String> codigos = new ArrayList<>(processadas.keySet());

            for (int i = 0; i < codigos.size(); i += 500) {
                List<String> lote = codigos.subList(i, Math.min(i + 500, codigos.size()));
                for (ImovelRural existente : imovelRepository.findByCodigoCarIn(lote)) {
                    existentes.put(existente.getCodigoCar(), existente);
                }
            }

            int total = 0;
            for (ProcessedProperty property : processadas.values()) {
                ImovelRural imovel = existentes.get(property.getRecibo());
                if (imovel == null) {
                    imovel = new ImovelRural();
                    imovel.setCodigoCar(property.getRecibo());
                }

                imovel.setGeometria(property.getGeometry4674());
                imovel.setAreaTotal(property.getAreaHa());
                imovel.setArquivoBruto(arquivoBruto);
                imovel.setVersao(versao);

                imovelRepository.save(imovel);
                total++;
            }

            versao.setSituacao("VIGENTE");
            versao.setDataPublicacao(LocalDateTime.now());
            versao.setExecucao(execucao);
            versaoRepository.save(versao);

            ConjuntoDados conjunto = versao.getConjunto();
            conjunto.setVersaoVigente(versao);
            conjunto.setSituacao("PUBLICADO");
            conjuntoRepository.save(conjunto);

            return total;
        });

        int totalSalvos = salvos == null ? 0 : salvos;

        // 4) Fechamento da execução com totais consolidados
        if (execucao != null) {
            execucao.setStatus(propertyArea.getTotalRejeitados() > 0 ? "COM_RESSALVA" : "CONCLUIDA");
            execucao.setFim(LocalDateTime.now());
            execucao.setRegistrosProcessados((long) (totalSalvos + propertyArea.getTotalRejeitados()));
            execucao.setRegistrosAprovados((long) totalSalvos);
            execucao.setRegistrosRejeitados((long) propertyArea.getTotalRejeitados());

            if (execucao.getInicio() != null) {
                execucao.setDuracaoSeg(Duration.between(execucao.getInicio(), LocalDateTime.now()).getSeconds());
            }

            execucaoRepository.save(execucao);
        }

        propertyArea.setState(null);
    }
}