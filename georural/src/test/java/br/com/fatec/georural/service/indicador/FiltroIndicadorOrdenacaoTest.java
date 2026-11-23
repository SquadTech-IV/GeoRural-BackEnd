package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltroIndicadorOrdenacaoTest {

    private FiltroIndicador montar(String ordenar, java.util.List<String> aceitas) {
        return FiltroIndicador.montar("IFC", null, null, null, null, null, null, null, true,
                null, null, false, 0, 50, ordenar, aceitas);
    }

    @Test
    void ocorrencia_eAceitaNosResultadosPorImovel() {
        assertThat(montar("ocorrencia", FiltroIndicador.ORDENACOES).ordenar()).isEqualTo("ocorrencia");
    }

    @Test
    void ocorrencia_eAceitaNosResultadosPorMunicipio() {
        assertThat(montar("ocorrencia", FiltroIndicador.ORDENACOES_MUNICIPIO).ordenar())
                .isEqualTo("ocorrencia");
    }

    @Test
    void ordemPadrao_continuaSendoAPrimeiraDaLista() {
        assertThat(montar(null, FiltroIndicador.ORDENACOES).ordenar()).isEqualTo("imovel");
    }

    @Test
    void ordenacaoDesconhecida_continuaRejeitada() {
        assertThatThrownBy(() -> montar("pior", FiltroIndicador.ORDENACOES))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("ocorrencia");
    }
}
