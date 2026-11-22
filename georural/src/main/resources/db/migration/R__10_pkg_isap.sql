-- =====================================================================
-- GeoRural DataHub - PKG_ISAP (repetivel)
-- Preparacao espacial, dissolucao e calculo do Indicador ISAP (US#24)
-- =====================================================================

CREATE OR REPLACE PACKAGE PKG_ISAP AS

    FUNCTION normalizar_filtro_uc(p_filtro VARCHAR2) RETURN VARCHAR2;

    FUNCTION uc_permitida(p_subtipo VARCHAR2, p_filtro VARCHAR2) RETURN NUMBER;

    PROCEDURE dissolver_areas_protegidas(
        p_ver_id      IN  NUMBER,
        p_filtro_uc   IN  VARCHAR2 DEFAULT '*',
        p_dissolvidas OUT NUMBER
    );

END PKG_ISAP;
/

CREATE OR REPLACE PACKAGE BODY PKG_ISAP AS

    FUNCTION normalizar_filtro_uc(p_filtro VARCHAR2) RETURN VARCHAR2 IS
        v_norm VARCHAR2(500);
    BEGIN
        v_norm := UPPER(TRIM(NVL(p_filtro, '*')));
        IF v_norm IS NULL OR v_norm = '*' OR v_norm = 'TODOS' OR v_norm = 'TODAS' THEN
            RETURN '*';
        END IF;
        v_norm := REGEXP_REPLACE(v_norm, '\s*,\s*', ',');
        RETURN v_norm;
    END normalizar_filtro_uc;

    FUNCTION uc_permitida(p_subtipo VARCHAR2, p_filtro VARCHAR2) RETURN NUMBER IS
        v_filtro  VARCHAR2(500) := normalizar_filtro_uc(p_filtro);
        v_subtipo VARCHAR2(100) := UPPER(TRIM(NVL(p_subtipo, '')));
    BEGIN
        IF v_filtro = '*' THEN
            RETURN 1;
        END IF;
        IF v_subtipo IS NULL THEN
            RETURN 0;
        END IF;
        IF INSTR(',' || v_filtro || ',', ',' || v_subtipo || ',') > 0 THEN
            RETURN 1;
        END IF;
        RETURN 0;
    END uc_permitida;

    PROCEDURE dissolver_areas_protegidas(
        p_ver_id      IN  NUMBER,
        p_filtro_uc   IN  VARCHAR2 DEFAULT '*',
        p_dissolvidas OUT NUMBER
    ) IS
        TYPE t_categorias IS VARRAY(4) OF VARCHAR2(20);
        v_categorias t_categorias := t_categorias('UC', 'TERRA_INDIGENA', 'QUILOMBOLA', 'FLORESTA_PUBLICA');
        v_tol        NUMBER := PKG_GEO.c_tol;
        v_cat        VARCHAR2(20);
        v_filtro     VARCHAR2(500);
        v_qtd        NUMBER;
        v_geom_union SDO_GEOMETRY;
        v_area_ha    NUMBER;
        v_total      NUMBER := 0;
    BEGIN
        IF p_ver_id IS NULL THEN
            p_dissolvidas := 0;
            RETURN;
        END IF;

        FOR idx IN 1 .. v_categorias.COUNT LOOP
            v_cat := v_categorias(idx);
            v_filtro := CASE WHEN v_cat = 'UC' THEN normalizar_filtro_uc(p_filtro_uc) ELSE '*' END;

            SELECT COUNT(*),
                   SDO_AGGR_UNION(SDOAGGRTYPE(a.apr_geometria, v_tol))
              INTO v_qtd,
                   v_geom_union
              FROM AREA_PROTEGIDA a
             WHERE a.ver_id = p_ver_id
               AND a.apr_tipo = v_cat
               AND a.apr_geometria IS NOT NULL
               AND (v_cat <> 'UC' OR uc_permitida(a.apr_subtipo, v_filtro) = 1);

            IF v_qtd > 0 AND v_geom_union IS NOT NULL THEN
                IF PKG_GEO.valida(v_geom_union) <> 'TRUE' THEN
                    v_geom_union := PKG_GEO.saneada(v_geom_union);
                END IF;

                v_area_ha := NVL(PKG_GEO.area_ha(v_geom_union), 0);

                MERGE INTO AREA_PROTEGIDA_DISSOLVIDA dst
                USING (SELECT p_ver_id AS ver_id,
                              v_cat    AS apd_categoria,
                              v_filtro AS apd_filtro_aplicado
                         FROM DUAL) src
                   ON (dst.ver_id = src.ver_id
                       AND dst.apd_categoria = src.apd_categoria
                       AND dst.apd_filtro_aplicado = src.apd_filtro_aplicado)
                 WHEN MATCHED THEN
                      UPDATE SET dst.apd_qtd_feicoes   = v_qtd,
                                 dst.apd_area_total_ha = v_area_ha,
                                 dst.apd_geometria     = v_geom_union,
                                 dst.apd_data_geracao  = SYSTIMESTAMP
                 WHEN NOT MATCHED THEN
                      INSERT (ver_id, apd_categoria, apd_filtro_aplicado,
                              apd_qtd_feicoes, apd_area_total_ha, apd_geometria, apd_data_geracao)
                      VALUES (p_ver_id, v_cat, v_filtro,
                              v_qtd, v_area_ha, v_geom_union, SYSTIMESTAMP);

                v_total := v_total + 1;
            ELSE
                DELETE FROM AREA_PROTEGIDA_DISSOLVIDA
                 WHERE ver_id = p_ver_id
                   AND apd_categoria = v_cat
                   AND apd_filtro_aplicado = v_filtro;
            END IF;
        END LOOP;

        p_dissolvidas := v_total;
    END dissolver_areas_protegidas;

END PKG_ISAP;
/