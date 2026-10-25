package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.versao.VersaoResumoResponse;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.ExecucaoPipeline;
import br.com.fatec.georural.entity.Usuario;
import br.com.fatec.georural.entity.Versao;
import br.com.fatec.georural.repository.ResultadoIndicadorRepository;
import br.com.fatec.georural.repository.VersaoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaVersaoServiceTest {

    @Mock VersaoRepository versaoRepository;
    @Mock ResultadoIndicadorRepository resultadoIndicadorRepository;

    @InjectMocks ConsultaVersaoService service;

    private Versao versao(Long id, Long numero, String situacao, Long conjuntoId,
                          String conjuntoNome, Long execucaoId, String usuarioNome,
                          LocalDateTime dataPublicacao) {
        ConjuntoDados conjunto = new ConjuntoDados();
        conjunto.setId(conjuntoId);
        conjunto.setNome(conjuntoNome);
        Usuario usuario = new Usuario();
        usuario.setNome(usuarioNome);
        Versao v = new Versao();
        v.setId(id);
        v.setNumero(numero);
        v.setSituacao(situacao);
        v.setCompetencia("2024");
        v.setConjunto(conjunto);
        v.setUsuario(usuario);
        v.setDataPublicacao(dataPublicacao);
        if (execucaoId != null) {
            ExecucaoPipeline exe = new ExecucaoPipeline();
            exe.setId(execucaoId);
            v.setExecucao(exe);
        }
        return v;
    }

    @Test
    void listar_deveMontarResumo_comIndicadoresEVigenteAtual() {
        Versao rascunho = versao(10L, 3L, "RASCUNHO", 1L, "Reserva Legal - PR", 100L, "Joel", null);
        LocalDateTime desde = LocalDateTime.of(2024, 1, 10, 9, 0);
        Versao vigente = versao(7L, 2L, "VIGENTE", 1L, "Reserva Legal - PR", 90L, "Maria", desde);

        when(versaoRepository.listarResumo()).thenReturn(List.of(rascunho));
        when(resultadoIndicadorRepository.listarIndicadoresPorVersoes(List.of(10L)))
                .thenReturn(List.<Object[]>of(
                        new Object[]{10L, "IRL", "Indice de Reserva Legal", "Regra RL v1"}));
        when(versaoRepository.listarResumoPorSituacao("VIGENTE")).thenReturn(List.of(vigente));

        List<VersaoResumoResponse> lista = service.listar(null);

        assertThat(lista).hasSize(1);
        VersaoResumoResponse r = lista.get(0);
        assertThat(r.versaoId()).isEqualTo(10L);
        assertThat(r.numero()).isEqualTo(3L);
        assertThat(r.situacao()).isEqualTo("RASCUNHO");
        assertThat(r.conjuntoNome()).isEqualTo("Reserva Legal - PR");
        assertThat(r.execucaoId()).isEqualTo(100L);
        assertThat(r.responsavel()).isEqualTo("Joel");
        assertThat(r.indicadores()).hasSize(1);
        assertThat(r.indicadores().get(0).sigla()).isEqualTo("IRL");
        assertThat(r.regrasCalculo()).containsExactly("Regra RL v1");
        assertThat(r.versaoVigenteAtual()).isEqualTo(2L);
        assertThat(r.vigenteDesde()).isEqualTo(desde);
    }

    @Test
    void listar_deveRetornarVazio_quandoNaoHaVersoes() {
        when(versaoRepository.listarResumo()).thenReturn(List.of());

        assertThat(service.listar(null)).isEmpty();
    }
}
