package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.isap.IsapCategoriaResumoResponse;
import br.com.fatec.georural.dto.response.isap.IsapImovelConsultaResponse;
import br.com.fatec.georural.dto.response.isap.IsapListaItemResponse;
import br.com.fatec.georural.entity.*;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import br.com.fatec.georural.repository.ImovelRuralRepository;
import br.com.fatec.georural.repository.ResultadoIsapDetalheRepository;
import br.com.fatec.georural.repository.ResultadoIsapResumoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IsapConsultaServiceTest {

    @Mock
    private ImovelRuralRepository imovelRepository;

    @Mock
    private ResultadoIsapResumoRepository resumoRepository;

    @Mock
    private ResultadoIsapDetalheRepository detalheRepository;

    private IsapConsultaService service;

    @BeforeEach
    void setUp() {
        service = new IsapConsultaService(imovelRepository, resumoRepository, detalheRepository);
    }

    private ImovelRural criarImovel(Long id, String car, String nome) {
        Municipio mun = new Municipio();
        mun.setId(4109401L);
        mun.setNome("Guarapuava");

        ImovelRural imovel = new ImovelRural();
        imovel.setId(id);
        imovel.setCodigoCar(car);
        imovel.setNome(nome);
        imovel.setMunicipio(mun);
        return imovel;
    }

    private Versao criarVersao(Long id, Long numero) {
        Versao v = new Versao();
        v.setId(id);
        v.setNumero(numero);
        return v;
    }

    private RegraCalculo criarRegra(String versao) {
        RegraCalculo r = new RegraCalculo();
        r.setId(5L);
        r.setVersao(versao);
        return r;
    }

    private ResultadoIsapResumo criarResumo(
            ImovelRural imovel,
            Versao versao,
            RegraCalculo regra,
            String categoria,
            String sobrepoe,
            BigDecimal areaSobrepostaHa,
            BigDecimal pct,
            BigDecimal areaImovelHa,
            String status) {
        ResultadoIsapResumo r = new ResultadoIsapResumo();
        r.setImovel(imovel);
        r.setVersao(versao);
        r.setRegraCalculo(regra);
        r.setCategoria(categoria);
        r.setSobrepoe(sobrepoe);
        r.setAreaSobrepostaHa(areaSobrepostaHa);
        r.setPctSobreposicao(pct);
        r.setAreaImovelHa(areaImovelHa);
        r.setStatus(status);
        r.setDataCalculo(LocalDateTime.of(2026, 11, 22, 14, 0));
        return r;
    }

    @Test
    @DisplayName("Cenario COM_SOBREPOSICAO com dupla contagem: TOTAL <= soma das categorias, pct <= 100 e aviso presente")
    void consultarPorImovelId_comSobreposicaoEDuplaContagem_deveManterConsistencia() {
        ImovelRural imovel = criarImovel(10L, "PR-4109401-AAA111", "Fazenda Pinhal");
        Versao versao = criarVersao(3L, 3L);
        RegraCalculo regra = criarRegra("1.0");
        BigDecimal areaImovel = new BigDecimal("100.0000");

        // UC = 30 ha (30%), TI = 25 ha (25%), com 10 ha de intersecao entre elas -> TOTAL = 45 ha (45%)
        List<ResultadoIsapResumo> resumos = List.of(
                criarResumo(imovel, versao, regra, "UC_FEDERAL_ESTADUAL", "S",
                        new BigDecimal("30.0000"), new BigDecimal("30.0000"), areaImovel, "COM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "TERRA_INDIGENA", "S",
                        new BigDecimal("25.0000"), new BigDecimal("25.0000"), areaImovel, "COM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "QUILOMBOLA", "N",
                        BigDecimal.ZERO, BigDecimal.ZERO, areaImovel, "SEM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "ASSENTAMENTO", "N",
                        BigDecimal.ZERO, BigDecimal.ZERO, areaImovel, "SEM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "TOTAL", "S",
                        new BigDecimal("45.0000"), new BigDecimal("45.0000"), areaImovel, "COM_SOBREPOSICAO")
        );

        AreaProtegida ap1 = new AreaProtegida();
        ap1.setId(501L);
        ResultadoIsapDetalhe d1 = new ResultadoIsapDetalhe();
        d1.setAreaProtegida(ap1);
        d1.setCategoria("UC_FEDERAL_ESTADUAL");
        d1.setNomeFeicao("PARNA Campos Gerais");
        d1.setTipoFeicao("PARNA");
        d1.setCodigoFeicao("0000.00.0123");
        d1.setAreaHa(new BigDecimal("30.0000"));
        d1.setPctSobreposicao(new BigDecimal("30.0000"));

        AreaProtegida ap2 = new AreaProtegida();
        ap2.setId(502L);
        ResultadoIsapDetalhe d2 = new ResultadoIsapDetalhe();
        d2.setAreaProtegida(ap2);
        d2.setCategoria("TERRA_INDIGENA");
        d2.setNomeFeicao("TI Rio das Cobras");
        d2.setTipoFeicao("Homologada");
        d2.setCodigoFeicao("37101");
        d2.setAreaHa(new BigDecimal("25.0000"));
        d2.setPctSobreposicao(new BigDecimal("25.0000"));

        when(imovelRepository.findById(10L)).thenReturn(Optional.of(imovel));
        when(resumoRepository.findVigentesPorImovelId(10L)).thenReturn(resumos);
        when(detalheRepository.findPorImovelEVersao(10L, 3L)).thenReturn(List.of(d1, d2));

        IsapImovelConsultaResponse resp = service.consultarPorImovelId(10L, null);

        assertEquals(10L, resp.imovelId());
        assertEquals("PR-4109401-AAA111", resp.codigoCar());
        assertEquals("Guarapuava", resp.municipio());
        assertEquals(5, resp.resumo().size());
        assertEquals(2, resp.detalhes().size());
        assertNotNull(resp.avisoDuplaContagem());
        assertTrue(resp.avisoDuplaContagem().contains("sobrepor"));

        IsapCategoriaResumoResponse total = resp.resumo().stream()
                .filter(r -> "TOTAL".equals(r.categoria()))
                .findFirst()
                .orElseThrow();

        BigDecimal somaCategorias = resp.resumo().stream()
                .filter(r -> !"TOTAL".equals(r.categoria()))
                .map(IsapCategoriaResumoResponse::areaSobrepostaHa)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertTrue(total.areaSobrepostaHa().compareTo(somaCategorias) <= 0,
                "TOTAL nao pode exceder a soma das categorias");
        assertTrue(total.areaSobrepostaHa().compareTo(resp.areaImovelHa()) <= 0,
                "TOTAL nao pode exceder a area do imovel");
        assertTrue(total.pctSobreposicao().compareTo(new BigDecimal("100")) <= 0,
                "Percentual deve ser <= 100%");
    }

    @Test
    @DisplayName("Cenario SEM_SOBREPOSICAO deve retornar 0 ha, 0% e lista de feicoes vazia")
    void consultarPorCodigoCar_semSobreposicao_deveRetornarZeroPorcento() {
        ImovelRural imovel = criarImovel(11L, "PR-4109401-BBB222", "Sitio Esperanca");
        Versao versao = criarVersao(3L, 3L);
        RegraCalculo regra = criarRegra("1.0");
        BigDecimal areaImovel = new BigDecimal("50.0000");

        List<ResultadoIsapResumo> resumos = List.of(
                criarResumo(imovel, versao, regra, "UC_FEDERAL_ESTADUAL", "N",
                        BigDecimal.ZERO, BigDecimal.ZERO, areaImovel, "SEM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "TERRA_INDIGENA", "N",
                        BigDecimal.ZERO, BigDecimal.ZERO, areaImovel, "SEM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "QUILOMBOLA", "N",
                        BigDecimal.ZERO, BigDecimal.ZERO, areaImovel, "SEM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "ASSENTAMENTO", "N",
                        BigDecimal.ZERO, BigDecimal.ZERO, areaImovel, "SEM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "TOTAL", "N",
                        BigDecimal.ZERO, BigDecimal.ZERO, areaImovel, "SEM_SOBREPOSICAO")
        );

        when(imovelRepository.findByCodigoCar("PR-4109401-BBB222")).thenReturn(Optional.of(imovel));
        when(resumoRepository.findPorImovelIdEVersaoId(11L, 3L)).thenReturn(resumos);
        when(detalheRepository.findPorImovelEVersao(11L, 3L)).thenReturn(List.of());

        IsapImovelConsultaResponse resp = service.consultarPorCodigoCar("PR-4109401-BBB222", 3L);

        assertTrue(resp.detalhes().isEmpty());
        for (IsapCategoriaResumoResponse item : resp.resumo()) {
            assertEquals(Boolean.FALSE, item.sobrepoe());
            assertEquals(0, BigDecimal.ZERO.compareTo(item.areaSobrepostaHa()));
            assertEquals(0, BigDecimal.ZERO.compareTo(item.pctSobreposicao()));
            assertEquals("SEM_SOBREPOSICAO", item.status());
        }
    }

    @Test
    @DisplayName("Cenario CAMADA_INDISPONIVEL preserva NULLs e propaga status para o TOTAL")
    void consultarPorImovelId_camadaIndisponivel_devePreservarNulls() {
        ImovelRural imovel = criarImovel(12L, "PR-4109401-CCC333", "Chacara Vale");
        Versao versao = criarVersao(4L, 4L);
        RegraCalculo regra = criarRegra("1.0");
        BigDecimal areaImovel = new BigDecimal("80.0000");

        List<ResultadoIsapResumo> resumos = List.of(
                criarResumo(imovel, versao, regra, "UC_FEDERAL_ESTADUAL", "N",
                        BigDecimal.ZERO, BigDecimal.ZERO, areaImovel, "SEM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "TERRA_INDIGENA", null,
                        null, null, areaImovel, "CAMADA_INDISPONIVEL"),
                criarResumo(imovel, versao, regra, "QUILOMBOLA", "N",
                        BigDecimal.ZERO, BigDecimal.ZERO, areaImovel, "SEM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "ASSENTAMENTO", "N",
                        BigDecimal.ZERO, BigDecimal.ZERO, areaImovel, "SEM_SOBREPOSICAO"),
                criarResumo(imovel, versao, regra, "TOTAL", null,
                        null, null, areaImovel, "CAMADA_INDISPONIVEL")
        );

        when(imovelRepository.findById(12L)).thenReturn(Optional.of(imovel));
        when(resumoRepository.findVigentesPorImovelId(12L)).thenReturn(resumos);
        when(detalheRepository.findPorImovelEVersao(12L, 4L)).thenReturn(List.of());

        IsapImovelConsultaResponse resp = service.consultarPorImovelId(12L, null);

        IsapCategoriaResumoResponse total = resp.resumo().stream()
                .filter(r -> "TOTAL".equals(r.categoria()))
                .findFirst()
                .orElseThrow();

        assertEquals("CAMADA_INDISPONIVEL", total.status());
        assertNull(total.sobrepoe());
        assertNull(total.areaSobrepostaHa());
        assertNull(total.pctSobreposicao());
    }

    @Test
    @DisplayName("Listagem paginada normaliza filtro de status e busca")
    void listar_deveNormalizarFiltrosERetornarPagina() {
        ImovelRural imovel = criarImovel(10L, "PR-4109401-AAA111", "Fazenda Pinhal");
        Versao versao = criarVersao(3L, 3L);
        ResultadoIsapResumo total = criarResumo(imovel, versao, criarRegra("1.0"),
                "TOTAL", "S", new BigDecimal("12.5000"), new BigDecimal("12.5000"),
                new BigDecimal("100.0000"), "COM_SOBREPOSICAO");

        PageRequest pageable = PageRequest.of(0, 20);
        when(resumoRepository.consultarVigentesPaginado("COM_SOBREPOSICAO", 4109401L, "Pinhal", pageable))
                .thenReturn(new PageImpl<>(List.of(total)));

        PaginaResponse<IsapListaItemResponse> pagina = service.listar(
                "  com_sobreposicao ", 4109401L, "  Pinhal  ", pageable);

        assertEquals(1, pagina.itens().size());
        assertEquals("COM_SOBREPOSICAO", pagina.itens().get(0).status());
        assertEquals(Boolean.TRUE, pagina.itens().get(0).sobrepoe());
    }

    @Test
    @DisplayName("Deve lancar RecursoNaoEncontradoException quando imovel ou resultado ISAP nao existir")
    void consultar_quandoInexistente_deveLancar404() {
        when(imovelRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.consultarPorImovelId(999L, null));

        ImovelRural imovelSemCalculo = criarImovel(20L, "PR-4109401-ZZZ999", "Sem Calculo");
        when(imovelRepository.findById(20L)).thenReturn(Optional.of(imovelSemCalculo));
        when(resumoRepository.findVigentesPorImovelId(20L)).thenReturn(List.of());
        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.consultarPorImovelId(20L, null));

        assertThrows(IllegalArgumentException.class,
                () -> service.consultarPorCodigoCar("   ", null));
    }
}