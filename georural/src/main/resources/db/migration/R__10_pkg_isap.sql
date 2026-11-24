-- =====================================================================
-- GeoRural DataHub - PKG_ISAP (repetivel)
-- Preparacao espacial, dissolucao e calculo do Indicador ISAP (US#24)
-- =====================================================================

CREATE OR REPLACE PACKAGE PKG_ISAP AS

    FUNCTION normalizar_filtro_uc(p_filtro VARCHAR2) RETURN VARCHAR2;

    FUNCTION uc_permitida(p_subtipo VARCHAR2, p_filtro VARCHAR2) RETURN NUMBER;

    -- Obtem o valor configurado do parametro tipos_uc_incluidos da regra ISAP
    FUNCTION obter_filtro_uc(p_rca_id NUMBER DEFAULT NULL) RETURN VARCHAR2;

    -- Resolve a versao vigente/mais recente disponivel para uma categoria especifica (US#24.07)
    FUNCTION resolver_versao_categoria(p_categoria VARCHAR2) RETURN NUMBER;

    -- Dissolve poligonos de AREA_PROTEGIDA por categoria (UC, TERRA_INDIGENA, QUILOMBOLA, FLORESTA_PUBLICA)
    -- eliminando dupla contagem intra-categoria via SDO_AGGR_UNION + RECTIFY_GEOMETRY (US#24.05)
    PROCEDURE dissolver_areas_protegidas(
        p_ver_id      IN  NUMBER,
        p_filtro_uc   IN  VARCHAR2 DEFAULT '*',
        p_dissolvidas OUT NUMBER
    );

    -- Calcula as 4 categorias + TOTAL (sem dupla contagem inter-categorias) e o detalhe por feicao
    -- para um imovel especifico. Se p_camada_ver_id for NULL, resolve por categoria (US#24.06 / US#24.07)
    PROCEDURE calcular_imovel(
        p_imo_id        IN NUMBER,
        p_ver_id        IN NUMBER,
        p_camada_ver_id IN NUMBER DEFAULT NULL,
        p_exe_id        IN NUMBER DEFAULT NULL,
        p_rca_id        IN NUMBER DEFAULT NULL,
        p_filtro_uc     IN VARCHAR2 DEFAULT NULL
    );

    -- Calcula o ISAP em lote para os imoveis no escopo (US#24.06 / US#24.07)
    -- p_escopo: 'IMOVEL' (imoveis da versao p_ver_id) ou 'TODOS' (todos os imoveis com geometria)
    PROCEDURE calcular_lote(
        p_ver_id        IN  NUMBER,
        p_camada_ver_id IN  NUMBER DEFAULT NULL,
        p_exe_id        IN  NUMBER DEFAULT NULL,
        p_rca_id        IN  NUMBER DEFAULT NULL,
        p_escopo        IN  VARCHAR2 DEFAULT 'TODOS',
        p_calculados    OUT NUMBER
    );

    -- Recalcula o ISAP de todos os imoveis apos alteracao do parametro tipos_uc_incluidos (US#24.07)
    PROCEDURE recalcular_por_parametro(
        p_rca_id     IN  NUMBER,
        p_calculados OUT NUMBER
    );

    -- Promove os resultados do ISAP de p_ver_id a vigentes sem apagar o historico anterior (US#24.08)
    PROCEDURE publicar(
        p_ver_id     IN  NUMBER,
        p_publicados OUT NUMBER
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

    FUNCTION obter_filtro_uc(p_rca_id NUMBER DEFAULT NULL) RETURN VARCHAR2 IS
        v_valor VARCHAR2(500);
    BEGIN
        IF p_rca_id IS NOT NULL THEN
            SELECT prc_valor INTO v_valor
              FROM PARAMETRO_REGRA_CALCULO
             WHERE rca_id = p_rca_id
               AND prc_nome = 'tipos_uc_incluidos';
            RETURN normalizar_filtro_uc(v_valor);
        END IF;

        SELECT p.prc_valor INTO v_valor
          FROM PARAMETRO_REGRA_CALCULO p
          JOIN REGRA_CALCULO r ON r.rca_id = p.rca_id
          JOIN INDICADOR i ON i.ind_id = r.ind_id
         WHERE i.ind_sigla = 'ISAP'
           AND r.rca_vigente = 'S'
           AND p.prc_nome = 'tipos_uc_incluidos'
         FETCH FIRST 1 ROWS ONLY;

        RETURN normalizar_filtro_uc(v_valor);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN '*';
    END obter_filtro_uc;

    FUNCTION resolver_versao_categoria(p_categoria VARCHAR2) RETURN NUMBER IS
        v_ver_id NUMBER;
    BEGIN
        SELECT MAX(v.ver_id)
          INTO v_ver_id
          FROM VERSAO v
         WHERE v.ver_situacao = 'VIGENTE'
           AND EXISTS (
               SELECT 1
                 FROM AREA_PROTEGIDA a
                WHERE a.ver_id = v.ver_id
                  AND a.apr_tipo = p_categoria
                  AND a.apr_geometria IS NOT NULL
           );

        IF v_ver_id IS NOT NULL THEN
            RETURN v_ver_id;
        END IF;

        SELECT MAX(a.ver_id)
          INTO v_ver_id
          FROM AREA_PROTEGIDA a
         WHERE a.apr_tipo = p_categoria
           AND a.apr_geometria IS NOT NULL;

        RETURN v_ver_id;
    END resolver_versao_categoria;

    FUNCTION validar_versao_existente(p_ver_id NUMBER) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        IF p_ver_id IS NULL THEN
            RETURN NULL;
        END IF;
        SELECT ver_id INTO v_id
          FROM VERSAO
         WHERE ver_id = p_ver_id;
        RETURN v_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN NULL;
    END validar_versao_existente;

    FUNCTION obter_competencia_versao(p_ver_id NUMBER) RETURN VARCHAR2 IS
        v_comp VARCHAR2(20);
    BEGIN
        IF p_ver_id IS NULL THEN
            RETURN NULL;
        END IF;
        SELECT ver_competencia INTO v_comp
          FROM VERSAO
         WHERE ver_id = p_ver_id;
        RETURN v_comp;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN NULL;
    END obter_competencia_versao;

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
               AND (v_cat <> 'UC' OR PKG_ISAP.uc_permitida(a.apr_subtipo, v_filtro) = 1);

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
                                 dst.apd_data_geracao  = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP)
                 WHEN NOT MATCHED THEN
                      INSERT (ver_id, apd_categoria, apd_filtro_aplicado,
                              apd_qtd_feicoes, apd_area_total_ha, apd_geometria, apd_data_geracao)
                      VALUES (p_ver_id, v_cat, v_filtro,
                              v_qtd, v_area_ha, v_geom_union, CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP));

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

    PROCEDURE gravar_resumo(
        p_imo_id           IN  NUMBER,
        p_ver_id           IN  NUMBER,
        p_camada_ver_id    IN  NUMBER,
        p_exe_id           IN  NUMBER,
        p_rca_id           IN  NUMBER,
        p_categoria        IN  VARCHAR2,
        p_sobrepoe         IN  CHAR,
        p_area_sobrep_ha   IN  NUMBER,
        p_pct_sobrep       IN  NUMBER,
        p_area_imovel_ha   IN  NUMBER,
        p_filtro_aplicado  IN  VARCHAR2,
        p_competencia      IN  VARCHAR2,
        p_status           IN  VARCHAR2,
        p_isr_id           OUT NUMBER
    ) IS
    BEGIN
        MERGE INTO RESULTADO_ISAP_RESUMO dst
        USING (SELECT p_imo_id AS imo_id, p_ver_id AS ver_id, p_categoria AS isr_categoria FROM DUAL) src
           ON (dst.imo_id = src.imo_id AND dst.ver_id = src.ver_id AND dst.isr_categoria = src.isr_categoria)
         WHEN MATCHED THEN
              UPDATE SET dst.isr_camada_ver_id      = p_camada_ver_id,
                         dst.exe_id                 = p_exe_id,
                         dst.rca_id                 = p_rca_id,
                         dst.isr_sobrepoe           = p_sobrepoe,
                         dst.isr_area_sobreposta_ha = p_area_sobrep_ha,
                         dst.isr_pct_sobreposicao   = p_pct_sobrep,
                         dst.isr_area_imovel_ha     = p_area_imovel_ha,
                         dst.isr_filtro_aplicado    = p_filtro_aplicado,
                         dst.isr_competencia_camada = p_competencia,
                         dst.isr_status             = p_status,
                         dst.isr_data_calculo       = CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP)
         WHEN NOT MATCHED THEN
              INSERT (imo_id, ver_id, isr_camada_ver_id, exe_id, rca_id, isr_categoria,
                      isr_sobrepoe, isr_area_sobreposta_ha, isr_pct_sobreposicao,
                      isr_area_imovel_ha, isr_filtro_aplicado, isr_competencia_camada,
                      isr_status, isr_vigente, isr_data_calculo)
              VALUES (p_imo_id, p_ver_id, p_camada_ver_id, p_exe_id, p_rca_id, p_categoria,
                      p_sobrepoe, p_area_sobrep_ha, p_pct_sobrep,
                      p_area_imovel_ha, p_filtro_aplicado, p_competencia,
                      p_status, 'N', CAST(SYSTIMESTAMP AT TIME ZONE 'America/Sao_Paulo' AS TIMESTAMP));

        SELECT isr_id INTO p_isr_id
          FROM RESULTADO_ISAP_RESUMO
         WHERE imo_id = p_imo_id
           AND ver_id = p_ver_id
           AND isr_categoria = p_categoria;
    END gravar_resumo;

    PROCEDURE calcular_imovel(
        p_imo_id        IN NUMBER,
        p_ver_id        IN NUMBER,
        p_camada_ver_id IN NUMBER DEFAULT NULL,
        p_exe_id        IN NUMBER DEFAULT NULL,
        p_rca_id        IN NUMBER DEFAULT NULL,
        p_filtro_uc     IN VARCHAR2 DEFAULT NULL
    ) IS
        TYPE t_categorias IS VARRAY(4) OF VARCHAR2(20);
        v_categorias         t_categorias := t_categorias('UC', 'TERRA_INDIGENA', 'QUILOMBOLA', 'FLORESTA_PUBLICA');
        v_tol                NUMBER := PKG_GEO.c_tol;
        v_imo_geom           SDO_GEOMETRY;
        v_imo_area_ha        NUMBER;
        v_filtro_uc          VARCHAR2(500);
        v_cat                VARCHAR2(20);
        v_cat_ver_id         NUMBER;
        v_cat_competencia    VARCHAR2(20);
        v_total_ver_id       NUMBER := validar_versao_existente(p_camada_ver_id);
        v_total_competencia  VARCHAR2(20);
        v_filtro_cat         VARCHAR2(500);
        v_apd_geom           SDO_GEOMETRY;
        v_inter_geom         SDO_GEOMETRY;
        v_total_geom         SDO_GEOMETRY;
        v_area_cat_ha        NUMBER;
        v_pct_cat            NUMBER;
        v_isr_id             NUMBER;
        v_feat_area_ha       NUMBER;
        v_feat_pct           NUMBER;
        v_cats_disponiveis   NUMBER := 0;
        v_cats_sobrepostas   NUMBER := 0;
        v_ultima_area_ha     NUMBER := 0;
        v_area_total_ha      NUMBER;
        v_pct_total          NUMBER;
        v_status_total       VARCHAR2(25);
        v_sobrepoe_total     CHAR(1);
    BEGIN
        SELECT imo_geometria,
               NVL(imo_area_total, PKG_GEO.area_ha(imo_geometria))
          INTO v_imo_geom, v_imo_area_ha
          FROM IMOVEL_RURAL
         WHERE imo_id = p_imo_id;

        IF v_imo_geom IS NULL OR NVL(v_imo_area_ha, 0) <= 0 THEN
            RETURN;
        END IF;

        IF PKG_GEO.valida(v_imo_geom) <> 'TRUE' THEN
            v_imo_geom := PKG_GEO.saneada(v_imo_geom);
        END IF;

        v_filtro_uc := normalizar_filtro_uc(NVL(p_filtro_uc, obter_filtro_uc(p_rca_id)));

        DELETE FROM RESULTADO_ISAP_DETALHE
         WHERE imo_id = p_imo_id
           AND ver_id = p_ver_id;

        FOR idx IN 1 .. v_categorias.COUNT LOOP
            v_cat := v_categorias(idx);
            v_filtro_cat := CASE WHEN v_cat = 'UC' THEN v_filtro_uc ELSE NULL END;
            v_cat_ver_id := validar_versao_existente(NVL(p_camada_ver_id, resolver_versao_categoria(v_cat)));
            v_cat_competencia := obter_competencia_versao(v_cat_ver_id);

            IF v_cat_ver_id IS NOT NULL AND (v_total_ver_id IS NULL OR v_cat_ver_id > v_total_ver_id) THEN
                v_total_ver_id := v_cat_ver_id;
            END IF;

            v_apd_geom := NULL;
            IF v_cat_ver_id IS NOT NULL THEN
                BEGIN
                    SELECT apd_geometria INTO v_apd_geom
                      FROM AREA_PROTEGIDA_DISSOLVIDA
                     WHERE ver_id = v_cat_ver_id
                       AND apd_categoria = v_cat
                       AND apd_filtro_aplicado = NVL(v_filtro_cat, '*');
                EXCEPTION
                    WHEN NO_DATA_FOUND THEN
                        v_apd_geom := NULL;
                END;
            END IF;

            IF v_apd_geom IS NULL THEN
                gravar_resumo(
                    p_imo_id          => p_imo_id,
                    p_ver_id          => p_ver_id,
                    p_camada_ver_id   => v_cat_ver_id,
                    p_exe_id          => p_exe_id,
                    p_rca_id          => p_rca_id,
                    p_categoria       => v_cat,
                    p_sobrepoe        => NULL,
                    p_area_sobrep_ha  => NULL,
                    p_pct_sobrep      => NULL,
                    p_area_imovel_ha  => ROUND(v_imo_area_ha, 4),
                    p_filtro_aplicado => v_filtro_cat,
                    p_competencia     => v_cat_competencia,
                    p_status          => 'CAMADA_INDISPONIVEL',
                    p_isr_id          => v_isr_id
                );
                CONTINUE;
            END IF;

            v_cats_disponiveis := v_cats_disponiveis + 1;

            v_inter_geom := NULL;
            v_area_cat_ha := 0;

            IF SDO_GEOM.RELATE(v_imo_geom, 'ANYINTERACT', v_apd_geom, v_tol) = 'TRUE' THEN
                v_inter_geom := PKG_GEO.intersecao(v_imo_geom, v_apd_geom);
                IF v_inter_geom IS NOT NULL THEN
                    v_area_cat_ha := LEAST(NVL(PKG_GEO.area_ha(v_inter_geom), 0), v_imo_area_ha);
                END IF;
            END IF;

            v_area_cat_ha := ROUND(NVL(v_area_cat_ha, 0), 4);

            IF v_area_cat_ha > 0 THEN
                v_pct_cat := LEAST(ROUND((v_area_cat_ha / v_imo_area_ha) * 100, 4), 100);
                v_cats_sobrepostas := v_cats_sobrepostas + 1;
                v_ultima_area_ha := v_area_cat_ha;

                IF v_total_geom IS NULL THEN
                    v_total_geom := v_inter_geom;
                ELSE
                    v_total_geom := SDO_GEOM.SDO_UNION(v_total_geom, v_inter_geom, v_tol);
                END IF;

                gravar_resumo(
                    p_imo_id          => p_imo_id,
                    p_ver_id          => p_ver_id,
                    p_camada_ver_id   => v_cat_ver_id,
                    p_exe_id          => p_exe_id,
                    p_rca_id          => p_rca_id,
                    p_categoria       => v_cat,
                    p_sobrepoe        => 'S',
                    p_area_sobrep_ha  => v_area_cat_ha,
                    p_pct_sobrep      => v_pct_cat,
                    p_area_imovel_ha  => ROUND(v_imo_area_ha, 4),
                    p_filtro_aplicado => v_filtro_cat,
                    p_competencia     => v_cat_competencia,
                    p_status          => 'COM_SOBREPOSICAO',
                    p_isr_id          => v_isr_id
                );

                FOR feat IN (
                    SELECT a.apr_id,
                           a.apr_nome,
                           NVL(a.apr_subtipo, a.apr_tipo) AS tipo_feicao,
                           a.apr_codigo,
                           a.apr_geometria
                      FROM AREA_PROTEGIDA a
                     WHERE a.ver_id = v_cat_ver_id
                       AND a.apr_tipo = v_cat
                       AND a.apr_geometria IS NOT NULL
                       AND (v_cat <> 'UC' OR PKG_ISAP.uc_permitida(a.apr_subtipo, v_filtro_uc) = 1)
                       AND SDO_RELATE(a.apr_geometria, v_imo_geom, 'mask=ANYINTERACT') = 'TRUE'
                ) LOOP
                    v_feat_area_ha := LEAST(
                        NVL(PKG_GEO.area_intersecao_ha(v_imo_geom, feat.apr_geometria), 0),
                        v_imo_area_ha
                    );
                    v_feat_area_ha := ROUND(v_feat_area_ha, 4);

                    IF v_feat_area_ha > 0 THEN
                        v_feat_pct := LEAST(ROUND((v_feat_area_ha / v_imo_area_ha) * 100, 4), 100);

                        INSERT INTO RESULTADO_ISAP_DETALHE (
                            isr_id, imo_id, ver_id, apr_id, isd_categoria,
                            isd_nome_feicao, isd_tipo_feicao, isd_codigo_feicao,
                            isd_area_ha, isd_pct_sobreposicao
                        ) VALUES (
                            v_isr_id, p_imo_id, p_ver_id, feat.apr_id, v_cat,
                            feat.apr_nome, feat.tipo_feicao, feat.apr_codigo,
                            v_feat_area_ha, v_feat_pct
                        );
                    END IF;
                END LOOP;
            ELSE
                gravar_resumo(
                    p_imo_id          => p_imo_id,
                    p_ver_id          => p_ver_id,
                    p_camada_ver_id   => v_cat_ver_id,
                    p_exe_id          => p_exe_id,
                    p_rca_id          => p_rca_id,
                    p_categoria       => v_cat,
                    p_sobrepoe        => 'N',
                    p_area_sobrep_ha  => 0,
                    p_pct_sobrep      => 0,
                    p_area_imovel_ha  => ROUND(v_imo_area_ha, 4),
                    p_filtro_aplicado => v_filtro_cat,
                    p_competencia     => v_cat_competencia,
                    p_status          => 'SEM_SOBREPOSICAO',
                    p_isr_id          => v_isr_id
                );
            END IF;
        END LOOP;

        v_total_competencia := obter_competencia_versao(v_total_ver_id);

        IF v_cats_disponiveis = 0 THEN
            v_status_total   := 'CAMADA_INDISPONIVEL';
            v_sobrepoe_total := NULL;
            v_area_total_ha  := NULL;
            v_pct_total      := NULL;
        ELSIF v_cats_sobrepostas = 0 THEN
            v_status_total   := 'SEM_SOBREPOSICAO';
            v_sobrepoe_total := 'N';
            v_area_total_ha  := 0;
            v_pct_total      := 0;
        ELSIF v_cats_sobrepostas = 1 THEN
            v_status_total   := 'COM_SOBREPOSICAO';
            v_sobrepoe_total := 'S';
            v_area_total_ha  := v_ultima_area_ha;
            v_pct_total      := LEAST(ROUND((v_area_total_ha / v_imo_area_ha) * 100, 4), 100);
        ELSE
            IF PKG_GEO.valida(v_total_geom) <> 'TRUE' THEN
                v_total_geom := PKG_GEO.saneada(v_total_geom);
            END IF;
            v_area_total_ha := LEAST(NVL(PKG_GEO.area_ha(v_total_geom), 0), v_imo_area_ha);
            v_area_total_ha := ROUND(v_area_total_ha, 4);

            IF v_area_total_ha > 0 THEN
                v_status_total   := 'COM_SOBREPOSICAO';
                v_sobrepoe_total := 'S';
                v_pct_total      := LEAST(ROUND((v_area_total_ha / v_imo_area_ha) * 100, 4), 100);
            ELSE
                v_status_total   := 'SEM_SOBREPOSICAO';
                v_sobrepoe_total := 'N';
                v_area_total_ha  := 0;
                v_pct_total      := 0;
            END IF;
        END IF;

        gravar_resumo(
            p_imo_id          => p_imo_id,
            p_ver_id          => p_ver_id,
            p_camada_ver_id   => v_total_ver_id,
            p_exe_id          => p_exe_id,
            p_rca_id          => p_rca_id,
            p_categoria       => 'TOTAL',
            p_sobrepoe        => v_sobrepoe_total,
            p_area_sobrep_ha  => v_area_total_ha,
            p_pct_sobrep      => v_pct_total,
            p_area_imovel_ha  => ROUND(v_imo_area_ha, 4),
            p_filtro_aplicado => v_filtro_uc,
            p_competencia     => v_total_competencia,
            p_status          => v_status_total,
            p_isr_id          => v_isr_id
        );
    END calcular_imovel;

    PROCEDURE calcular_lote(
        p_ver_id        IN  NUMBER,
        p_camada_ver_id IN  NUMBER DEFAULT NULL,
        p_exe_id        IN  NUMBER DEFAULT NULL,
        p_rca_id        IN  NUMBER DEFAULT NULL,
        p_escopo        IN  VARCHAR2 DEFAULT 'TODOS',
        p_calculados    OUT NUMBER
    ) IS
        TYPE t_categorias IS VARRAY(4) OF VARCHAR2(20);
        v_categorias  t_categorias := t_categorias('UC', 'TERRA_INDIGENA', 'QUILOMBOLA', 'FLORESTA_PUBLICA');
        v_filtro_uc   VARCHAR2(500) := obter_filtro_uc(p_rca_id);
        v_ver_cat     NUMBER;
        v_dissolvidas NUMBER;
        v_count       NUMBER := 0;
    BEGIN
        IF validar_versao_existente(p_camada_ver_id) IS NOT NULL THEN
            dissolver_areas_protegidas(p_camada_ver_id, v_filtro_uc, v_dissolvidas);
        ELSIF p_camada_ver_id IS NULL THEN
            FOR idx IN 1 .. v_categorias.COUNT LOOP
                v_ver_cat := resolver_versao_categoria(v_categorias(idx));
                IF v_ver_cat IS NOT NULL THEN
                    dissolver_areas_protegidas(v_ver_cat, v_filtro_uc, v_dissolvidas);
                END IF;
            END LOOP;
        END IF;

        FOR r IN (
            SELECT i.imo_id
              FROM IMOVEL_RURAL i
             WHERE i.imo_geometria IS NOT NULL
               AND (UPPER(NVL(p_escopo, 'TODOS')) = 'TODOS' OR i.ver_id = p_ver_id)
             ORDER BY i.imo_id
        ) LOOP
            calcular_imovel(
                p_imo_id        => r.imo_id,
                p_ver_id        => p_ver_id,
                p_camada_ver_id => p_camada_ver_id,
                p_exe_id        => p_exe_id,
                p_rca_id        => p_rca_id,
                p_filtro_uc     => v_filtro_uc
            );
            v_count := v_count + 1;
        END LOOP;

        p_calculados := v_count;
    END calcular_lote;

    PROCEDURE recalcular_por_parametro(
        p_rca_id     IN  NUMBER,
        p_calculados OUT NUMBER
    ) IS
        v_ver_imovel NUMBER;
    BEGIN
        SELECT MAX(v.ver_id)
          INTO v_ver_imovel
          FROM VERSAO v
         WHERE v.ver_situacao = 'VIGENTE'
           AND EXISTS (SELECT 1 FROM IMOVEL_RURAL i WHERE i.ver_id = v.ver_id);

        IF v_ver_imovel IS NULL THEN
            SELECT MAX(ver_id) INTO v_ver_imovel FROM IMOVEL_RURAL;
        END IF;

        IF v_ver_imovel IS NULL THEN
            p_calculados := 0;
            RETURN;
        END IF;

        calcular_lote(
            p_ver_id        => v_ver_imovel,
            p_camada_ver_id => NULL,
            p_exe_id        => NULL,
            p_rca_id        => p_rca_id,
            p_escopo        => 'TODOS',
            p_calculados    => p_calculados
        );
    END recalcular_por_parametro;

    PROCEDURE publicar(
        p_ver_id     IN  NUMBER,
        p_publicados OUT NUMBER
    ) IS
    BEGIN
        IF p_ver_id IS NULL THEN
            p_publicados := 0;
            RETURN;
        END IF;

        -- Desativa vigencia anterior apenas dos imoveis presentes nesta rodada, preservando historico
        UPDATE RESULTADO_ISAP_RESUMO r
           SET r.isr_vigente = 'N'
         WHERE r.isr_vigente = 'S'
           AND r.ver_id <> p_ver_id
           AND r.imo_id IN (
               SELECT DISTINCT imo_id
                 FROM RESULTADO_ISAP_RESUMO
                WHERE ver_id = p_ver_id
           );

        UPDATE RESULTADO_ISAP_RESUMO r
           SET r.isr_vigente = 'S'
         WHERE r.ver_id = p_ver_id;

        p_publicados := SQL%ROWCOUNT;
    END publicar;

END PKG_ISAP;
/