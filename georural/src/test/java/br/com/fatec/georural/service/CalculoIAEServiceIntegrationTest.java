package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Transactional
@Rollback
class CalculoIAEServiceIntegrationTest {

    @Autowired
    private CalculoIAEService service;

    @Test
    void calcular_deveRetornarAreaProximaDaMedidaManualmente() {
        Long imovelId = 41L;
        BigDecimal areaEsperadaHa = new BigDecimal("91.503");
        BigDecimal tolerancia = new BigDecimal("1.83"); // ~2% de 91.503

        IAEResponse resposta = service.calcularSobreposicaoEmbargo(imovelId);

        assertThat(resposta.getValorHectares().doubleValue())
            .isCloseTo(areaEsperadaHa.doubleValue(), within(tolerancia.doubleValue()));

        assertThat(resposta.getEmbargosIntersectantes()).isNotEmpty();
    }
}
