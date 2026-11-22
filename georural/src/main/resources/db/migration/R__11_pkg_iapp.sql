-- =====================================================================
-- GeoRural DataHub - PKG_IAPP (US#23.05, US#23.06, US#23.07 e US#23.08)
-- Preparacao espacial, calculo do IAPP, rastreabilidade e publicacao
-- =====================================================================

CREATE OR REPLACE PACKAGE PKG_IAPP AS
    c_srid_oficial CONSTANT NUMBER := 4674;

    FUNCTION normalizar_filtro(p_filtro VARCHAR2) RETURN VARCHAR2;
    FUNCTION item_permitido(p_valor VARCHAR2, p_filtro VARCHAR2) RETURN NUMBER;

    -- Obtem o valor de um parametro da regra IAPP em PARAMETRO_REGRA_CALCULO
    FUNCTION obter_parametro(p_nome_param VARCHAR2, p_rca_id NUMBER DEFAULT NULL, p_default VARCHAR2 DEFAULT '*') RETURN VARCHAR2;

    -- Resolve a versao vigente de APP ou VEGETACAO_NATIVA quando nao informada explicitamente
    FUNCTION resolver_versao_camada(p_tipo_camada VARCHAR2) RETURN NUMBER;

    -- Garante SRID 4674 (reprojeta via SDO_CS.TRANSFORM quando necessario)
    FUNCTION normalizar_srid(p_geom SDO_GEOMETRY) RETURN SDO_GEOMETRY;

    -- Valida, tenta corrigir e, se irrecuperavel, registra na QUARENTENA sem abortar o lote
    FUNCTION preparar_geometria(
        p_geom          IN SDO_GEOMETRY,
        p_arq_bruto_id  IN NUMBER,
        p_identificador IN VARCHAR2,
        p_coluna        IN VARCHAR2
    ) RETURN SDO_GEOMETRY;

    -- Dissolve APP e VEGETACAO_NATIVA por versao e filtro (SDO_AGGR_UNION) evitando dupla contagem (US#23.05)
    PROCEDURE dissolver_camadas(
        p_app_ver_id          IN  NUMBER,
        p_veg_ver_id          IN  NUMBER,
        p_filtro_tipos_app    IN  VARCHAR2 DEFAULT '*',
        p_filtro_classes_veg  IN  VARCHAR2 DEFAULT '*',
        p_app_feicoes_validas OUT NUMBER,
        p_veg_feicoes_validas OUT NUMBER
    );

    -- Calcula o cruzamento APP x VEGETACAO_NATIVA dentro do perimetro de um imovel (US#23.06 / US#23.07 / US#23.08)
    PROCEDURE calcular_imovel(
        p_imo_id             IN NUMBER,
        p_ver_id             IN NUMBER,
        p_app_ver_id         IN NUMBER DEFAULT NULL,
        p_veg_ver_id         IN NUMBER DEFAULT NULL,
        p_exe_id             IN NUMBER DEFAULT NULL,
        p_rca_id             IN NUMBER DEFAULT NULL,
        p_filtro_tipos_app   IN VARCHAR2 DEFAULT NULL,
        p_filtro_classes_veg IN VARCHAR2 DEFAULT NULL
    );

    -- Calcula o IAPP em lote para os imoveis no escopo (US#23.06 / US#23.07 / US#23.08)
    PROCEDURE calcular_lote(
        p_ver_id        IN  NUMBER,
        p_app_ver_id    IN  NUMBER DEFAULT NULL,
        p_veg_ver_id    IN  NUMBER DEFAULT NULL,
        p_exe_id        IN  NUMBER DEFAULT NULL,
        p_rca_id        IN  NUMBER DEFAULT NULL,
        p_escopo        IN  VARCHAR2 DEFAULT 'TODOS',
        p_calculados    OUT NUMBER
    );

    -- Recalcula o IAPP de todos os imoveis apos alteracao de parametro da regra (US#23.08)
    PROCEDURE recalcular_por_parametro(
        p_rca_id     IN  NUMBER,
        p_calculados OUT NUMBER
    );

    -- Promove os resultados do IAPP da versao publicada sem apagar o historico anterior (US#23.08)
    PROCEDURE publicar(
        p_ver_id     IN  NUMBER,
        p_publicados OUT NUMBER
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

    FUNCTION obter_parametro(p_nome_param VARCHAR2, p_rca_id NUMBER DEFAULT NULL, p_default VARCHAR2 DEFAULT '*') RETURN VARCHAR2 IS
        v_valor VARCHAR2(500);
    BEGIN
        IF p_rca_id IS NOT NULL THEN
            SELECT prc_valor INTO v_valor
              FROM PARAMETRO_REGRA_CALCULO
             WHERE rca_id = p_rca_id
               AND prc_nome = p_nome_param;
            RETURN NVL(TRIM(v_valor), p_default);
        END IF;

        SELECT p.prc_valor INTO v_valor
          FROM PARAMETRO_REGRA_CALCULO p
          JOIN REGRA_CALCULO r ON r.rca_id = p.rca_id
          JOIN INDICADOR i ON i.ind_id = r.ind_id
         WHERE i.ind_sigla = 'IAPP'
           AND r.rca_vigente = 'S'
           AND p.prc_nome = p_nome_param
         FETCH FIRST 1 ROWS ONLY;

        RETURN NVL(TRIM(v_valor), p_default);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN p_default;
    END obter_parametro;

    FUNCTION obter_regra_vigente_id RETURN NUMBER IS
        v_rca_id NUMBER;
    BEGIN
        SELECT r.rca_id INTO v_rca_id
          FROM REGRA_CALCULO r
          JOIN INDICADOR i ON i.ind_id = r.ind_id
         WHERE i.ind_sigla = 'IAPP'
           AND r.rca_vigente = 'S'
         FETCH FIRST 1 ROWS ONLY;
        RETURN v_rca_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RETURN NULL;
    END obter_regra_vigente_id;

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

    FUNCTION resolver_versao_camada(p_tipo_camada VARCHAR2) RETURN NUMBER IS
        v_ver_id NUMBER;
    BEGIN
        IF UPPER(p_tipo_camada) = 'APP' THEN
            SELECT MAX(v.ver_id) INTO v_ver_id
              FROM VERSAO v
              JOIN CONJUNTO_DADOS c ON c.con_id = v.con_id
              JOIN TIPO_CONJUNTO t ON t.tip_id = c.tip_id
             WHERE t.tip_codigo = 'APP'
               AND v.ver_situacao = 'VIGENTE'
               AND EXISTS (SELECT 1 FROM APP a WHERE a.ver_id = v.ver_id);

            IF v_ver_id IS NULL THEN
                SELECT MAX(ver_id) INTO v_ver_id FROM APP;
            END IF;
            RETURN v_ver_id;
        ELSIF UPPER(p_tipo_camada) = 'VEGETACAO' THEN
            SELECT MAX(v.ver_id) INTO v_ver_id
              FROM VERSAO v
              JOIN CONJUNTO_DADOS c ON c.con_id = v.con_id
              JOIN TIPO_CONJUNTO t ON t.tip_id = c.tip_id
             WHERE t.tip_codigo = 'VEGETACAO'
               AND v.ver_situacao = 'VIGENTE'
               AND EXISTS (SELECT 1 FROM VEGETACAO_NATIVA vn WHERE vn.ver_id = v.ver_id);

            IF v_ver_id IS NULL THEN
                SELECT MAX(ver_id) INTO v_ver_id FROM VEGETACAO_NATIVA;
            END IF;
            RETURN v_ver_id;
        END IF;

        RETURN NULL;
    END resolver_versao_camada;

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

    PROCEDURE gravar_resultado(
        p_imo_id            IN NUMBER,
        p_ver_id            IN NUMBER,
        p_app_ver_id        IN NUMBER,
        p_veg_ver_id        IN NUMBER,
        p_exe_id            IN NUMBER,
        p_rca_id            IN NUMBER,
        p_app_total_ha      IN NUMBER,
        p_app_vegetada_ha   IN NUMBER,
        p_passivo_ha        IN NUMBER,
        p_iapp_pct          IN NUMBER,
        p_area_imovel_ha    IN NUMBER,
        p_filtro_app        IN VARCHAR2,
        p_filtro_veg        IN VARCHAR2,
        p_comp_app          IN VARCHAR2,
        p_comp_veg          IN VARCHAR2,
        p_status            IN VARCHAR2,
        p_mensagem          IN VARCHAR2,
        p_app_veg_geom      IN SDO_GEOMETRY,
        p_passivo_geom      IN SDO_GEOMETRY
    ) IS
    BEGIN
        MERGE INTO RESULTADO_IAPP dst
        USING (SELECT p_imo_id AS imo_id, p_ver_id AS ver_id FROM DUAL) src
           ON (dst.imo_id = src.imo_id AND dst.ver_id = src.ver_id)
         WHEN MATCHED THEN
              UPDATE SET dst.iap_app_ver_id         = p_app_ver_id,
                         dst.iap_veg_ver_id         = p_veg_ver_id,
                         dst.exe_id                 = p_exe_id,
                         dst.rca_id                 = p_rca_id,
                         dst.iap_app_total_ha       = p_app_total_ha,
                         dst.iap_app_vegetada_ha    = p_app_vegetada_ha,
                         dst.iap_passivo_ha         = p_passivo_ha,
                         dst.iap_iapp_pct           = p_iapp_pct,
                         dst.iap_area_imovel_ha     = p_area_imovel_ha,
                         dst.iap_filtro_tipos_app   = p_filtro_app,
                         dst.iap_filtro_classes_veg = p_filtro_veg,
                         dst.iap_competencia_app    = p_comp_app,
                         dst.iap_competencia_veg    = p_comp_veg,
                         dst.iap_status             = p_status,
                         dst.iap_mensagem           = p_mensagem,
                         dst.iap_app_vegetada_geom  = p_app_veg_geom,
                         dst.iap_passivo_geom       = p_passivo_geom,
                         dst.iap_data_calculo       = SYSTIMESTAMP
         WHEN NOT MATCHED THEN
              INSERT (imo_id, ver_id, iap_app_ver_id, iap_veg_ver_id, exe_id, rca_id,
                      iap_app_total_ha, iap_app_vegetada_ha, iap_passivo_ha, iap_iapp_pct,
                      iap_area_imovel_ha, iap_filtro_tipos_app, iap_filtro_classes_veg,
                      iap_competencia_app, iap_competencia_veg, iap_status, iap_mensagem,
                      iap_app_vegetada_geom, iap_passivo_geom, iap_vigente, iap_data_calculo)
              VALUES (p_imo_id, p_ver_id, p_app_ver_id, p_veg_ver_id, p_exe_id, p_rca_id,
                      p_app_total_ha, p_app_vegetada_ha, p_passivo_ha, p_iapp_pct,
                      p_area_imovel_ha, p_filtro_app, p_filtro_veg,
                      p_comp_app, p_comp_veg, p_status, p_mensagem,
                      p_app_veg_geom, p_passivo_geom, 'N', SYSTIMESTAMP);
    END gravar_resultado;

    PROCEDURE calcular_imovel(
        p_imo_id             IN NUMBER,
        p_ver_id             IN NUMBER,
        p_app_ver_id         IN NUMBER DEFAULT NULL,
        p_veg_ver_id         IN NUMBER DEFAULT NULL,
        p_exe_id             IN NUMBER DEFAULT NULL,
        p_rca_id             IN NUMBER DEFAULT NULL,
        p_filtro_tipos_app   IN VARCHAR2 DEFAULT NULL,
        p_filtro_classes_veg IN VARCHAR2 DEFAULT NULL
    ) IS
        v_tol               NUMBER := PKG_GEO.c_tol;
        v_imo_geom          SDO_GEOMETRY;
        v_arq_bruto_id      NUMBER;
        v_imo_codigo_car    VARCHAR2(100);
        v_imo_area_ha       NUMBER;
        v_rca_id            NUMBER;
        v_app_ver_id        NUMBER;
        v_veg_ver_id        NUMBER;
        v_filtro_app        VARCHAR2(500);
        v_filtro_veg        VARCHAR2(500);
        v_comp_app          VARCHAR2(20);
        v_comp_veg          VARCHAR2(20);
        v_apd_geom          SDO_GEOMETRY;
        v_ved_geom          SDO_GEOMETRY;
        v_app_imovel_geom   SDO_GEOMETRY;
        v_app_veg_geom      SDO_GEOMETRY;
        v_passivo_geom      SDO_GEOMETRY;
        v_app_total_ha      NUMBER;
        v_app_vegetada_ha   NUMBER;
        v_passivo_ha        NUMBER;
        v_iapp_pct          NUMBER;
        v_mensagem          VARCHAR2(500);
    BEGIN
        SELECT imo_geometria, arq_bruto_id, imo_codigo_car,
               NVL(imo_area_total, PKG_GEO.area_ha(imo_geometria))
          INTO v_imo_geom, v_arq_bruto_id, v_imo_codigo_car, v_imo_area_ha
          FROM IMOVEL_RURAL
         WHERE imo_id = p_imo_id;

        v_imo_geom := preparar_geometria(
            v_imo_geom, v_arq_bruto_id, 'IMOVEL#' || NVL(v_imo_codigo_car, TO_CHAR(p_imo_id)), 'IMO_GEOMETRIA'
        );

        IF v_imo_geom IS NULL OR NVL(v_imo_area_ha, 0) <= 0 THEN
            RETURN;
        END IF;

        v_rca_id     := NVL(p_rca_id, obter_regra_vigente_id());
        v_app_ver_id := validar_versao_existente(NVL(p_app_ver_id, resolver_versao_camada('APP')));
        v_veg_ver_id := validar_versao_existente(NVL(p_veg_ver_id, resolver_versao_camada('VEGETACAO')));
        v_filtro_app := normalizar_filtro(NVL(p_filtro_tipos_app, obter_parametro('tipos_app_incluidos', v_rca_id, '*')));
        v_filtro_veg := normalizar_filtro(NVL(p_filtro_classes_veg, obter_parametro('classes_vegetacao_incluidas', v_rca_id, '*')));
        v_comp_app   := obter_competencia_versao(v_app_ver_id);
        v_comp_veg   := obter_competencia_versao(v_veg_ver_id);

        IF v_app_ver_id IS NULL OR v_veg_ver_id IS NULL THEN
            v_mensagem := CASE
                WHEN v_app_ver_id IS NULL AND v_veg_ver_id IS NULL THEN
                    'Camadas de APP e Vegetacao Nativa indisponiveis/nao publicadas para o calculo.'
                WHEN v_app_ver_id IS NULL THEN
                    'Camada de APP indisponivel/nao publicada para o calculo.'
                ELSE
                    'Camada de Vegetacao Nativa indisponivel/nao publicada para o calculo.'
            END;

            gravar_resultado(
                p_imo_id          => p_imo_id,
                p_ver_id          => p_ver_id,
                p_app_ver_id      => v_app_ver_id,
                p_veg_ver_id      => v_veg_ver_id,
                p_exe_id          => p_exe_id,
                p_rca_id          => v_rca_id,
                p_app_total_ha    => NULL,
                p_app_vegetada_ha => NULL,
                p_passivo_ha      => NULL,
                p_iapp_pct        => NULL,
                p_area_imovel_ha  => ROUND(v_imo_area_ha, 4),
                p_filtro_app      => v_filtro_app,
                p_filtro_veg      => v_filtro_veg,
                p_comp_app        => v_comp_app,
                p_comp_veg        => v_comp_veg,
                p_status          => 'CAMADA_INDISPONIVEL',
                p_mensagem        => v_mensagem,
                p_app_veg_geom    => NULL,
                p_passivo_geom    => NULL
            );
            RETURN;
        END IF;

        BEGIN
            SELECT apd_geometria INTO v_apd_geom
              FROM APP_DISSOLVIDA
             WHERE ver_id = v_app_ver_id
               AND apd_filtro_aplicado = v_filtro_app;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                v_apd_geom := NULL;
        END;

        BEGIN
            SELECT ved_geometria INTO v_ved_geom
              FROM VEGETACAO_DISSOLVIDA
             WHERE ver_id = v_veg_ver_id
               AND ved_filtro_aplicado = v_filtro_veg;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                v_ved_geom := NULL;
        END;

        v_app_total_ha := 0;
        IF v_apd_geom IS NOT NULL AND SDO_GEOM.RELATE(v_imo_geom, 'ANYINTERACT', v_apd_geom, v_tol) = 'TRUE' THEN
            v_app_imovel_geom := PKG_GEO.intersecao(v_imo_geom, v_apd_geom);
            IF v_app_imovel_geom IS NOT NULL THEN
                v_app_total_ha := LEAST(NVL(PKG_GEO.area_ha(v_app_imovel_geom), 0), v_imo_area_ha);
            END IF;
        END IF;
        v_app_total_ha := ROUND(NVL(v_app_total_ha, 0), 4);

        IF v_app_total_ha <= 0 THEN
            gravar_resultado(
                p_imo_id          => p_imo_id,
                p_ver_id          => p_ver_id,
                p_app_ver_id      => v_app_ver_id,
                p_veg_ver_id      => v_veg_ver_id,
                p_exe_id          => p_exe_id,
                p_rca_id          => v_rca_id,
                p_app_total_ha    => NULL,
                p_app_vegetada_ha => NULL,
                p_passivo_ha      => NULL,
                p_iapp_pct        => NULL,
                p_area_imovel_ha  => ROUND(v_imo_area_ha, 4),
                p_filtro_app      => v_filtro_app,
                p_filtro_veg      => v_filtro_veg,
                p_comp_app        => v_comp_app,
                p_comp_veg        => v_comp_veg,
                p_status          => 'SEM_APP',
                p_mensagem        => 'O imovel nao possui Area de Preservacao Permanente (APP) incidente na versao avaliada.',
                p_app_veg_geom    => NULL,
                p_passivo_geom    => NULL
            );
            RETURN;
        END IF;

        v_app_vegetada_ha := 0;
        v_app_veg_geom    := NULL;
        IF v_ved_geom IS NOT NULL AND SDO_GEOM.RELATE(v_app_imovel_geom, 'ANYINTERACT', v_ved_geom, v_tol) = 'TRUE' THEN
            v_app_veg_geom := PKG_GEO.intersecao(v_app_imovel_geom, v_ved_geom);
            IF v_app_veg_geom IS NOT NULL THEN
                v_app_vegetada_ha := LEAST(NVL(PKG_GEO.area_ha(v_app_veg_geom), 0), v_app_total_ha);
            END IF;
        END IF;

        v_app_vegetada_ha := ROUND(NVL(v_app_vegetada_ha, 0), 4);
        v_passivo_ha      := ROUND(GREATEST(v_app_total_ha - v_app_vegetada_ha, 0), 4);
        v_iapp_pct        := LEAST(ROUND((v_app_vegetada_ha / NULLIF(v_app_total_ha, 0)) * 100, 4), 100);

        IF v_passivo_ha <= 0 THEN
            v_passivo_geom := NULL;
            v_mensagem     := 'APP integralmente coberta por vegetacao nativa (passivo ambiental zero).';
        ELSIF v_app_vegetada_ha <= 0 THEN
            v_passivo_geom := v_app_imovel_geom;
            v_mensagem     := 'APP sem cobertura de vegetacao nativa (passivo integral de '
                              || TO_CHAR(v_passivo_ha, 'FM999999990D0000') || ' ha).';
        ELSE
            BEGIN
                v_passivo_geom := SDO_GEOM.SDO_DIFFERENCE(v_app_imovel_geom, v_app_veg_geom, v_tol);
            EXCEPTION
                WHEN OTHERS THEN
                    v_passivo_geom := NULL;
            END;
            v_mensagem := 'Calculo concluido com sucesso. Passivo ambiental de '
                          || TO_CHAR(v_passivo_ha, 'FM999999990D0000') || ' ha identificado em APP.';
        END IF;

        gravar_resultado(
            p_imo_id          => p_imo_id,
            p_ver_id          => p_ver_id,
            p_app_ver_id      => v_app_ver_id,
            p_veg_ver_id      => v_veg_ver_id,
            p_exe_id          => p_exe_id,
            p_rca_id          => v_rca_id,
            p_app_total_ha    => v_app_total_ha,
            p_app_vegetada_ha => v_app_vegetada_ha,
            p_passivo_ha      => v_passivo_ha,
            p_iapp_pct        => v_iapp_pct,
            p_area_imovel_ha  => ROUND(v_imo_area_ha, 4),
            p_filtro_app      => v_filtro_app,
            p_filtro_veg      => v_filtro_veg,
            p_comp_app        => v_comp_app,
            p_comp_veg        => v_comp_veg,
            p_status          => 'CALCULADO',
            p_mensagem        => v_mensagem,
            p_app_veg_geom    => v_app_veg_geom,
            p_passivo_geom    => v_passivo_geom
        );
    END calcular_imovel;

    PROCEDURE calcular_lote(
        p_ver_id        IN  NUMBER,
        p_app_ver_id    IN  NUMBER DEFAULT NULL,
        p_veg_ver_id    IN  NUMBER DEFAULT NULL,
        p_exe_id        IN  NUMBER DEFAULT NULL,
        p_rca_id        IN  NUMBER DEFAULT NULL,
        p_escopo        IN  VARCHAR2 DEFAULT 'TODOS',
        p_calculados    OUT NUMBER
    ) IS
        v_rca_id     NUMBER := NVL(p_rca_id, obter_regra_vigente_id());
        v_app_ver_id NUMBER := validar_versao_existente(NVL(p_app_ver_id, resolver_versao_camada('APP')));
        v_veg_ver_id NUMBER := validar_versao_existente(NVL(p_veg_ver_id, resolver_versao_camada('VEGETACAO')));
        v_filtro_app VARCHAR2(500) := normalizar_filtro(obter_parametro('tipos_app_incluidos', v_rca_id, '*'));
        v_filtro_veg VARCHAR2(500) := normalizar_filtro(obter_parametro('classes_vegetacao_incluidas', v_rca_id, '*'));
        v_q_app      NUMBER;
        v_q_veg      NUMBER;
        v_count      NUMBER := 0;
    BEGIN
        IF v_app_ver_id IS NOT NULL AND v_veg_ver_id IS NOT NULL THEN
            dissolver_camadas(v_app_ver_id, v_veg_ver_id, v_filtro_app, v_filtro_veg, v_q_app, v_q_veg);
        END IF;

        FOR r IN (
            SELECT i.imo_id
              FROM IMOVEL_RURAL i
             WHERE i.imo_geometria IS NOT NULL
               AND (UPPER(NVL(p_escopo, 'TODOS')) = 'TODOS' OR i.ver_id = p_ver_id)
             ORDER BY i.imo_id
        ) LOOP
            calcular_imovel(
                p_imo_id             => r.imo_id,
                p_ver_id             => p_ver_id,
                p_app_ver_id         => v_app_ver_id,
                p_veg_ver_id         => v_veg_ver_id,
                p_exe_id             => p_exe_id,
                p_rca_id             => v_rca_id,
                p_filtro_tipos_app   => v_filtro_app,
                p_filtro_classes_veg => v_filtro_veg
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
            p_app_ver_id    => NULL,
            p_veg_ver_id    => NULL,
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
        v_con_id NUMBER;
        v_qtd    NUMBER := 0;
    BEGIN
        SELECT COUNT(*) INTO v_qtd
          FROM RESULTADO_IAPP
         WHERE ver_id = p_ver_id;

        IF v_qtd = 0 THEN
            p_publicados := 0;
            RETURN;
        END IF;

        SELECT con_id INTO v_con_id
          FROM VERSAO
         WHERE ver_id = p_ver_id;

        -- Desativa vigencia apenas de versoes anteriores do mesmo conjunto, preservando historico
        UPDATE RESULTADO_IAPP r
           SET r.iap_vigente = 'N'
         WHERE r.iap_vigente = 'S'
           AND r.ver_id IN (
               SELECT ver_id
                 FROM VERSAO
                WHERE con_id = v_con_id
                  AND ver_id <> p_ver_id
           );

        UPDATE RESULTADO_IAPP r
           SET r.iap_vigente = 'S'
         WHERE r.ver_id = p_ver_id;

        p_publicados := SQL%ROWCOUNT;
    END publicar;

END PKG_IAPP;
/