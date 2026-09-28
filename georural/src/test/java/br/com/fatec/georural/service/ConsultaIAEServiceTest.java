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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaIAEServiceTest {

    @Mock ResultadoIndicadorRepository resultadoIndicadorRepository;
    @Mock IndicadorRepository indicadorRepository;
    @Mock ImovelRuralRepository imovelRuralRepository;
    @Mock IAEMapper mapper;

    @InjectMocks ConsultaIAEService service;

    @Test
    void consultar_deveLancarNaoEncontrado_quandoNuncaFoiCalculado() {
        Indicador ind = new Indicador();
        ind.setSigla("IAE");
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.of(ind));
        when(resultadoIndicadorRepository.findTopByImovelIdAndIndicadorOrderByDataCalculoDesc(1L, ind))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarPorImovel(1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void consultar_deveLancarEstadoInconsistente_quandoIndicadorNaoCadastrado() {
        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarPorImovel(1L))
                .isInstanceOf(EstadoInconsistenteException.class);
    }

    @Test
    void consultar_deveRetornarResultadoSalvo_quandoExiste() {
        Indicador ind = new Indicador();
        ind.setSigla("IAE");
        ResultadoIndicador salvo = new ResultadoIndicador();
        salvo.setDataCalculo(LocalDateTime.now());

        when(indicadorRepository.findBySigla("IAE")).thenReturn(Optional.of(ind));
        when(resultadoIndicadorRepository.findTopByImovelIdAndIndicadorOrderByDataCalculoDesc(1L, ind))
                .thenReturn(Optional.of(salvo));
        when(imovelRuralRepository.encontrarEmbargosIntersectantes(1L)).thenReturn(List.of());
        when(mapper.toResponse(any(), any())).thenReturn(
                new IAEResponse(1L, "IAE", null, null, null, null, List.of()));

        IAEResponse resposta = service.consultarPorImovel(1L);

        assertThat(resposta).isNotNull();
    }
}