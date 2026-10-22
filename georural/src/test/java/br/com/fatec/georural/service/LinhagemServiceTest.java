package br.com.fatec.georural.service;

import br.com.fatec.georural.dto.response.linhagem.LinhagemResponse;
import br.com.fatec.georural.dto.response.linhagem.LinhagemResultadoItemResponse;
import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinhagemServiceTest {

    @Mock private ResultadoIndicadorRepository resultadoRepository;
    @Mock private ParametroRegraCalculoRepository parametroRegraRepository;
    @Mock private ExecucaoPipelineRepository execucaoRepository;
    @Mock private EntradaExecucaoRepository entradaRepository;
    @Mock private TransformacaoRepository transformacaoRepository;
    @Mock private MovimentacaoZonaRepository movimentacaoRepository;

    @InjectMocks
    private LinhagemService service;

    // ---------- helpers para montar o cenário ----------

    private Usuario usuario(String nome) {
        Usuario u = new Usuario();
        u.setNome(nome);
        return u;
    }

    private ConjuntoDados conjunto(String fonte, String formaEntrada) {
        FonteDoDado f = new FonteDoDado();
        f.setNome(fonte);
        ConjuntoDados c = new ConjuntoDados();
        c.setFonte(f);
        c.setFormaEntrada(formaEntrada);
        return c;
    }

    private ArquivoBruto arquivo(Long id, String nome, ConjuntoDados conjunto, Usuario usuario) {
        ArquivoBruto a = new ArquivoBruto();
        a.setId(id);
        a.setNome(nome);
        a.setConjunto(conjunto);
        a.setUsuario(usuario);
        a.setFormato("SHP");
        a.setTamanhoBytes(1000L);
        a.setHash("hash-" + id);
        a.setDataUpload(LocalDateTime.of(2026, 10, 15, 9, 0));
        return a;
    }

    private ImovelRural imovel(String car, ArquivoBruto arquivo) {
        Municipio municipio = new Municipio();
        municipio.setNome("Guarapuava");
        ImovelRural i = new ImovelRural();
        i.setCodigoCar(car);
        i.setAreaTotal(new BigDecimal("25.80"));
        i.setMunicipio(municipio);
        i.setArquivoBruto(arquivo);
        return i;
    }

    private ExecucaoPipeline execucao(Long id, String etapa, String dagRunId) {
        ExecucaoPipeline e = new ExecucaoPipeline();
        e.setId(id);
        e.setEtapa(etapa);
        e.setStatus("CONCLUIDA");
        e.setDagRunId(dagRunId);
        e.setInicio(LocalDateTime.of(2026, 10, 18, 3, 12));
        e.setDuracaoSeg(252L);
        return e;
    }

    private Versao versao(Long numero, String situacao, ExecucaoPipeline execucao, Usuario usuario) {
        Versao v = new Versao();
        v.setNumero(numero);
        v.setSituacao(situacao);
        v.setExecucao(execucao);
        v.setUsuario(usuario);
        v.setDataPublicacao(LocalDateTime.of(2026, 10, 18, 4, 0));
        return v;
    }

    private Indicador indicador() {
        Indicador ind = new Indicador();
        ind.setSigla("IAE");
        ind.setNome("Indice de Area Embargada");
        return ind;
    }

    private RegraCalculo regra() {
        RegraCalculo r = new RegraCalculo();
        r.setId(5L);
        r.setVersao("v4");
        r.setDescricao("Mede a parcela do imovel sobreposta a embargos");
        r.setAutor("gestor");
        r.setMotivo("ajuste de projecao");
        r.setVigente("S");
        r.setDataCriacao(LocalDateTime.of(2026, 8, 1, 10, 0));
        return r;
    }

    private ParametroRegraCalculo parametro(String nome, String valor, String unidade) {
        ParametroRegraCalculo p = new ParametroRegraCalculo();
        p.setNome(nome);
        p.setValor(valor);
        p.setUnidade(unidade);
        return p;
    }

    private Transformacao transformacao(int ordem, String tipo) {
        Transformacao t = new Transformacao();
        t.setOrdem(ordem);
        t.setTipo(tipo);
        t.setDescricao("descricao de " + tipo);
        return t;
    }

    private MovimentacaoZona movimentacao(ArquivoBruto arquivo) {
        MovimentacaoZona m = new MovimentacaoZona();
        m.setArquivoBruto(arquivo);
        return m;
    }

    private ResultadoIndicador resultado(Long id, ImovelRural imovel, Versao versao,
                                         ExecucaoPipeline execucao, RegraCalculo regra) {
        ResultadoIndicador r = new ResultadoIndicador();
        r.setId(id);
        r.setImovel(imovel);
        r.setIndicador(indicador());
        r.setVersao(versao);
        r.setExecucao(execucao);
        r.setRegraCalculo(regra);
        r.setValorPercentual(new BigDecimal("12.4000"));
        r.setValorHectares(new BigDecimal("3.2000"));
        r.setValorAbsoluto(new BigDecimal("32000"));
        r.setSituacao("ruim");
        r.setDataCalculo(LocalDateTime.of(2026, 10, 18, 3, 16));
        return r;
    }

    // ---------- listarPublicados ----------

    @Test
    void listarPublicadosDeveNormalizarFiltrosEMontarItens() {
        ImovelRural imovel = imovel("PR-4104105-F3A2E7C9D1B4", null);
        Versao versao = versao(13L, "VIGENTE", null, null);
        ResultadoIndicador r = resultado(1L, imovel, versao, null, null);

        when(resultadoRepository.buscarPublicados("IAE", "%santa%")).thenReturn(List.of(r));

        List<LinhagemResultadoItemResponse> lista = service.listarPublicados("iae", " Santa ");

        assertThat(lista).hasSize(1);
        LinhagemResultadoItemResponse item = lista.get(0);
        assertThat(item.id()).isEqualTo(1L);
        assertThat(item.codigoCar()).isEqualTo("PR-4104105-F3A2E7C9D1B4");
        assertThat(item.municipio()).isEqualTo("Guarapuava");
        assertThat(item.indicadorSigla()).isEqualTo("IAE");
        assertThat(item.valorPercentual()).isEqualByComparingTo("12.4");
        assertThat(item.versaoNumero()).isEqualTo(13L);
    }

    @Test
    void listarPublicadosComTodosEBuscaNulaDeveUsarFiltroVazio() {
        when(resultadoRepository.buscarPublicados("", "%%")).thenReturn(List.of());

        List<LinhagemResultadoItemResponse> lista = service.listarPublicados("TODOS", null);

        assertThat(lista).isEmpty();
    }

    // ---------- consultar ----------

    @Test
    void consultarDeveLancarExcecaoQuandoResultadoNaoExistir() {
        when(resultadoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(999L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("999");
    }

    @Test
    void consultarSemExecucaoDeveTrazerRegraEOrigemEDeixarExecucaoNulaETransformacoesVazias() {
        ArquivoBruto arquivoCar = arquivo(1L, "CAR_Guarapuava.zip", conjunto("CAR", "UPLOAD"), null);
        RegraCalculo regra = regra();
        ResultadoIndicador r = resultado(1L, imovel("PR-123", arquivoCar),
                versao(13L, "VIGENTE", null, usuario("Gestor")), null, regra);

        when(resultadoRepository.findById(1L)).thenReturn(Optional.of(r));
        when(parametroRegraRepository.findByRegraCalculo(regra)).thenReturn(List.of(
                parametro("Projecao de area", "Albers / SIRGAS 2000", null),
                parametro("Buffer de tolerancia", "0", "m")));

        LinhagemResponse resposta = service.consultar(1L);

        assertThat(resposta.resultado().codigoCar()).isEqualTo("PR-123");
        assertThat(resposta.resultado().municipio()).isEqualTo("Guarapuava");
        assertThat(resposta.resultado().publicadoPor()).isEqualTo("Gestor");
        assertThat(resposta.resultado().versaoNumero()).isEqualTo(13L);

        assertThat(resposta.regra().versao()).isEqualTo("v4");
        assertThat(resposta.regra().vigente()).isTrue();
        assertThat(resposta.regra().parametros()).hasSize(2);
        assertThat(resposta.regra().parametros().get(0).nome()).isEqualTo("Projecao de area");

        assertThat(resposta.execucao()).isNull();
        assertThat(resposta.transformacoes()).isEmpty();

        assertThat(resposta.origem()).hasSize(1);
        assertThat(resposta.origem().get(0).nome()).isEqualTo("CAR_Guarapuava.zip");
        assertThat(resposta.origem().get(0).formaEntrada()).isEqualTo("UPLOAD");
        assertThat(resposta.origem().get(0).fonte()).isEqualTo("CAR");
        assertThat(resposta.origem().get(0).enviadoPor()).isNull();
        assertThat(resposta.origem().get(0).hash()).isEqualTo("hash-1");
    }

    @Test
    void consultarSemRegraDeveDevolverRegraNula() {
        ResultadoIndicador r = resultado(1L, imovel("PR-123", null),
                versao(1L, "VIGENTE", null, null), null, null);

        when(resultadoRepository.findById(1L)).thenReturn(Optional.of(r));

        LinhagemResponse resposta = service.consultar(1L);

        assertThat(resposta.regra()).isNull();
        assertThat(resposta.origem()).isEmpty();
    }

    @Test
    void consultarCadeiaCompletaDeveReunirExecucaoTransformacoesEOrigens() {
        ArquivoBruto arquivoCar = arquivo(1L, "CAR_Guarapuava.zip", conjunto("CAR", "UPLOAD"), usuario("operador.dados"));
        ArquivoBruto arquivoEmbargo = arquivo(2L, "Embargos_IBAMA.geojson", conjunto("IBAMA", "API"), null);

        ExecucaoPipeline tratamentoImovel = execucao(80L, "TRATAMENTO", null);
        ExecucaoPipeline tratamentoEmbargo = execucao(90L, "TRATAMENTO", null);
        ExecucaoPipeline calculo = execucao(104L, "CALCULO", "run-1");
        ExecucaoPipeline ingestao = execucao(70L, "INGESTAO", "run-1");

        Versao versaoImovel = versao(13L, "VIGENTE", tratamentoImovel, usuario("Gestor"));
        Versao versaoEmbargo = versao(7L, "VIGENTE", tratamentoEmbargo, null);

        EntradaExecucao entrada = new EntradaExecucao();
        entrada.setExecucao(calculo);
        entrada.setVersao(versaoEmbargo);
        entrada.setPapel("EMBARGO");

        ResultadoIndicador r = resultado(1L, imovel("PR-123", arquivoCar), versaoImovel, calculo, null);

        when(resultadoRepository.findById(1L)).thenReturn(Optional.of(r));
        when(entradaRepository.findByExecucao_Id(104L)).thenReturn(List.of(entrada));
        when(execucaoRepository.findByDagRunIdOrderByInicioAsc("run-1")).thenReturn(List.of(ingestao, calculo));
        when(transformacaoRepository.findByExecucao_IdOrderByOrdemAsc(80L))
                .thenReturn(List.of(transformacao(1, "Reprojecao")));
        when(transformacaoRepository.findByExecucao_IdOrderByOrdemAsc(90L))
                .thenReturn(List.of(transformacao(1, "Recorte"), transformacao(2, "Dissolve")));
        when(movimentacaoRepository.findByExecucao_Id(90L)).thenReturn(List.of(movimentacao(arquivoEmbargo)));

        LinhagemResponse resposta = service.consultar(1L);

        assertThat(resposta.execucao().id()).isEqualTo(104L);
        assertThat(resposta.execucao().dagRunId()).isEqualTo("run-1");
        assertThat(resposta.execucao().etapas()).containsExactly("INGESTAO", "CALCULO");

        assertThat(resposta.transformacoes())
                .extracting(LinhagemResponse.TransformacaoInfo::tipo)
                .containsExactly("Reprojecao", "Recorte", "Dissolve");

        assertThat(resposta.origem())
                .extracting(LinhagemResponse.OrigemInfo::nome)
                .containsExactly("CAR_Guarapuava.zip", "Embargos_IBAMA.geojson");
        assertThat(resposta.origem().get(0).enviadoPor()).isEqualTo("operador.dados");
        assertThat(resposta.origem().get(1).formaEntrada()).isEqualTo("API");
        assertThat(resposta.origem().get(1).fonte()).isEqualTo("IBAMA");
        assertThat(resposta.origem().get(1).enviadoPor()).isNull();
    }

    @Test
    void consultarNaoDeveDuplicarArquivoQuandoMesmoArquivoVemDoImovelEDaMovimentacao() {
        ArquivoBruto arquivoCar = arquivo(1L, "CAR_Guarapuava.zip", conjunto("CAR", "UPLOAD"), null);

        ExecucaoPipeline calculo = execucao(104L, "CALCULO", null);
        ExecucaoPipeline tratamento = execucao(90L, "TRATAMENTO", null);
        Versao versaoEntrada = versao(7L, "VIGENTE", tratamento, null);

        EntradaExecucao entrada = new EntradaExecucao();
        entrada.setExecucao(calculo);
        entrada.setVersao(versaoEntrada);

        ResultadoIndicador r = resultado(1L, imovel("PR-123", arquivoCar),
                versao(13L, "VIGENTE", null, null), calculo, null);

        when(resultadoRepository.findById(1L)).thenReturn(Optional.of(r));
        when(entradaRepository.findByExecucao_Id(104L)).thenReturn(List.of(entrada));
        when(movimentacaoRepository.findByExecucao_Id(90L)).thenReturn(List.of(movimentacao(arquivoCar)));

        LinhagemResponse resposta = service.consultar(1L);

        assertThat(resposta.origem()).hasSize(1);
    }

    @Test
    void consultarDeveUsarExecucaoDaVersaoQuandoResultadoNaoTemExecucao() {
        ExecucaoPipeline tratamento = execucao(80L, "TRATAMENTO", null);
        ResultadoIndicador r = resultado(1L, imovel("PR-123", null),
                versao(13L, "VIGENTE", tratamento, null), null, null);

        when(resultadoRepository.findById(1L)).thenReturn(Optional.of(r));
        when(entradaRepository.findByExecucao_Id(80L)).thenReturn(List.of());
        when(transformacaoRepository.findByExecucao_IdOrderByOrdemAsc(80L))
                .thenReturn(List.of(transformacao(1, "Reprojecao")));

        LinhagemResponse resposta = service.consultar(1L);

        assertThat(resposta.execucao().id()).isEqualTo(80L);
        assertThat(resposta.execucao().etapas()).containsExactly("TRATAMENTO");
        // a mesma execução entra uma vez só, mesmo sendo da versão e do resultado
        assertThat(resposta.transformacoes()).hasSize(1);
        verify(execucaoRepository, never()).findByDagRunIdOrderByInicioAsc(any());
    }
}