package br.com.fatec.georural.gateway;

import br.com.fatec.georural.dto.response.EnvioProcessamentoResponse;

public interface ProcessamentoGateway {
    void enviarParaProcessamento(EnvioProcessamentoResponse entrega);
}