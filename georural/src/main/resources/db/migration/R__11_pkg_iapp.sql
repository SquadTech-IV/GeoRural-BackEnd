-- =====================================================================
-- GeoRural DataHub - PKG_IAPP (US#23.05 - Preparacao Espacial)
-- =====================================================================

CREATE OR REPLACE PACKAGE PKG_IAPP AS
    c_srid_oficial CONSTANT NUMBER := 4674;

    FUNCTION normalizar_filtro(p_filtro VARCHAR2) RETURN VARCHAR2;
    FUNCTION item_permitido(p_valor VARCHAR2, p_filtro VARCHAR2) RETURN NUMBER;

    -- Garante SRID 4674 (reprojeta via SDO_CS.TRANSFORM quando necessario)
    FUNCTION normalizar_srid(p_geom SDO_GEOMETRY) RETURN SDO_GEOMETRY;

    -- Valida, tenta corrigir e, se irrecuperavel, registra na QUARENTENA sem abortar o lote
    FUNCTION preparar_geometria(
        p_geom          IN SDO_GEOMETRY,
        p_arq_bruto_id  IN NUMBER,
        p_identificador IN VARCHAR2,
        p_coluna        IN VARCHAR2
    ) RETURN SDO_GEOMETRY;

    -- Dissolve APP e VEGETACAO_NATIVA por versao e filtro (SDO_AGGR_UNION) evitando dupla contagem
    PROCEDURE dissolver_camadas(
        p_app_ver_id          IN  NUMBER,
        p_veg_ver_id          IN  NUMBER,
        p_filtro_tipos_app    IN  VARCHAR2 DEFAULT '*',
        p_filtro_classes_veg  IN  VARCHAR2 DEFAULT '*',
        p_app_feicoes_validas OUT NUMBER,
        p_veg_feicoes_validas OUT NUMBER
    );
END PKG_IAPP;
/

CREATE OR REPLACE PACKAGE BODY PKG_IAPP AS

    FUNCTION normalizar_filtro(p_filtro VARCHAR2) RETURN VARCHAR2 IS
        v_limpo VARCHAR2(500);
    BEGIN
        v_limpo := UPPER(TRIM(NVL(p_filtro, '*')));
        IF v_limpo IS NULL OR v_limpo = '*' OR v_limpo = 'TODOS' OR v_limpo = 'TODAS' THEN
            RETURN '*';
        END IF;
        RETURN REGEXP_REPLACE(v_limpo, '\s*,\s*', ',');
    END normalizar_filtro;

    FUNCTION item_permitido(p_valor VARCHAR2, p_filtro VARCHAR2) RETURN NUMBER IS
        v_filtro VARCHAR2(500) := normalizar_filtro(p_filtro);
        v_val    VARCHAR2(200) := UPPER(TRIM(NVL(p_valor, '')));
    BEGIN
        IF v_filtro = '*' THEN
            RETURN 1;
        END IF;
        IF v_val IS NULL THEN
            RETURN 0;
        END IF;
        IF INSTR(',' || v_filtro || ',', ',' || v_val || ',') > 0 THEN
            RETURN 1;
        END IF;
        RETURN 0;
    END item_permitido;

    FUNCTION normalizar_srid(p_geom SDO_GEOMETRY) RETURN SDO_GEOMETRY IS
        v_geom SDO_GEOMETRY := p_geom;
    BEGIN
        IF v_geom IS NULL THEN
            RETURN NULL;
        END IF;

        IF v_geom.SDO_SRID IS NULL THEN
            v_geom.SDO_SRID := c_srid_oficial;
            RETURN v_geom;
        END IF;

        IF v_geom.SDO_SRID <> c_srid_oficial THEN
            RETURN SDO_CS.TRANSFORM(v_geom, c_srid_oficial);
        END IF;

        RETURN v_geom;
    EXCEPTION
        WHEN OTHERS THEN
            RETURN NULL;
    END normalizar_srid;

    PROCEDURE registrar_quarentena_geometria(
        p_arq_bruto_id  IN NUMBER,
        p_identificador IN VARCHAR2,
        p_coluna        IN VARCHAR2,
        p_motivo        IN VARCHAR2
    ) IS
        PRAGMA AUTONOMOUS_TRANSACTION;
        v_val_id NUMBER;
        v_reg_id NUMBER;
    BEGIN
        IF p_arq_bruto_id IS NULL THEN
            RETURN;
        END IF;

        SELECT MAX(val_id) INTO v_val_id
          FROM VALIDACAO
         WHERE arq_bruto_id = p_arq_bruto_id;

        IF v_val_id IS NULL THEN
            INSERT INTO VALIDACAO (arq_bruto_id, val_status_final)
            VALUES (p_arq_bruto_id, 'REJEITADO')
            RETURNING val_id INTO v_val_id;
        END IF;

        SELECT MAX(reg_id) INTO v_reg_id
          FROM REGRA_QUALIDADE
         WHERE reg_nome = 'Geometria invalida';

        INSERT INTO QUARENTENA (
            val_id, reg_id, qua_etapa, qua_identificador, qua_coluna_erro,
            qua_valor_encontrado, qua_valor_esperado, qua_motivo_erro, qua_situacao
        ) VALUES (
            v_val_id, v_reg_id, 'CALCULO', SUBSTR(p_identificador, 1, 120), p_coluna,
            SUBSTR(p_motivo, 1, 400), 'Poligono valido em SRID 4674',
            SUBSTR('Geometria invalida irrecuperavel descartada no IAPP: ' || p_motivo, 1, 400),
            'PENDENTE'
        );

        COMMIT;
    EXCEPTION
        WHEN OTHERS THEN
            ROLLBACK;
    END registrar_quarentena_geometria;

    FUNCTION preparar_geometria(
        p_geom          IN SDO_GEOMETRY,
        p_arq_bruto_id  IN NUMBER,
        p_identificador IN VARCHAR2,
        p_coluna        IN VARCHAR2
    ) RETURN SDO_GEOMETRY IS
        v_geom   SDO_GEOMETRY;
        v_status VARCHAR2(4000);
    BEGIN
        IF p_geom IS NULL THEN
            registrar_quarentena_geometria(p_arq_bruto_id, p_identificador, p_coluna, 'NULA');
            RETURN NULL;
        END IF;

        v_geom := normalizar_srid(p_geom);
        IF v_geom IS NULL THEN
            registrar_quarentena_geometria(p_arq_bruto_id, p_identificador, p_coluna, 'Falha na transformacao de SRID para 4674');
            RETURN NULL;
        END IF;

        v_status := PKG_GEO.valida(v_geom);
        IF v_status = 'TRUE' THEN
            RETURN v_geom;
        END IF;

        -- Tenta saneamento automatico (SDO_UTIL.RECTIFY_GEOMETRY)
        BEGIN
            v_geom := PKG_GEO.saneada(v_geom);
            v_status := PKG_GEO.valida(v_geom);
            IF v_geom IS NOT NULL AND v_status = 'TRUE' THEN
                RETURN v_geom;
            END IF;
        EXCEPTION
            WHEN OTHERS THEN
                v_status := SQLERRM;
        END;

        registrar_quarentena_geometria(p_arq_bruto_id, p_identificador, p_coluna, NVL(v_status, 'INVALIDA'));
        RETURN NULL;
    END preparar_geometria;

    PROCEDURE dissolver_camadas(
        p_app_ver_id          IN  NUMBER,
        p_veg_ver_id          IN  NUMBER,
        p_filtro_tipos_app    IN  VARCHAR2 DEFAULT '*',
        p_filtro_classes_veg  IN  VARCHAR2 DEFAULT '*',
        p_app_feicoes_validas OUT NUMBER,
        p_veg_feicoes_validas OUT NUMBER
    ) IS
        v_filtro_app VARCHAR2(500) := normalizar_filtro(p_filtro_tipos_app);
        v_filtro_veg VARCHAR2(500) := normalizar_filtro(p_filtro_classes_veg);
        v_tol        NUMBER := PKG_GEO.c_tol;
        v_app_union  SDO_GEOMETRY := NULL;
        v_veg_union  SDO_GEOMETRY := NULL;
    BEGIN
        p_app_feicoes_validas := 0;
        p_veg_feicoes_validas := 0;

        -- 1. Preparacao e dissolucao da camada APP
        IF p_app_ver_id IS NOT NULL THEN
            SELECT COUNT(*),
                   SDO_AGGR_UNION(SDOAGGRTYPE(prep.geom, v_tol))
              INTO p_app_feicoes_validas,
                   v_app_union
              FROM (
                  SELECT PKG_IAPP.preparar_geometria(
                             a.app_geometria, a.arq_bruto_id, 'APP#' || a.app_id, 'APP_GEOMETRIA'
                         ) AS geom
                    FROM APP a
                   WHERE a.ver_id = p_app_ver_id
                     AND PKG_IAPP.item_permitido(a.app_tipo, v_filtro_app) = 1
              ) prep
             WHERE prep.geom IS NOT NULL;

            IF v_app_union IS NOT NULL AND PKG_GEO.valida(v_app_union) <> 'TRUE' THEN
                v_app_union := PKG_GEO.saneada(v_app_union);
            END IF;

            MERGE INTO APP_DISSOLVIDA dst
            USING (SELECT p_app_ver_id AS ver_id, v_filtro_app AS filtro FROM DUAL) src
               ON (dst.ver_id = src.ver_id AND dst.apd_filtro_aplicado = src.filtro)
             WHEN MATCHED THEN
                  UPDATE SET dst.apd_qtd_feicoes   = p_app_feicoes_validas,
                             dst.apd_area_total_ha = PKG_GEO.area_ha(v_app_union),
                             dst.apd_geometria     = v_app_union,
                             dst.apd_data_geracao  = SYSTIMESTAMP
             WHEN NOT MATCHED THEN
                  INSERT (ver_id, apd_filtro_aplicado, apd_qtd_feicoes, apd_area_total_ha, apd_geometria)
                  VALUES (p_app_ver_id, v_filtro_app, p_app_feicoes_validas, PKG_GEO.area_ha(v_app_union), v_app_union);
        END IF;

        -- 2. Preparacao e dissolucao da camada VEGETACAO_NATIVA
        IF p_veg_ver_id IS NOT NULL THEN
            SELECT COUNT(*),
                   SDO_AGGR_UNION(SDOAGGRTYPE(prep.geom, v_tol))
              INTO p_veg_feicoes_validas,
                   v_veg_union
              FROM (
                  SELECT PKG_IAPP.preparar_geometria(
                             v.veg_geometria, v.arq_bruto_id, 'VEGETACAO#' || v.veg_id, 'VEG_GEOMETRIA'
                         ) AS geom
                    FROM VEGETACAO_NATIVA v
                   WHERE v.ver_id = p_veg_ver_id
                     AND PKG_IAPP.item_permitido(v.veg_classe, v_filtro_veg) = 1
              ) prep
             WHERE prep.geom IS NOT NULL;

            IF v_veg_union IS NOT NULL AND PKG_GEO.valida(v_veg_union) <> 'TRUE' THEN
                v_veg_union := PKG_GEO.saneada(v_veg_union);
            END IF;

            MERGE INTO VEGETACAO_DISSOLVIDA dst
            USING (SELECT p_veg_ver_id AS ver_id, v_filtro_veg AS filtro FROM DUAL) src
               ON (dst.ver_id = src.ver_id AND dst.ved_filtro_aplicado = src.filtro)
             WHEN MATCHED THEN
                  UPDATE SET dst.ved_qtd_feicoes   = p_veg_feicoes_validas,
                             dst.ved_area_total_ha = PKG_GEO.area_ha(v_veg_union),
                             dst.ved_geometria     = v_veg_union,
                             dst.ved_data_geracao  = SYSTIMESTAMP
             WHEN NOT MATCHED THEN
                  INSERT (ver_id, ved_filtro_aplicado, ved_qtd_feicoes, ved_area_total_ha, ved_geometria)
                  VALUES (p_veg_ver_id, v_filtro_veg, p_veg_feicoes_validas, PKG_GEO.area_ha(v_veg_union), v_veg_union);
        END IF;
    END dissolver_camadas;

END PKG_IAPP;
/