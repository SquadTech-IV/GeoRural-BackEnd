package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.request.regra.CriarVersaoRegraRequest;
import br.com.fatec.georural.dto.request.regra.ParametroInput;
import br.com.fatec.georural.dto.response.regra.RegraCalculoResponse;
import br.com.fatec.georural.entity.Indicador;
import br.com.fatec.georural.entity.ParametroRegraCalculo;
import br.com.fatec.georural.entity.RegraCalculo;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ParametroRegraCalculoRepository;
import br.com.fatec.georural.repository.RegraCalculoRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegraCalculoServiceTest {

    @Mock RegraCalculoRepository regraCalculoRepository;
    @Mock ParametroRegraCalculoRepository parametroRegraCalculoRepository;
    @Mock IndicadorService indicadorService;
    @Mock ParametroRegraValidador parametroRegraValidador;

    @InjectMocks RegraCalculoService service;

    // ---------- criar nova versao ----------

    @Test
    void criarNovaVersao_deveCriarVigente_eInvalidarAnterior() {
        Indicador ind = indicador("IRL");
        RegraCalculo v1 = regra(ind, "v1", "formula antiga", "S");

        when(indicadorService.buscarEntidade("IRL")).thenReturn(ind);
        when(regraCalculoRepository.findByIndicadorAndVigente(ind, "S")).thenReturn(Optional.of(v1));
        when(parametroRegraCalculoRepository.findByRegraCalculo(v1))
                .thenReturn(List.of(param(v1, "minimo_rl_percent", "18"))); // valor antigo
        when(regraCalculoRepository.countByIndicadorAndVigente(ind, "S")).thenReturn(0L);
        when(regraCalculoRepository.countByIndicador(ind)).thenReturn(1L);
        when(regraCalculoRepository.save(any(RegraCalculo.class))).thenAnswer(inv -> inv.getArgument(0));

        CriarVersaoRegraRequest request = req("mudou o minimo", "jhow",
                List.of(input("minimo_rl_percent", "20"))); // valor novo (ha alteracao)

        RegraCalculoResponse resposta = service.criarNovaVersao("IRL", request);

        assertThat(resposta.versao()).isEqualTo("v2");
        assertThat(resposta.vigente()).isEqualTo("S");
        assertThat(resposta.autor()).isEqualTo("jhow");
        assertThat(resposta.motivo()).isEqualTo("mudou o minimo");
        assertThat(resposta.parametros()).hasSize(1);
        assertThat(v1.getVigente()).isEqualTo("N");
        verify(regraCalculoRepository).saveAndFlush(v1);
    }

    @Test
    void criarNovaVersao_deveRejeitar_quandoMotivoVazio() {
        CriarVersaoRegraRequest request = req("", "jhow", List.of());
        assertThatThrownBy(() -> service.criarNovaVersao("IRL", request))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void criarNovaVersao_deveRejeitar_quandoAutorVazio() {
        CriarVersaoRegraRequest request = req("motivo", "  ", List.of());
        assertThatThrownBy(() -> service.criarNovaVersao("IRL", request))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void criarNovaVersao_devePropagarErroDeValidacaoPorTipo() {
        org.mockito.Mockito.doThrow(new RequisicaoInvalidaException("valor invalido"))
                .when(parametroRegraValidador).validar(any());

        CriarVersaoRegraRequest request = req("motivo", "jhow",
                List.of(input("minimo_rl_percent", "150")));

        assertThatThrownBy(() -> service.criarNovaVersao("IRL", request))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void criarNovaVersao_deveRejeitar_quandoNenhumaAlteracao() {
        Indicador ind = indicador("IRL");
        RegraCalculo v1 = regra(ind, "v1", "formula", "S");

        when(indicadorService.buscarEntidade("IRL")).thenReturn(ind);
        when(regraCalculoRepository.findByIndicadorAndVigente(ind, "S")).thenReturn(Optional.of(v1));
        when(parametroRegraCalculoRepository.findByRegraCalculo(v1))
                .thenReturn(List.of(param(v1, "minimo_rl_percent", "20")));

        CriarVersaoRegraRequest request = req("sem mudar nada", "jhow",
                List.of(input("minimo_rl_percent", "20")));

        assertThatThrownBy(() -> service.criarNovaVersao("IRL", request))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void criarNovaVersao_deveRejeitar_quandoFormulaVazia() {
        Indicador ind = indicador("IRL");
        RegraCalculo v1 = regra(ind, "v1", "formula", "S");

        when(indicadorService.buscarEntidade("IRL")).thenReturn(ind);
        when(regraCalculoRepository.findByIndicadorAndVigente(ind, "S")).thenReturn(Optional.of(v1));

        CriarVersaoRegraRequest request = new CriarVersaoRegraRequest(
                "motivo", "jhow", "   ", null, List.of(input("minimo_rl_percent", "20")));

        assertThatThrownBy(() -> service.criarNovaVersao("IRL", request))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    // ---------- consultas ----------

    @Test
    void buscarVigente_deveRetornar_quandoExiste() {
        Indicador ind = indicador("IRL");
        RegraCalculo v1 = regra(ind, "v1", "formula", "S");

        when(indicadorService.buscarEntidade("IRL")).thenReturn(ind);
        when(regraCalculoRepository.findByIndicadorAndVigente(ind, "S")).thenReturn(Optional.of(v1));
        when(parametroRegraCalculoRepository.findByRegraCalculo(v1))
                .thenReturn(List.of(param(v1, "minimo_rl_percent", "20")));

        RegraCalculoResponse resposta = service.buscarVigente("IRL");

        assertThat(resposta.versao()).isEqualTo("v1");
        assertThat(resposta.parametros()).hasSize(1);
    }

    @Test
    void buscarVigente_deveLancar404_quandoSemVigente() {
        Indicador ind = indicador("IRL");
        when(indicadorService.buscarEntidade("IRL")).thenReturn(ind);
        when(regraCalculoRepository.findByIndicadorAndVigente(ind, "S")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarVigente("IRL"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listarHistorico_deveMapearTodasAsVersoes() {
        Indicador ind = indicador("IRL");
        RegraCalculo v2 = regra(ind, "v2", "formula", "S");
        RegraCalculo v1 = regra(ind, "v1", "formula", "N");

        when(indicadorService.buscarEntidade("IRL")).thenReturn(ind);
        when(regraCalculoRepository.findByIndicadorOrderByDataCriacaoDesc(ind))
                .thenReturn(List.of(v2, v1));
        when(parametroRegraCalculoRepository.findByRegraCalculo(any())).thenReturn(List.of());

        List<RegraCalculoResponse> historico = service.listarHistorico("IRL");

        assertThat(historico).hasSize(2);
        assertThat(historico.get(0).versao()).isEqualTo("v2");
        assertThat(historico.get(1).versao()).isEqualTo("v1");
    }

    @Test
    void buscarVersao_deveRetornar_quandoExiste() {
        Indicador ind = indicador("IRL");
        RegraCalculo v1 = regra(ind, "v1", "formula", "N");

        when(indicadorService.buscarEntidade("IRL")).thenReturn(ind);
        when(regraCalculoRepository.findByIndicadorAndVersao(eq(ind), eq("v1")))
                .thenReturn(Optional.of(v1));
        when(parametroRegraCalculoRepository.findByRegraCalculo(v1)).thenReturn(List.of());

        RegraCalculoResponse resposta = service.buscarVersao("IRL", "v1");

        assertThat(resposta.versao()).isEqualTo("v1");
    }

    @Test
    void buscarVersao_deveLancar404_quandoNaoExiste() {
        Indicador ind = indicador("IRL");
        when(indicadorService.buscarEntidade("IRL")).thenReturn(ind);
        when(regraCalculoRepository.findByIndicadorAndVersao(eq(ind), eq("v99")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarVersao("IRL", "v99"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ---------- helpers ----------

    private Indicador indicador(String sigla) {
        Indicador i = new Indicador();
        i.setId(1L);
        i.setSigla(sigla);
        i.setNome(sigla);
        return i;
    }

    private RegraCalculo regra(Indicador ind, String versao, String formula, String vigente) {
        RegraCalculo r = new RegraCalculo();
        r.setId(10L);
        r.setIndicador(ind);
        r.setVersao(versao);
        r.setFormula(formula);
        r.setDescricao("desc");
        r.setVigente(vigente);
        r.setDataCriacao(LocalDateTime.now());
        return r;
    }

    private ParametroRegraCalculo param(RegraCalculo r, String nome, String valor) {
        ParametroRegraCalculo p = new ParametroRegraCalculo();
        p.setRegraCalculo(r);
        p.setNome(nome);
        p.setTipo("PERCENTUAL");
        p.setUnidade("%");
        p.setValor(valor);
        return p;
    }

    private CriarVersaoRegraRequest req(String motivo, String autor, List<ParametroInput> params) {
        return new CriarVersaoRegraRequest(motivo, autor, null, null, params);
    }

    private ParametroInput input(String nome, String valor) {
        return new ParametroInput(nome, "PERCENTUAL", "%", valor);
    }
}