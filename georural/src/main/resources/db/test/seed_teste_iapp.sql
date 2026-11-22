-- =====================================================================
-- GeoRural DataHub - Massa de Teste Sintetica do IAPP (US#23.03)
-- SRID: 4674 (SIRGAS 2000) | Recorte territorial: Parana (PR)
-- =====================================================================
-- Reaproveita conjuntos/municipios existentes e cria versoes de teste
-- isoladas com prefixo 'PR-TESTE-IAPP-' para validacao deterministica.
-- =====================================================================

DECLARE
    v_usu_id      NUMBER;
    v_mun_id      NUMBER;
    v_con_imo_id  NUMBER;
    v_con_app_id  NUMBER;
    v_con_veg_id  NUMBER;
    v_arq_imo_id  NUMBER;
    v_arq_app_id  NUMBER;
    v_arq_veg_id  NUMBER;
    v_ver_imo_id  NUMBER;
    v_ver_app_id  NUMBER;
    v_ver_veg_id  NUMBER;

    v_imo_01_id   NUMBER;
    v_imo_02_id   NUMBER;
    v_imo_03_id   NUMBER;
    v_imo_04_id   NUMBER;
    v_imo_05_id   NUMBER;
    v_imo_06_id   NUMBER;

    -- Helper para criar retangulo fechado (CCW) em SRID 4674
    FUNCTION rect(p_x1 NUMBER, p_y1 NUMBER, p_x2 NUMBER, p_y2 NUMBER) RETURN SDO_GEOMETRY IS
    BEGIN
        RETURN SDO_GEOMETRY(
            2003, 4674, NULL,
            SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(p_x1, p_y1, p_x2, p_y1, p_x2, p_y2, p_x1, p_y2, p_x1, p_y1)
        );
    END;
BEGIN
    SELECT MIN(usu_id) INTO v_usu_id FROM USUARIO;
    SELECT MIN(mun_id) INTO v_mun_id FROM MUNICIPIO;

    -- Conjuntos de dados (reaproveita os existentes por TIP_CODIGO)
    SELECT MIN(c.con_id) INTO v_con_imo_id
      FROM CONJUNTO_DADOS c JOIN TIPO_CONJUNTO t ON t.tip_id = c.tip_id
     WHERE t.tip_codigo = 'IMOVEL';

    SELECT MIN(c.con_id) INTO v_con_app_id
      FROM CONJUNTO_DADOS c JOIN TIPO_CONJUNTO t ON t.tip_id = c.tip_id
     WHERE t.tip_codigo = 'APP';

    SELECT MIN(c.con_id) INTO v_con_veg_id
      FROM CONJUNTO_DADOS c JOIN TIPO_CONJUNTO t ON t.tip_id = c.tip_id
     WHERE t.tip_codigo = 'VEGETACAO';

    -- Arquivos brutos sinteticos
    INSERT INTO ARQUIVO_BRUTO (con_id, usu_id, arq_nome, arq_formato, arq_srs_declarado, arq_srs_utilizado,
                               arq_path_armazenamento, arq_status, arq_hash, arq_competencia)
    VALUES (v_con_imo_id, v_usu_id, 'seed_iapp_imoveis.geojson', '.geojson', 4674, 4674,
            '/seed/iapp/imoveis.geojson', 'PROCESSADO', LPAD('A1', 64, '0'), '2026-11')
    RETURNING arq_bruto_id INTO v_arq_imo_id;

    INSERT INTO ARQUIVO_BRUTO (con_id, usu_id, arq_nome, arq_formato, arq_srs_declarado, arq_srs_utilizado,
                               arq_path_armazenamento, arq_status, arq_hash, arq_competencia)
    VALUES (v_con_app_id, v_usu_id, 'seed_iapp_app.geojson', '.geojson', 4674, 4674,
            '/seed/iapp/app.geojson', 'PROCESSADO', LPAD('B2', 64, '0'), '2026-11')
    RETURNING arq_bruto_id INTO v_arq_app_id;

    INSERT INTO ARQUIVO_BRUTO (con_id, usu_id, arq_nome, arq_formato, arq_srs_declarado, arq_srs_utilizado,
                               arq_path_armazenamento, arq_status, arq_hash, arq_competencia)
    VALUES (v_con_veg_id, v_usu_id, 'seed_iapp_vegetacao.geojson', '.geojson', 4674, 4674,
            '/seed/iapp/vegetacao.geojson', 'PROCESSADO', LPAD('C3', 64, '0'), '2026-11')
    RETURNING arq_bruto_id INTO v_arq_veg_id;

    -- Versoes de teste (APP e VEGETACAO PUBLICADAS para serem resolvidas como vigentes)
    INSERT INTO VERSAO (con_id, usu_id, ver_numero, ver_hash, ver_competencia, ver_situacao, ver_data_publicacao)
    VALUES (v_con_imo_id, v_usu_id, 9901, LPAD('11', 64, '0'), '2026-11', 'RASCUNHO', NULL)
    RETURNING ver_id INTO v_ver_imo_id;

    INSERT INTO VERSAO (con_id, usu_id, ver_numero, ver_hash, ver_competencia, ver_situacao, ver_data_publicacao)
    VALUES (v_con_app_id, v_usu_id, 9901, LPAD('22', 64, '0'), '2026-11', 'PUBLICADA', SYSTIMESTAMP)
    RETURNING ver_id INTO v_ver_app_id;

    INSERT INTO VERSAO (con_id, usu_id, ver_numero, ver_hash, ver_competencia, ver_situacao, ver_data_publicacao)
    VALUES (v_con_veg_id, v_usu_id, 9901, LPAD('33', 64, '0'), '2026-11', 'PUBLICADA', SYSTIMESTAMP)
    RETURNING ver_id INTO v_ver_veg_id;

    UPDATE CONJUNTO_DADOS SET con_ver_vigente_id = v_ver_app_id WHERE con_id = v_con_app_id;
    UPDATE CONJUNTO_DADOS SET con_ver_vigente_id = v_ver_veg_id WHERE con_id = v_con_veg_id;

    -- =================================================================
    -- IMOVEIS SINTETICOS (cada imovel: quadrado 0.02 x 0.02 graus ~ 446.92 ha)
    -- =================================================================

    -- Cenario 1: APP Totalmente Vegetada (100% conservada, passivo = 0)
    -- Imovel: [-51.20, -25.20] a [-51.18, -25.18]
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-01', 'Fazenda APP Integralmente Preservada', 'MANUAL',
            rect(-51.20, -25.20, -51.18, -25.18), PKG_GEO.area_ha(rect(-51.20, -25.20, -51.18, -25.18)))
    RETURNING imo_id INTO v_imo_01_id;

    -- APP ocupa metade sul (2/4 do imovel); Vegetacao ocupa todo o imovel
    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_01_id, v_arq_app_id, v_ver_app_id,
            PKG_GEO.area_ha(rect(-51.20, -25.20, -51.18, -25.19)),
            rect(-51.20, -25.20, -51.18, -25.19));

    INSERT INTO VEGETACAO_NATIVA (arq_bruto_id, ver_id, veg_area_ha, veg_geometria)
    VALUES (v_arq_veg_id, v_ver_veg_id,
            PKG_GEO.area_ha(rect(-51.20, -25.20, -51.18, -25.18)),
            rect(-51.20, -25.20, -51.18, -25.18));

    -- Cenario 2: APP Parcialmente Vegetada (75% conservada, 25% passivo)
    -- Imovel: [-51.16, -25.20] a [-51.14, -25.18]
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-02', 'Fazenda APP Parcialmente Vegetada', 'MANUAL',
            rect(-51.16, -25.20, -51.14, -25.18), PKG_GEO.area_ha(rect(-51.16, -25.20, -51.14, -25.18)))
    RETURNING imo_id INTO v_imo_02_id;

    -- APP ocupa toda a area do imovel (4 quadrantes: NW, NE, SW, SE)
    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_02_id, v_arq_app_id, v_ver_app_id,
            PKG_GEO.area_ha(rect(-51.16, -25.20, -51.14, -25.18)),
            rect(-51.16, -25.20, -51.14, -25.18));

    -- Vegetacao cobre 3 quadrantes (SW + SE = metade sul, mais NW = 3/4 = 75%)
    INSERT INTO VEGETACAO_NATIVA (arq_bruto_id, ver_id, veg_area_ha, veg_geometria)
    VALUES (v_arq_veg_id, v_ver_veg_id,
            PKG_GEO.area_ha(rect(-51.16, -25.20, -51.14, -25.19)),
            rect(-51.16, -25.20, -51.14, -25.19));
    INSERT INTO VEGETACAO_NATIVA (arq_bruto_id, ver_id, veg_area_ha, veg_geometria)
    VALUES (v_arq_veg_id, v_ver_veg_id,
            PKG_GEO.area_ha(rect(-51.16, -25.19, -51.15, -25.18)),
            rect(-51.16, -25.19, -51.15, -25.18));

    -- Cenario 3: APP Sem Vegetacao Nativa (0% conservada, passivo = 100% da APP)
    -- Imovel: [-51.12, -25.20] a [-51.10, -25.18]
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-03', 'Fazenda Passivo Integral em APP', 'MANUAL',
            rect(-51.12, -25.20, -51.10, -25.18), PKG_GEO.area_ha(rect(-51.12, -25.20, -51.10, -25.18)))
    RETURNING imo_id INTO v_imo_03_id;

    -- APP ocupa metade oeste do imovel; nenhuma vegetacao toca o imovel
    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_03_id, v_arq_app_id, v_ver_app_id,
            PKG_GEO.area_ha(rect(-51.12, -25.20, -51.11, -25.18)),
            rect(-51.12, -25.20, -51.11, -25.18));

    -- Cenario 4: Imovel Sem APP (status = SEM_APP, campos numericos NULL)
    -- Imovel: [-51.08, -25.20] a [-51.06, -25.18]
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-04', 'Sitio Sem APP Incidente', 'MANUAL',
            rect(-51.08, -25.20, -51.06, -25.18), PKG_GEO.area_ha(rect(-51.08, -25.20, -51.06, -25.18)))
    RETURNING imo_id INTO v_imo_04_id;

    -- Cenario 5: APP com Poligonos Sobrepostos + APP Extrapolando Perimetro do Imovel
    -- Imovel: [-51.04, -25.20] a [-51.02, -25.18]
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-05', 'Fazenda APP Sobreposta e Extrapolada', 'MANUAL',
            rect(-51.04, -25.20, -51.02, -25.18), PKG_GEO.area_ha(rect(-51.04, -25.20, -51.02, -25.18)))
    RETURNING imo_id INTO v_imo_05_id;

    -- Poligono APP A: metade oeste [-51.04, -25.20] a [-51.03, -25.18] (2/4 do imovel)
    -- Poligono APP B: faixa central sobreposta a A e extrapolando o norte [-51.035, -25.20] a [-51.025, -25.17]
    -- Uniao (A U B) dentro do imovel = faixa [-51.04, -51.025] x [-25.20, -25.18] = 3/4 (75%) do imovel!
    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_05_id, v_arq_app_id, v_ver_app_id,
            PKG_GEO.area_ha(rect(-51.04, -25.20, -51.03, -25.18)),
            rect(-51.04, -25.20, -51.03, -25.18));

    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_05_id, v_arq_app_id, v_ver_app_id,
            PKG_GEO.area_ha(rect(-51.035, -25.20, -51.025, -25.17)),
            rect(-51.035, -25.20, -51.025, -25.17));

    -- Vegetacao cobre metade oeste [-51.04, -51.03] = 2/4 do imovel (2/3 da APP recortada = 66.6667%)
    INSERT INTO VEGETACAO_NATIVA (arq_bruto_id, ver_id, veg_area_ha, veg_geometria)
    VALUES (v_arq_veg_id, v_ver_veg_id,
            PKG_GEO.area_ha(rect(-51.04, -25.20, -51.03, -25.18)),
            rect(-51.04, -25.20, -51.03, -25.18));

    -- Cenario 6: Geometria Invalida (auto-intersecao / gravata-borboleta em APP para testar saneamento/quarentena)
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-06', 'Fazenda Teste Geometria Invalida', 'MANUAL',
            rect(-51.00, -25.20, -50.98, -25.18), PKG_GEO.area_ha(rect(-51.00, -25.20, -50.98, -25.18)))
    RETURNING imo_id INTO v_imo_06_id;

    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_06_id, v_arq_app_id, v_ver_app_id, 10.0,
            SDO_GEOMETRY(2003, 4674, NULL,
                SDO_ELEM_INFO_ARRAY(1, 1003, 1),
                SDO_ORDINATE_ARRAY(-51.00, -25.20, -50.99, -25.19, -51.00, -25.19, -50.99, -25.20, -51.00, -25.20)
            ));

    COMMIT;
END;
/