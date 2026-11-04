package br.com.fatec.georural.service.pipeline.validacao;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.FeicaoStaging;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// grava as feicoes aprovadas na FEICAO_STAGING, em lotes
@Slf4j
@Service
public class CargaStagingService {

    public static final int TAMANHO_LOTE = 500;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public int gravarLote(Long execucaoId, Long arquivoBrutoId, String origem,
                          List<FeicaoPadronizada> feicoes) {
        if (feicoes.isEmpty()) {
            return 0;
        }
        ExecucaoPipeline execucao = entityManager.getReference(ExecucaoPipeline.class, execucaoId);
        ArquivoBruto arquivo = arquivoBrutoId == null ? null
                : entityManager.getReference(ArquivoBruto.class, arquivoBrutoId);
        LocalDateTime agora = LocalDateTime.now();

        for (FeicaoPadronizada feicao : feicoes) {
            FeicaoStaging linha = new FeicaoStaging();
            linha.setExecucao(execucao);
            linha.setArquivoBruto(arquivo);
            linha.setOrigem(origem);
            linha.setIdentificador(cortar(feicao.identificador(), 120));
            linha.setLinha(feicao.linha());
            linha.setAtributos(JsonSimples.deMapa(feicao.atributos()));
            linha.setGeometria(feicao.geometria());
            linha.setData(agora);
            entityManager.persist(linha);
        }

        // flush + clear para a memoria nao crescer com o tamanho do arquivo
        entityManager.flush();
        entityManager.clear();
        return feicoes.size();
    }

    // apaga a area de pouso de uma execucao
    @Transactional
    public int limpar(Long execucaoId) {
        int apagadas = entityManager
                .createQuery("DELETE FROM FeicaoStaging f WHERE f.execucao.id = :exe")
                .setParameter("exe", execucaoId)
                .executeUpdate();
        if (apagadas > 0) {
            log.debug("Staging da execucao {}: {} linha(s) apagada(s).", execucaoId, apagadas);
        }
        return apagadas;
    }

    private String cortar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
