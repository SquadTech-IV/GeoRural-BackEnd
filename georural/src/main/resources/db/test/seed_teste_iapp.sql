-- =====================================================================
-- GeoRural DataHub - Massa de Teste Sintetica do IAPP (US#23.03)
-- SRID: 4674 (SIRGAS 2000) | Recorte territorial: Parana (PR)
-- =====================================================================

DECLARE
    v_usu_id      NUMBER;
    v_mun_id      NUMBER;
    v_fon_id      NUMBER;
    v_tip_imo_id  NUMBER;
    v_tip_app_id  NUMBER;
    v_tip_veg_id  NUMBER;
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

    v_geom        SDO_GEOMETRY;
    v_area        NUMBER;

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

    -- Garante ao menos uma FONTE_DO_DADO
    SELECT MIN(fon_id) INTO v_fon_id FROM FONTE_DO_DADO;
    IF v_fon_id IS NULL THEN
        INSERT INTO FONTE_DO_DADO (fon_nome, fon_sigla, fon_esfera)
        VALUES ('Base de Teste GeoRural', 'TESTE', 'ESTADUAL')
        RETURNING fon_id INTO v_fon_id;
    END IF;

    SELECT tip_id INTO v_tip_imo_id FROM TIPO_CONJUNTO WHERE tip_codigo = 'IMOVEL';
    SELECT tip_id INTO v_tip_app_id FROM TIPO_CONJUNTO WHERE tip_codigo = 'APP';
    SELECT tip_id INTO v_tip_veg_id FROM TIPO_CONJUNTO WHERE tip_codigo = 'VEGETACAO';

    -- Garante CONJUNTO_DADOS para IMOVEL, APP e VEGETACAO
    SELECT MIN(con_id) INTO v_con_imo_id FROM CONJUNTO_DADOS WHERE tip_id = v_tip_imo_id;
    IF v_con_imo_id IS NULL THEN
        INSERT INTO CONJUNTO_DADOS (fon_id, tip_id, usu_id, con_nome, con_situacao)
        VALUES (v_fon_id, v_tip_imo_id, v_usu_id, 'Imoveis Rurais (Teste IAPP)', 'PUBLICADO')
        RETURNING con_id INTO v_con_imo_id;
    END IF;

    SELECT MIN(con_id) INTO v_con_app_id FROM CONJUNTO_DADOS WHERE tip_id = v_tip_app_id;
    IF v_con_app_id IS NULL THEN
        INSERT INTO CONJUNTO_DADOS (fon_id, tip_id, usu_id, con_nome, con_situacao)
        VALUES (v_fon_id, v_tip_app_id, v_usu_id, 'APP Declarada (Teste IAPP)', 'PUBLICADO')
        RETURNING con_id INTO v_con_app_id;
    END IF;

    SELECT MIN(con_id) INTO v_con_veg_id FROM CONJUNTO_DADOS WHERE tip_id = v_tip_veg_id;
    IF v_con_veg_id IS NULL THEN
        INSERT INTO CONJUNTO_DADOS (fon_id, tip_id, usu_id, con_nome, con_situacao)
        VALUES (v_fon_id, v_tip_veg_id, v_usu_id, 'Vegetacao Nativa (Teste IAPP)', 'PUBLICADO')
        RETURNING con_id INTO v_con_veg_id;
    END IF;

    -- Arquivos brutos sinteticos (com usu_id preenchido - NOT NULL na V7)
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

    -- Versoes de teste (ck_ver_situacao aceita RASCUNHO, VIGENTE, ANTERIOR)
    INSERT INTO VERSAO (con_id, usu_id, ver_numero, ver_hash, ver_competencia, ver_situacao, ver_data_publicacao)
    VALUES (v_con_imo_id, v_usu_id, 9901, LPAD('11', 64, '0'), '2026-11', 'RASCUNHO', NULL)
    RETURNING ver_id INTO v_ver_imo_id;

    INSERT INTO VERSAO (con_id, usu_id, ver_numero, ver_hash, ver_competencia, ver_situacao, ver_data_publicacao)
    VALUES (v_con_app_id, v_usu_id, 9901, LPAD('22', 64, '0'), '2026-11', 'VIGENTE', SYSTIMESTAMP)
    RETURNING ver_id INTO v_ver_app_id;

    INSERT INTO VERSAO (con_id, usu_id, ver_numero, ver_hash, ver_competencia, ver_situacao, ver_data_publicacao)
    VALUES (v_con_veg_id, v_usu_id, 9901, LPAD('33', 64, '0'), '2026-11', 'VIGENTE', SYSTIMESTAMP)
    RETURNING ver_id INTO v_ver_veg_id;

    UPDATE CONJUNTO_DADOS SET con_ver_vigente_id = v_ver_app_id WHERE con_id = v_con_app_id;
    UPDATE CONJUNTO_DADOS SET con_ver_vigente_id = v_ver_veg_id WHERE con_id = v_con_veg_id;

    -- =================================================================
    -- Cenario 1: APP Totalmente Vegetada (100% conservada, passivo = 0)
    -- =================================================================
    v_geom := rect(-51.20, -25.20, -51.18, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-01', 'Fazenda APP Integralmente Preservada', 'MANUAL', v_geom, v_area)
    RETURNING imo_id INTO v_imo_01_id;

    v_geom := rect(-51.20, -25.20, -51.18, -25.19);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_01_id, v_arq_app_id, v_ver_app_id, v_area, v_geom);

    v_geom := rect(-51.20, -25.20, -51.18, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO VEGETACAO_NATIVA (arq_bruto_id, ver_id, veg_area_ha, veg_geometria)
    VALUES (v_arq_veg_id, v_ver_veg_id, v_area, v_geom);

    -- =================================================================
    -- Cenario 2: APP Parcialmente Vegetada (75% conservada, 25% passivo)
    -- =================================================================
    v_geom := rect(-51.16, -25.20, -51.14, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-02', 'Fazenda APP Parcialmente Vegetada', 'MANUAL', v_geom, v_area)
    RETURNING imo_id INTO v_imo_02_id;

    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_02_id, v_arq_app_id, v_ver_app_id, v_area, v_geom);

    v_geom := rect(-51.16, -25.20, -51.14, -25.19);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO VEGETACAO_NATIVA (arq_bruto_id, ver_id, veg_area_ha, veg_geometria)
    VALUES (v_arq_veg_id, v_ver_veg_id, v_area, v_geom);

    v_geom := rect(-51.16, -25.19, -51.15, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO VEGETACAO_NATIVA (arq_bruto_id, ver_id, veg_area_ha, veg_geometria)
    VALUES (v_arq_veg_id, v_ver_veg_id, v_area, v_geom);

    -- =================================================================
    -- Cenario 3: APP Sem Vegetacao Nativa (0% conservada, passivo = 100%)
    -- =================================================================
    v_geom := rect(-51.12, -25.20, -51.10, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-03', 'Fazenda Passivo Integral em APP', 'MANUAL', v_geom, v_area)
    RETURNING imo_id INTO v_imo_03_id;

    v_geom := rect(-51.12, -25.20, -51.11, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_03_id, v_arq_app_id, v_ver_app_id, v_area, v_geom);

    -- =================================================================
    -- Cenario 4: Imovel Sem APP (status = SEM_APP, campos numericos NULL)
    -- =================================================================
    v_geom := rect(-51.08, -25.20, -51.06, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-04', 'Sitio Sem APP Incidente', 'MANUAL', v_geom, v_area)
    RETURNING imo_id INTO v_imo_04_id;

    -- =================================================================
    -- Cenario 5: APP com Poligonos Sobrepostos + Extrapolando Perimetro
    -- =================================================================
    v_geom := rect(-51.04, -25.20, -51.02, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-05', 'Fazenda APP Sobreposta e Extrapolada', 'MANUAL', v_geom, v_area)
    RETURNING imo_id INTO v_imo_05_id;

    v_geom := rect(-51.04, -25.20, -51.03, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_05_id, v_arq_app_id, v_ver_app_id, v_area, v_geom);

    v_geom := rect(-51.035, -25.20, -51.025, -25.17);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_05_id, v_arq_app_id, v_ver_app_id, v_area, v_geom);

    v_geom := rect(-51.04, -25.20, -51.03, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO VEGETACAO_NATIVA (arq_bruto_id, ver_id, veg_area_ha, veg_geometria)
    VALUES (v_arq_veg_id, v_ver_veg_id, v_area, v_geom);

    -- =================================================================
    -- Cenario 6: Geometria Invalida (gravata-borboleta em APP)
    -- =================================================================
    v_geom := rect(-51.00, -25.20, -50.98, -25.18);
    v_area := PKG_GEO.area_ha(v_geom);
    INSERT INTO IMOVEL_RURAL (mun_id, arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_mun_id, v_arq_imo_id, v_ver_imo_id, 'PR-TESTE-IAPP-06', 'Fazenda Teste Geometria Invalida', 'MANUAL', v_geom, v_area)
    RETURNING imo_id INTO v_imo_06_id;

    v_geom := SDO_GEOMETRY(
        2003, 4674, NULL,
        SDO_ELEM_INFO_ARRAY(1, 1003, 1),
        SDO_ORDINATE_ARRAY(-51.00, -25.20, -50.99, -25.19, -51.00, -25.19, -50.99, -25.20, -51.00, -25.20)
    );
    INSERT INTO APP (imo_id, arq_bruto_id, ver_id, app_area_ha, app_geometria)
    VALUES (v_imo_06_id, v_arq_app_id, v_ver_app_id, 10.0, v_geom);

    COMMIT;
END;
/