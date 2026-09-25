package br.com.fatec.georural.gateway;

import br.com.fatec.georural.dto.response.EnvioProcessamentoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProcessamentoGatewayStub implements ProcessamentoGateway {

    private static final Logger log = LoggerFactory.getLogger(ProcessamentoGatewayStub.class);

    @Override
    public void enviarParaProcessamento(EnvioProcessamentoResponse e) {
        log.info("[STUB US-01] Arquivo id={} ({}) pronto para processamento. Formas de acesso:",
                e.id(), e.nomeArquivo());
        log.info("  - HTTP     : {}", e.downloadUrl());
        log.info("  - BANCO    : SELECT {} FROM {} WHERE {} = {}",
                e.colunaConteudo(), e.tabelaConteudo(), e.colunaChave(), e.id());
        log.info("  - DATALAKE : {}", (e.uriDataLake() != null ? e.uriDataLake() : "(ainda no banco)"));
    }
}