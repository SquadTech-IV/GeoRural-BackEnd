package br.com.fatec.georural.service;

import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.MovimentacaoZona;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import br.com.fatec.georural.repository.MovimentacaoZonaRepository;
import br.com.fatec.georural.dto.response.trajetoria.TrajetoriaResponse;
import br.com.fatec.georural.dto.response.trajetoria.StatusAtualResponse;

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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TrajetoriaConjuntoServiceTest {

    @Mock
    private ConjuntoDadosRepository conjuntoRepository;

    @Mock
    private MovimentacaoZonaRepository movimentacaoZonaRepository;

    @InjectMocks
    private TrajetoriaConjuntoService service;

    @Test
    void deveMontarTrajetoriaComEventosDasMovimentacoes() {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(1L);
        conjunto.setNome("Demo Guarapuava");
        conjunto.setSituacao("PUBLICADO");

        ArquivoBruto arquivo = new ArquivoBruto();
        arquivo.setNome("embargos_pr.shp");

        MovimentacaoZona mov1 = new MovimentacaoZona();
        mov1.setArquivoBruto(arquivo);
        mov1.setZona("BRUTA");
        mov1.setStatus("RECEBIDO");
        mov1.setData(LocalDateTime.of(2026, 10, 1, 10, 0));

        MovimentacaoZona mov2 = new MovimentacaoZona();
        mov2.setArquivoBruto(arquivo);
        mov2.setZona("TRATADA");
        mov2.setStatus("CONCLUIDO");
        mov2.setData(LocalDateTime.of(2026, 10, 1, 10, 5));

        when(conjuntoRepository.findById(1L)).thenReturn(Optional.of(conjunto));
        when(movimentacaoZonaRepository.findByArquivoBruto_Conjunto_IdOrderByDataAsc(1L))
                .thenReturn(List.of(mov1, mov2));

        TrajetoriaResponse resultado = service.consultarTrajetoria(1L);

        assertThat(resultado.conjuntoId()).isEqualTo(1L);
        assertThat(resultado.conjuntoNome()).isEqualTo("Demo Guarapuava");
        assertThat(resultado.zonaAtual()).isEqualTo("TRATADA");
        assertThat(resultado.eventos()).hasSize(2);
        assertThat(resultado.eventos().get(0).zona()).isEqualTo("BRUTA");
        assertThat(resultado.eventos().get(1).zona()).isEqualTo("TRATADA");
    }

    @Test
    void deveRetornarZonaBrutaQuandoNaoHouverMovimentacoes() {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(1L);
        conjunto.setNome("Demo Guarapuava");
        conjunto.setSituacao("PUBLICADO");
        conjunto.setDataCadastro(LocalDateTime.of(2026, 9, 1, 8, 0));

        when(conjuntoRepository.findById(1L)).thenReturn(Optional.of(conjunto));
        when(movimentacaoZonaRepository.findByArquivoBruto_Conjunto_IdOrderByDataAsc(1L))
                .thenReturn(List.of());

        TrajetoriaResponse resultado = service.consultarTrajetoria(1L);

        assertThat(resultado.zonaAtual()).isEqualTo("BRUTA");
        assertThat(resultado.eventos()).isEmpty();
    }

    @Test
    void deveLancarExcecaoQuandoConjuntoNaoExistir() {
        when(conjuntoRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarTrajetoria(999L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("999");
    }

    @Test
    void statusAtualDeveRefletirUltimoEventoDaTrajetoria() {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(1L);
        conjunto.setNome("Demo Guarapuava");
        conjunto.setSituacao("PUBLICADO");

        MovimentacaoZona mov = new MovimentacaoZona();
        mov.setArquivoBruto(new ArquivoBruto());
        mov.setZona("QUARENTENA");
        mov.setStatus("PENDENTE");
        mov.setData(LocalDateTime.of(2026, 10, 2, 14, 30));

        when(conjuntoRepository.findById(1L)).thenReturn(Optional.of(conjunto));
        when(movimentacaoZonaRepository.findByArquivoBruto_Conjunto_IdOrderByDataAsc(1L))
                .thenReturn(List.of(mov));

        StatusAtualResponse resultado = service.consultarStatusAtual(1L);

        assertThat(resultado.zonaAtual()).isEqualTo("QUARENTENA");
        assertThat(resultado.ultimaAtualizacao()).isEqualTo(LocalDateTime.of(2026, 10, 2, 14, 30));
    }
}
