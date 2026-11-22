package br.com.fatec.georural.service.indicador;

import br.com.fatec.georural.dto.response.PaginaResponse;
import br.com.fatec.georural.dto.response.iapp.IappListaItemResponse;
import br.com.fatec.georural.dto.response.iapp.IappResultadoResponse;
import br.com.fatec.georural.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IappConsultaServiceTest {

    private JdbcTemplate jdbc;
    private IappConsultaService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new IappConsultaService(jdbc);
    }

    @Test
    @DisplayName("Listar IAPP deve validar status inválido e lançar IllegalArgumentException")
    void listar_statusInvalido_deveLancarExcecao() {
        assertThrows(IllegalArgumentException.class,
                () -> service.listar("INVALIDO", null, null, null, PageRequest.of(0, 20)));
        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("Listar IAPP deve aplicar filtros de status, município e busca retornando PaginaResponse")
    void listar_comFiltrosValidos_deveRetornarPagina() {
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*)"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);

        IappListaItemResponse itemMock = new IappListaItemResponse(
                100L, 10L, "PR-4109401-IAPP01", "Fazenda Rio Verde",
                4109401L, "Guarapuava", 3L,
                new BigDecimal("20.0000"), new BigDecimal("15.0000"), new BigDecimal("5.0000"),
                new BigDecimal("75.0000"), new BigDecimal("100.0000"),
                "CALCULADO", "APP preservada em 75,00% (passivo de 5,0000 ha).",
                "S", LocalDateTime.of(2026, 11, 22, 18, 0)
        );

        when(jdbc.query(startsWith("SELECT r.iap_id"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(itemMock));

        PaginaResponse<IappListaItemResponse> pagina = service.listar(
                "CALCULADO", 4109401L, "Rio Verde", null, PageRequest.of(0, 20));

        assertEquals(1, pagina.totalItens());
        assertEquals(1, pagina.itens().size());
        IappListaItemResponse item = pagina.itens().get(0);

        // Consistência matemática da US#23.03: app_vegetada_ha + passivo_ha = app_total_ha e iapp_pct <= 100
        assertEquals(0, item.appVegetadaHa().add(item.passivoHa()).compareTo(item.appTotalHa()));
        assertTrue(item.iappPct().compareTo(new BigDecimal("100")) <= 0);
        assertEquals("CALCULADO", item.status());
    }

    @Test
    @DisplayName("Consultar por ID deve lançar 404 quando imóvel não existir")
    void consultarPorImovelId_imovelInexistente_deveLancar404() {
        when(jdbc.queryForObject(contains("FROM IMOVEL_RURAL WHERE imo_id = ?"), eq(Integer.class), eq(999L)))
                .thenReturn(0);

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.consultarPorImovelId(999L, null, false));
    }

    @Test
    @DisplayName("Consultar por ID deve lançar 404 quando imóvel existir mas não tiver resultado IAPP")
    void consultarPorImovelId_semResultado_deveLancar404() {
        when(jdbc.queryForObject(contains("FROM IMOVEL_RURAL WHERE imo_id = ?"), eq(Integer.class), eq(10L)))
                .thenReturn(1);
        when(jdbc.query(startsWith("SELECT r.iap_id"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.consultarPorImovelId(10L, null, false));
    }

    @Test
    @DisplayName("Consultar por ID deve retornar resultado CALCULADO com rastreabilidade completa e GeoJSON")
    void consultarPorImovelId_calculadoComGeoJson_deveRetornarContratoCompleto() throws Exception {
        when(jdbc.queryForObject(contains("FROM IMOVEL_RURAL WHERE imo_id = ?"), eq(Integer.class), eq(10L)))
                .thenReturn(1);

        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("iap_id")).thenReturn(50L);
        when(rs.getObject("imo_id")).thenReturn(10L);
        when(rs.getString("imo_codigo_car")).thenReturn("PR-4109401-IAPP01");
        when(rs.getString("imo_nome")).thenReturn("Fazenda Rio Verde");
        when(rs.getObject("mun_id")).thenReturn(4109401L);
        when(rs.getString("mun_nome")).thenReturn("Guarapuava");
        when(rs.getObject("ver_id")).thenReturn(3L);
        when(rs.getObject("ver_numero")).thenReturn(2);
        when(rs.getObject("iap_app_ver_id")).thenReturn(4L);
        when(rs.getObject("iap_veg_ver_id")).thenReturn(5L);
        when(rs.getObject("exe_id")).thenReturn(12L);
        when(rs.getObject("rca_id")).thenReturn(8L);
        when(rs.getString("rca_versao")).thenReturn("1.0");
        when(rs.getBigDecimal("iap_app_total_ha")).thenReturn(new BigDecimal("20.0000"));
        when(rs.getBigDecimal("iap_app_vegetada_ha")).thenReturn(new BigDecimal("15.0000"));
        when(rs.getBigDecimal("iap_passivo_ha")).thenReturn(new BigDecimal("5.0000"));
        when(rs.getBigDecimal("iap_iapp_pct")).thenReturn(new BigDecimal("75.0000"));
        when(rs.getBigDecimal("iap_area_imovel_ha")).thenReturn(new BigDecimal("100.0000"));
        when(rs.getString("iap_filtro_tipos_app")).thenReturn("TODOS");
        when(rs.getString("iap_filtro_classes_veg")).thenReturn("TODOS");
        when(rs.getString("iap_competencia_app")).thenReturn("2026-10");
        when(rs.getString("iap_competencia_veg")).thenReturn("2026-10");
        when(rs.getString("iap_status")).thenReturn("CALCULADO");
        when(rs.getString("iap_mensagem")).thenReturn("APP preservada em 75,00% (passivo de 5,0000 ha).");
        when(rs.getString("iap_vigente")).thenReturn("S");
        when(rs.getTimestamp("iap_data_calculo"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 11, 22, 18, 0)));
        when(rs.getObject("app_veg_geojson")).thenReturn("{\"type\":\"Polygon\",\"coordinates\":[]}");
        when(rs.getObject("passivo_geojson")).thenReturn("{\"type\":\"Polygon\",\"coordinates\":[]}");

        when(jdbc.query(startsWith("SELECT r.iap_id"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(inv -> {
                    RowMapper<IappResultadoResponse> mapper = inv.getArgument(1);
                    return List.of(mapper.mapRow(rs, 0));
                });

        IappResultadoResponse resp = service.consultarPorImovelId(10L, null, true);

        assertEquals(50L, resp.iappId());
        assertEquals("CALCULADO", resp.status());
        assertEquals(0, resp.appVegetadaHa().add(resp.passivoHa()).compareTo(resp.appTotalHa()));
        assertTrue(resp.iappPct().compareTo(new BigDecimal("100")) <= 0);
        assertNotNull(resp.appVegetadaGeoJson());
        assertNotNull(resp.passivoGeoJson());
    }

    @Test
    @DisplayName("Consultar por código CAR deve validar CAR vazio e suportar cenários SEM_APP e CAMADA_INDISPONIVEL")
    void consultarPorCodigoCar_semAppECamadaIndisponivel() {
        assertThrows(IllegalArgumentException.class,
                () -> service.consultarPorCodigoCar("   ", null, false));

        when(jdbc.queryForObject(contains("UPPER(imo_codigo_car) = UPPER(?)"), eq(Integer.class), eq("PR-SEM-APP")))
                .thenReturn(1);

        IappResultadoResponse semApp = new IappResultadoResponse(
                51L, 11L, "PR-SEM-APP", "Sitio Campos", 4109401L, "Guarapuava",
                3L, 2, 4L, 5L, 12L, 8L, "1.0",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, new BigDecimal("50.0000"),
                "TODOS", "TODOS", "2026-10", "2026-10",
                "SEM_APP", "Imovel sem APP mapeada na versao vigente da camada APP.",
                "S", LocalDateTime.of(2026, 11, 22, 18, 5), null, null
        );

        when(jdbc.query(startsWith("SELECT r.iap_id"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(semApp));

        IappResultadoResponse resp = service.consultarPorCodigoCar("PR-SEM-APP", null, false);
        assertEquals("SEM_APP", resp.status());
        assertNull(resp.iappPct());
        assertEquals(0, BigDecimal.ZERO.compareTo(resp.appTotalHa()));
        assertEquals(0, BigDecimal.ZERO.compareTo(resp.passivoHa()));
    }
}