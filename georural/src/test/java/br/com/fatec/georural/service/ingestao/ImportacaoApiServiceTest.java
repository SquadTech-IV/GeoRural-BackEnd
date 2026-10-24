package br.com.fatec.georural.service.ingestao;

import br.com.fatec.georural.dto.ingestao.ConteudoRecebido;
import br.com.fatec.georural.dto.ingestao.FormatoRespostaApi;
import br.com.fatec.georural.dto.ingestao.ImportacaoResponse;
import br.com.fatec.georural.dto.ingestao.ItemDisponivelResponse;
import br.com.fatec.georural.dto.ingestao.ParametrosConsultaApi;
import br.com.fatec.georural.dto.ingestao.RespostaApi;
import br.com.fatec.georural.dto.ingestao.ResultadoValidacao;
import br.com.fatec.georural.dto.ingestao.TipoAutenticacaoApi;
import br.com.fatec.georural.entity.ArquivoBruto;
import br.com.fatec.georural.entity.ConjuntoDados;
import br.com.fatec.georural.entity.FonteDoDado;
import br.com.fatec.georural.entity.TipoConjunto;
import br.com.fatec.georural.exception.FalhaComunicacaoApiException;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.exception.RequisicaoInvalidaException;
import br.com.fatec.georural.repository.ConjuntoDadosRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportacaoApiServiceTest {

    @Mock ConjuntoDadosRepository conjuntoRepository;
    @Mock MapeadorParametrosApi mapeador;
    @Mock IntegracaoApiService integracao;
    @Mock LeitorRespostaApi leitor;
    @Mock ValidadorRecebimentoApi validador;
    @Mock DuplicidadeService duplicidade;
    @Mock IngestaoZonaBrutaService ingestao;

    @InjectMocks ImportacaoApiService service;

    private ConjuntoDados conjuntoApi() {
        TipoConjunto tipo = new TipoConjunto();
        tipo.setCodigo("FOCOS_CALOR");
        tipo.setNome("Focos de calor");
        FonteDoDado fonte = new FonteDoDado();
        fonte.setNome("INPE");
        ConjuntoDados c = new ConjuntoDados();
        c.setId(10L);
        c.setNome("Focos de calor");
        c.setFonte(fonte);
        c.setTipo(tipo);
        c.setFormaEntrada("API");
        c.setApiEndpoint("https://exemplo/api/focos");
        c.setApiFormato("GeoJSON");
        c.setApiAuth("none");
        c.setPeriodicidade("Diaria");
        c.setSituacao("PUBLICADO");
        return c;
    }

    private ParametrosConsultaApi params() {
        return ParametrosConsultaApi.de("https://exemplo/api/focos",
                FormatoRespostaApi.GEOJSON, TipoAutenticacaoApi.NENHUMA, null);
    }

    @Test
    void importa_com_sucesso() {
        ConjuntoDados c = conjuntoApi();
        byte[] corpo = "{\"type\":\"FeatureCollection\",\"features\":[{},{}]}".getBytes(StandardCharsets.UTF_8);

        when(conjuntoRepository.findById(10L)).thenReturn(Optional.of(c));
        when(duplicidade.jaRecebido(eq(10L), anyString())).thenReturn(false);
        when(mapeador.montar(eq(c), anyMap())).thenReturn(params());
        when(integracao.consultar(any())).thenReturn(new RespostaApi(200, "application/geo+json", corpo));
        when(leitor.ler(any(), eq(FormatoRespostaApi.GEOJSON)))
                .thenReturn(new ConteudoRecebido(FormatoRespostaApi.GEOJSON, 2, corpo.length, true, corpo));
        when(validador.validar(any(), any(), any())).thenReturn(ResultadoValidacao.ok());
        when(duplicidade.jaExistePorHash(anyString())).thenReturn(false);
        ArquivoBruto salvo = new ArquivoBruto();
        salvo.setId(99L);
        when(ingestao.registrar(eq(c), any(), anyString(), eq(FormatoRespostaApi.GEOJSON), any(), eq("API")))
                .thenReturn(salvo);

        ImportacaoResponse resp = service.importar(10L, List.of("2026-10-18"));

        assertThat(resp.importados()).isEqualTo(1);
        assertThat(resp.falhas()).isZero();
        assertThat(resp.itens()).hasSize(1);
        assertThat(resp.itens().get(0).status()).isEqualTo("SUCESSO");
        assertThat(resp.itens().get(0).quantidadeRegistros()).isEqualTo(2);
        verify(ingestao).registrar(eq(c), any(), anyString(), eq(FormatoRespostaApi.GEOJSON), any(), eq("API"));
    }

    @Test
    void pula_quando_ja_recebido_por_nome() {
        ConjuntoDados c = conjuntoApi();
        when(conjuntoRepository.findById(10L)).thenReturn(Optional.of(c));
        when(duplicidade.jaRecebido(eq(10L), anyString())).thenReturn(true);

        ImportacaoResponse resp = service.importar(10L, List.of("2026-10-18"));

        assertThat(resp.jaRecebidos()).isEqualTo(1);
        assertThat(resp.itens().get(0).status()).isEqualTo("JA_RECEBIDO");
        verifyNoInteractions(integracao, leitor, validador, ingestao);
    }

    @Test
    void marca_falha_quando_comunicacao_falha() {
        ConjuntoDados c = conjuntoApi();
        when(conjuntoRepository.findById(10L)).thenReturn(Optional.of(c));
        when(duplicidade.jaRecebido(eq(10L), anyString())).thenReturn(false);
        when(mapeador.montar(eq(c), anyMap())).thenReturn(params());
        when(integracao.consultar(any())).thenThrow(new FalhaComunicacaoApiException("timeout"));

        ImportacaoResponse resp = service.importar(10L, List.of("2026-10-18"));

        assertThat(resp.falhas()).isEqualTo(1);
        assertThat(resp.itens().get(0).status()).isEqualTo("FALHA");
        assertThat(resp.itens().get(0).mensagem()).contains("timeout");
        verify(ingestao, never()).registrar(any(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void marca_falha_quando_validacao_reprova() {
        ConjuntoDados c = conjuntoApi();
        byte[] corpo = "nao e json".getBytes(StandardCharsets.UTF_8);
        when(conjuntoRepository.findById(10L)).thenReturn(Optional.of(c));
        when(duplicidade.jaRecebido(eq(10L), anyString())).thenReturn(false);
        when(mapeador.montar(eq(c), anyMap())).thenReturn(params());
        when(integracao.consultar(any())).thenReturn(new RespostaApi(200, "text/plain", corpo));
        when(leitor.ler(any(), any()))
                .thenReturn(new ConteudoRecebido(FormatoRespostaApi.GEOJSON, 0, corpo.length, false, corpo));
        when(validador.validar(any(), any(), any()))
                .thenReturn(ResultadoValidacao.invalido(List.of("Conteudo nao e um GEOJSON valido.")));

        ImportacaoResponse resp = service.importar(10L, List.of("2026-10-18"));

        assertThat(resp.falhas()).isEqualTo(1);
        assertThat(resp.itens().get(0).status()).isEqualTo("FALHA");
        assertThat(resp.itens().get(0).mensagem()).contains("GEOJSON");
        verify(ingestao, never()).registrar(any(), any(), anyString(), any(), any(), anyString());
    }

    @Test
    void rejeita_conjunto_que_nao_aceita_api() {
        ConjuntoDados c = conjuntoApi();
        c.setFormaEntrada("UPLOAD");
        when(conjuntoRepository.findById(10L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.importar(10L, List.of("2026-10-18")))
                .isInstanceOf(RequisicaoInvalidaException.class);
    }

    @Test
    void rejeita_conjunto_inexistente() {
        when(conjuntoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importar(99L, List.of("2026-10-18")))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void lista_disponiveis_marcando_novos() {
        ConjuntoDados c = conjuntoApi();
        when(conjuntoRepository.findById(10L)).thenReturn(Optional.of(c));
        when(duplicidade.jaRecebido(eq(10L), anyString())).thenReturn(false);

        List<ItemDisponivelResponse> itens = service.listarDisponiveis(10L);

        assertThat(itens).isNotEmpty();
        assertThat(itens).allMatch(i -> !i.jaRecebido());
        assertThat(itens.get(0).nome()).endsWith(".geojson");
    }
}