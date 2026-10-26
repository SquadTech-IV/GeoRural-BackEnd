package br.com.fatec.georural.service;

import br.com.fatec.georural.entity.ImovelRural;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.ResultadoIndicador;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class ResultadoIndicadorServiceTest {

    @Mock ResultadoIndicadorRepository repository;
    @InjectMocks ResultadoIndicadorService service;

    private ResultadoIndicador resultado(Long id, ImovelRural imovel, Indicador indicador, String vigente) {
        ResultadoIndicador r = new ResultadoIndicador();
        r.setId(id);
        r.setImovel(imovel);
        r.setIndicador(indicador);
        r.setVigente(vigente);
        return r;
    }

    @Test
    void salvar_deveInvalidarOVigenteAnterior_quandoForOutro() {
        ImovelRural imovel = new ImovelRural();
        imovel.setId(1L);
        Indicador indicador = new Indicador();
        indicador.setId(10L);

        ResultadoIndicador anterior = resultado(100L, imovel, indicador, "S");
        ResultadoIndicador novo = resultado(null, imovel, indicador, "S");

        when(repository.findByImovelIdAndIndicadorAndVigente(1L, indicador, "S"))
                .thenReturn(Optional.of(anterior));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.salvarComoVigente(novo);

        assertThat(anterior.getVigente()).isEqualTo("N");
        verify(repository).saveAndFlush(anterior);
        verify(repository).save(novo);
        assertThat(novo.getVigente()).isEqualTo("S");
    }

    @Test
    void salvar_naoDeveInvalidarNada_quandoNaoHaVigenteAnterior() {
        ImovelRural imovel = new ImovelRural();
        imovel.setId(2L);
        Indicador indicador = new Indicador();
        indicador.setId(10L);

        ResultadoIndicador novo = resultado(null, imovel, indicador, "S");

        when(repository.findByImovelIdAndIndicadorAndVigente(2L, indicador, "S"))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.salvarComoVigente(novo);

        verify(repository, never()).saveAndFlush(any());
        verify(repository).save(novo);
        assertThat(novo.getVigente()).isEqualTo("S");
    }

    @Test
    void salvar_naoDeveInvalidarEleMesmo_quandoReescreveOProprioVigente() {
        ImovelRural imovel = new ImovelRural();
        imovel.setId(3L);
        Indicador indicador = new Indicador();
        indicador.setId(10L);

        ResultadoIndicador mesmo = resultado(200L, imovel, indicador, "S");

        when(repository.findByImovelIdAndIndicadorAndVigente(3L, indicador, "S"))
                .thenReturn(Optional.of(mesmo));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.salvarComoVigente(mesmo);

        verify(repository, never()).saveAndFlush(any());
        verify(repository).save(mesmo);
        assertThat(mesmo.getVigente()).isEqualTo("S");
    }
}