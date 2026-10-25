package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.indicador.IAEResponse;
import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.exception.EstadoInconsistenteException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.mapper.IAEMapper;
import br.com.fatec.georural.repository.*;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculoIAEServiceTest {

    @Mock ImovelRuralRepository imovelRuralRepository;
    @Mock IndicadorRepository indicadorRepository;
    @Mock RegraCalculoRepository regraCalculoRepository;
    @Mock ResultadoIndicadorRepository resultadoIndicadorRepository;
    @Mock ResultadoIndicadorService resultadoIndicadorService;
    @Mock IAEMapper mapper;

    @InjectMocks CalculoIAEService service;

    private ImovelRural imovelExemplo() {
        ImovelRural i = new ImovelRural();
        i.setId(1L);
        i.setAreaTotal(new BigDecimal("1472.88"));
        i.setVersao(new Versao());
        return i;
    }

    private Indicador indicadorExemplo() {
        Indicador ind = new Indicador();
        ind.setId(10L);
        ind.setSigla("IAE");
        return ind;
    }

    private void stubsBase() {
        when(imovelRuralRepository.findById(1L)).thenReturn(Optional.of(imovelExemplo()));
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.of(indicadorExemplo()));
        when(regraCalculoRepository.findByIndicadorAndVigente(any(), any())).thenReturn(Optional.of(new RegraCalculo()));
        when(imovelRuralRepository.calcularAreaEmbargadaAgregada(1L, 1L))
            .thenReturn(List.<Object[]>of(new Object[]{null, new BigDecimal("1000")}));
        when(imovelRuralRepository.buscarEmbargosGeoJson(1L)).thenReturn(null);
        when(resultadoIndicadorService.salvarComoVigente(any())).thenAnswer(inv -> inv.getArgument(0));
        when(imovelRuralRepository.encontrarEmbargosIntersectantes(1L)).thenReturn(List.of());
        when(mapper.toResponse(any(), any())).thenAnswer(inv -> {
    ResultadoIndicador r = inv.getArgument(0);
    return new IAEResponse(
            1L,
            null,
            null,
            "IAE",
            r.getValorPercentual(),
            r.getValorHectares(),
            r.getValorAbsoluto(),
            r.getDataCalculo(),
            List.of());
});
    }

    @Test
    void calcular_deveRetornarPercentualZero_quandoNaoHaEmbargo() {
        stubsBase();
        when(imovelRuralRepository.calcularAreaEmbargadaAgregada(1L, 61L))
                .thenReturn(
                    List.<Object[]>of(
                        new Object[]{null, new BigDecimal("1472.88")}
                    ));

        IAEResponse resposta = service.calcularSobreposicaoEmbargo(1L, 61L);

        assertThat(resposta.getValorPercentual()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resposta.getEmbargosIntersectantes()).isEmpty();
    }

    @Test
    void calcular_deveMarcarVigenteOk_quandoNaoHaEmbargo() {
        when(imovelRuralRepository.findById(1L)).thenReturn(Optional.of(imovelExemplo()));
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.of(indicadorExemplo()));
        when(regraCalculoRepository.findByIndicadorAndVigente(any(), any())).thenReturn(Optional.of(new RegraCalculo()));
        when(imovelRuralRepository.calcularAreaEmbargadaAgregada(1L, 61L))
                .thenReturn(List.<Object[]>of(new Object[]{null, new BigDecimal("1000")}));
        when(imovelRuralRepository.buscarEmbargosGeoJson(1L)).thenReturn(null);
        when(resultadoIndicadorService.salvarComoVigente(any())).thenAnswer(inv -> inv.getArgument(0));
        when(imovelRuralRepository.encontrarEmbargosIntersectantes(1L)).thenReturn(List.of());
        when(mapper.toResponse(any(), any())).thenAnswer(inv -> {
            ResultadoIndicador r = inv.getArgument(0);
            // confirma a classificacao gravada no resultado antes de mapear
            assertThat(r.getSituacao()).isEqualTo("ok");
            return new IAEResponse(
                1L,                         // imovelId
                null,                       // municipio
                null,                       // codigoCar
                "IAE",                      // indicadorSigla
                r.getValorPercentual(),
                r.getValorHectares(),
                r.getValorAbsoluto(),
                r.getDataCalculo(),
                List.of()
            );
        });
        service.calcularSobreposicaoEmbargo(1L, 61L);
    }

    @Test
    void calcular_deveLancarNaoEncontrado_quandoImovelNaoExiste() {
        when(imovelRuralRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calcularSobreposicaoEmbargo(99L, 1L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }

    @Test
    void calcular_deveLancarEstadoInconsistente_quandoIndicadorNaoCadastrado() {
        when(imovelRuralRepository.findById(1L)).thenReturn(Optional.of(imovelExemplo()));
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calcularSobreposicaoEmbargo(1L, 61L))
                .isInstanceOf(EstadoInconsistenteException.class);
    }

    @Test
    void calcular_deveLancarEstadoInconsistente_quandoSemRegraVigente() {
        when(imovelRuralRepository.findById(1L)).thenReturn(Optional.of(imovelExemplo()));
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.of(indicadorExemplo()));
        when(regraCalculoRepository.findByIndicadorAndVigente(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calcularSobreposicaoEmbargo(1L, 61L))
                .isInstanceOf(EstadoInconsistenteException.class);
    }
}