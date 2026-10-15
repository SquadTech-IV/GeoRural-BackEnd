package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.imovel.ImovelIndicadoresResponse;
import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ImovelIndicadoresServiceTest {

    @Mock ImovelRuralRepository imovelRepository;
    @Mock ResultadoIndicadorRepository resultadoRepository;
    @InjectMocks ImovelIndicadoresService service;

    private ImovelRural imovel() {
        ImovelRural i = new ImovelRural();
        i.setId(1L);
        i.setCodigoCar("PR-TESTE-1");
        i.setAreaTotal(new BigDecimal("100"));
        return i;
    }

    private ResultadoIndicador resultado(String sigla, String nome, String situacao,
                                         String versaoRegra, String features) {
        Indicador ind = new Indicador();
        ind.setSigla(sigla);
        ind.setNome(nome);

        RegraCalculo regra = null;
        if (versaoRegra != null) {
            regra = new RegraCalculo();
            regra.setVersao(versaoRegra);
        }

        ResultadoIndicador r = new ResultadoIndicador();
        r.setIndicador(ind);
        r.setRegraCalculo(regra);
        r.setSituacao(situacao);
        r.setValorPercentual(new BigDecimal("12.5"));
        r.setFeatures(features);
        return r;
    }

    @Test
    void detalhar_deveRetornarSoOsIndicadoresVigentesMapeados() {
        when(imovelRepository.findByCodigoCar("PR-TESTE-1")).thenReturn(Optional.of(imovel()));
        when(imovelRepository.buscarGeometriaGeoJson(1L)).thenReturn("{\"type\":\"Polygon\"}");
        when(resultadoRepository.buscarVigentesDoImovel(1L)).thenReturn(List.of(
                resultado("IAE", "Indice de Area Embargada", "ruim", "v1", "{\"type\":\"MultiPolygon\"}"),
                resultado("IRL", "Indice de Reserva Legal", "ok", "v2", null)));

        ImovelIndicadoresResponse resp = service.detalharPorCar("PR-TESTE-1");

        assertThat(resp.codigoCar()).isEqualTo("PR-TESTE-1");
        assertThat(resp.geometria()).contains("Polygon");
        assertThat(resp.indicadores()).hasSize(2);
        assertThat(resp.indicadores().get(0).sigla()).isEqualTo("IAE");
        assertThat(resp.indicadores().get(0).situacao()).isEqualTo("ruim");
        assertThat(resp.indicadores().get(0).versaoRegra()).isEqualTo("v1");
        assertThat(resp.indicadores().get(0).features()).contains("MultiPolygon");
        assertThat(resp.indicadores().get(1).sigla()).isEqualTo("IRL");
        assertThat(resp.indicadores().get(1).versaoRegra()).isEqualTo("v2");
        assertThat(resp.indicadores().get(1).features()).isNull();
    }

    @Test
    void detalhar_deveRetornarListaVazia_quandoImovelSemCalculo() {
        when(imovelRepository.findByCodigoCar("PR-TESTE-1")).thenReturn(Optional.of(imovel()));
        when(imovelRepository.buscarGeometriaGeoJson(1L)).thenReturn(null);
        when(resultadoRepository.buscarVigentesDoImovel(1L)).thenReturn(List.of());

        ImovelIndicadoresResponse resp = service.detalharPorCar("PR-TESTE-1");

        assertThat(resp.indicadores()).isEmpty();
    }

    @Test
    void detalhar_deveLancarNaoEncontrado_quandoCarNaoExiste() {
        when(imovelRepository.findByCodigoCar("NAO-EXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detalharPorCar("NAO-EXISTE"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}