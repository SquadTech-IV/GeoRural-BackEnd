-- =====================================================================
-- GeoRural DataHub - PKG_CALCULO
-- =====================================================================

CREATE OR REPLACE PACKAGE PKG_CALCULO AS

    -- imoveis alcancados por esta rodada; escopo = IMOVEL, RESERVA_LEGAL ou EMBARGO
    FUNCTION imoveis_no_escopo(p_ver_id NUMBER, p_escopo VARCHAR2) RETURN SYS.ODCINUMBERLIST;

    PROCEDURE calcular_irl(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                           p_minimo_pct NUMBER, p_escopo VARCHAR2, p_calculados OUT NUMBER);

    PROCEDURE calcular_iae(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                           p_escopo VARCHAR2, p_calculados OUT NUMBER);

    PROCEDURE calcular_icv(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                           p_minimo_pct NUMBER, p_escopo VARCHAR2, p_calculados OUT NUMBER);

    PROCEDURE agregar_municipio(p_ver_id NUMBER, p_sigla VARCHAR2, p_agregados OUT NUMBER);

    -- liga a versao: resultados viram vigentes e a anterior deixa de ser
    PROCEDURE publicar(p_ver_id NUMBER, p_publicados OUT NUMBER);

END PKG_CALCULO;
/

CREATE OR REPLACE PACKAGE BODY PKG_CALCULO AS

    FUNCTION id_indicador(p_sigla VARCHAR2) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        SELECT ind_id INTO v_id FROM INDICADOR WHERE ind_sigla = p_sigla;
        RETURN v_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            RAISE_APPLICATION_ERROR(-20040, 'Indicador ' || p_sigla || ' nao cadastrado.');
    END id_indicador;

    FUNCTION imoveis_no_escopo(p_ver_id NUMBER, p_escopo VARCHAR2) RETURN SYS.ODCINUMBERLIST IS
        v_ids SYS.ODCINUMBERLIST;
    BEGIN
        IF p_escopo = 'IMOVEL' THEN
            SELECT i.imo_id BULK COLLECT INTO v_ids
              FROM IMOVEL_RURAL i
             WHERE i.ver_id = p_ver_id;

        ELSIF p_escopo = 'RESERVA_LEGAL' THEN
            SELECT DISTINCT r.imo_id BULK COLLECT INTO v_ids
              FROM RESERVA_LEGAL r
             WHERE r.ver_id = p_ver_id;

        ELSIF p_escopo = 'EMBARGO' THEN
            SELECT DISTINCT i.imo_id BULK COLLECT INTO v_ids
              FROM IMOVEL_RURAL i
             WHERE i.imo_geometria IS NOT NULL
               AND EXISTS (SELECT 1
                             FROM EMBARGO b
                            WHERE b.ver_id = p_ver_id
                              AND b.emb_geometria IS NOT NULL
                              AND SDO_RELATE(b.emb_geometria, i.imo_geometria,
                                             'mask=ANYINTERACT') = 'TRUE');
        ELSIF p_escopo = 'VEGETACAO' THEN
            -- imoveis tocados pela vegetacao que ENTROU nesta versao; o indice espacial
            -- esta em VEGETACAO_NATIVA, por isso ela e o primeiro argumento do SDO_RELATE
            SELECT DISTINCT i.imo_id BULK COLLECT INTO v_ids
              FROM IMOVEL_RURAL i
             WHERE i.imo_geometria IS NOT NULL
               AND EXISTS (SELECT 1
                             FROM VEGETACAO_NATIVA v
                            WHERE v.ver_id = p_ver_id
                              AND v.veg_geometria IS NOT NULL
                              AND SDO_RELATE(v.veg_geometria, i.imo_geometria,
                                             'mask=ANYINTERACT') = 'TRUE');
        ELSE
            RAISE_APPLICATION_ERROR(-20041,
                'Escopo de calculo desconhecido: ' || p_escopo
                || '. Esperado IMOVEL, RESERVA_LEGAL, VEGETACAO ou EMBARGO.');
        END IF;

        RETURN NVL(v_ids, SYS.ODCINUMBERLIST());
    END imoveis_no_escopo;

    PROCEDURE calcular_irl(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                           p_minimo_pct NUMBER, p_escopo VARCHAR2, p_calculados OUT NUMBER) IS
        v_ids SYS.ODCINUMBERLIST := imoveis_no_escopo(p_ver_id, p_escopo);
        v_ind NUMBER := id_indicador('IRL');
    BEGIN
        IF v_ids.COUNT = 0 THEN
            p_calculados := 0;
            RETURN;
        END IF;

        MERGE INTO RESULTADO_INDICADOR d
        USING (SELECT o.imo_id,
                      v_ind AS ind_id,
                      o.pct,
                      o.area_rl,
                      o.area_rl - o.minimo_ha AS saldo_ha,
                      CASE WHEN o.pct >= p_minimo_pct THEN 'CONFORME' ELSE 'DEFICIT' END AS situacao,
                      '{"area_imovel_ha":' || TO_CHAR(ROUND(o.area_imovel, 4))
                        || ',"area_rl_ha":' || TO_CHAR(ROUND(o.area_rl, 4))
                        || ',"minimo_pct":' || TO_CHAR(p_minimo_pct)
                        || ',"minimo_ha":' || TO_CHAR(ROUND(o.minimo_ha, 4))
                        || ',"saldo_ha":' || TO_CHAR(ROUND(o.area_rl - o.minimo_ha, 4))
                        || ',"reservas_consideradas":' || TO_CHAR(o.reservas) || '}' AS detalhes
                 FROM (SELECT i.imo_id,
                              NVL(i.imo_area_total, 0) AS area_imovel,
                              NVL(i.imo_area_total, 0) * p_minimo_pct / 100 AS minimo_ha,
                              NVL((SELECT SUM(r.rle_area_ha) FROM RESERVA_LEGAL r
                                    WHERE r.imo_id = i.imo_id), 0) AS area_rl,
                              NVL((SELECT COUNT(*) FROM RESERVA_LEGAL r
                                    WHERE r.imo_id = i.imo_id), 0) AS reservas,
                              NVL((SELECT SUM(r.rle_area_ha) FROM RESERVA_LEGAL r
                                    WHERE r.imo_id = i.imo_id), 0)
                                / NULLIF(i.imo_area_total, 0) * 100 AS pct
                         FROM IMOVEL_RURAL i
                        WHERE i.imo_id IN (SELECT column_value FROM TABLE(v_ids))) o) f
           ON (d.imo_id = f.imo_id AND d.ind_id = f.ind_id AND d.ver_id = p_ver_id)
         WHEN MATCHED THEN
              UPDATE SET d.rin_valor_percentual = f.pct,
                         d.rin_valor_hectares   = f.area_rl,
                         d.rin_valor_absoluto   = f.saldo_ha,
                         d.rin_situacao         = f.situacao,
                         d.rin_detalhes         = f.detalhes,
                         d.rin_data_calculo     = SYSTIMESTAMP,
                         d.exe_id               = p_exe_id,
                         d.rca_id               = p_rca_id
         WHEN NOT MATCHED THEN
              INSERT (imo_id, ind_id, ver_id, exe_id, rca_id, rin_valor_percentual,
                      rin_valor_hectares, rin_valor_absoluto, rin_situacao, rin_detalhes,
                      rin_data_calculo, rin_vigente)
              VALUES (f.imo_id, f.ind_id, p_ver_id, p_exe_id, p_rca_id, f.pct,
                      f.area_rl, f.saldo_ha, f.situacao, f.detalhes,
                      SYSTIMESTAMP, 'N');
        p_calculados := SQL%ROWCOUNT;
    END calcular_irl;

    PROCEDURE calcular_iae(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                           p_escopo VARCHAR2, p_calculados OUT NUMBER) IS
        v_ids SYS.ODCINUMBERLIST := imoveis_no_escopo(p_ver_id, p_escopo);
        v_ind NUMBER := id_indicador('IAE');
    BEGIN
        IF v_ids.COUNT = 0 THEN
            p_calculados := 0;
            RETURN;
        END IF;

        MERGE INTO RESULTADO_INDICADOR d
        USING (SELECT o.imo_id,
                      v_ind AS ind_id,
                      o.area_embargada / NULLIF(o.area_imovel, 0) * 100 AS pct,
                      o.area_embargada,
                      CASE WHEN o.area_embargada > 0 THEN 'COM_EMBARGO'
                           ELSE 'SEM_EMBARGO' END AS situacao,
                      '{"area_imovel_ha":' || TO_CHAR(ROUND(o.area_imovel, 4))
                        || ',"area_embargada_ha":' || TO_CHAR(ROUND(o.area_embargada, 4))
                        || ',"embargos_sobrepostos":' || TO_CHAR(o.embargos) || '}' AS detalhes
                 FROM (SELECT i.imo_id,
                              NVL(i.imo_area_total, 0) AS area_imovel,
                              NVL((SELECT SUM(PKG_GEO.area_intersecao_ha(i.imo_geometria,
                                                                         b.emb_geometria))
                                     FROM EMBARGO b
                                    WHERE b.emb_geometria IS NOT NULL
                                      AND SDO_RELATE(b.emb_geometria, i.imo_geometria,
                                                     'mask=ANYINTERACT') = 'TRUE'), 0)
                                AS area_embargada,
                              NVL((SELECT COUNT(*)
                                     FROM EMBARGO b
                                    WHERE b.emb_geometria IS NOT NULL
                                      AND SDO_RELATE(b.emb_geometria, i.imo_geometria,
                                                     'mask=ANYINTERACT') = 'TRUE'), 0) AS embargos
                         FROM IMOVEL_RURAL i
                        WHERE i.imo_id IN (SELECT column_value FROM TABLE(v_ids))) o) f
           ON (d.imo_id = f.imo_id AND d.ind_id = f.ind_id AND d.ver_id = p_ver_id)
         WHEN MATCHED THEN
              UPDATE SET d.rin_valor_percentual = f.pct,
                         d.rin_valor_hectares   = f.area_embargada,
                         d.rin_valor_absoluto   = f.area_embargada,
                         d.rin_situacao         = f.situacao,
                         d.rin_detalhes         = f.detalhes,
                         d.rin_data_calculo     = SYSTIMESTAMP,
                         d.exe_id               = p_exe_id,
                         d.rca_id               = p_rca_id
         WHEN NOT MATCHED THEN
              INSERT (imo_id, ind_id, ver_id, exe_id, rca_id, rin_valor_percentual,
                      rin_valor_hectares, rin_valor_absoluto, rin_situacao, rin_detalhes,
                      rin_data_calculo, rin_vigente)
              VALUES (f.imo_id, f.ind_id, p_ver_id, p_exe_id, p_rca_id, f.pct,
                      f.area_embargada, f.area_embargada, f.situacao, f.detalhes,
                      SYSTIMESTAMP, 'N');
        p_calculados := SQL%ROWCOUNT;
    END calcular_iae;

    PROCEDURE calcular_icv(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                           p_minimo_pct NUMBER, p_escopo VARCHAR2, p_calculados OUT NUMBER) IS
        v_ids SYS.ODCINUMBERLIST := imoveis_no_escopo(p_ver_id, p_escopo);
        v_ind NUMBER := id_indicador('ICV');
    BEGIN
        IF v_ids.COUNT = 0 THEN
            p_calculados := 0;
            RETURN;
        END IF;

        MERGE INTO RESULTADO_INDICADOR d
        USING (SELECT o.imo_id,
                      v_ind AS ind_id,
                      LEAST(o.area_vegetacao / NULLIF(o.area_imovel, 0) * 100, 100) AS pct,
                      o.area_vegetacao,
                      CASE WHEN o.area_vegetacao / NULLIF(o.area_imovel, 0) * 100 >= p_minimo_pct
                           THEN 'CONSERVADO' ELSE 'DEGRADADO' END AS situacao,
                      '{"area_imovel_ha":' || TO_CHAR(ROUND(o.area_imovel, 4))
                        || ',"area_vegetacao_ha":' || TO_CHAR(ROUND(o.area_vegetacao, 4))
                        || ',"minimo_pct":' || TO_CHAR(p_minimo_pct)
                        || ',"poligonos_considerados":' || TO_CHAR(o.poligonos)
                        || ',"dissolvido":true}' AS detalhes
                 FROM (SELECT i.imo_id,
                              NVL(i.imo_area_total, 0) AS area_imovel,
                              -- une a vegetacao que toca o imovel e so entao recorta
                              NVL(PKG_GEO.area_intersecao_ha(
                                    i.imo_geometria,
                                    (SELECT SDO_AGGR_UNION(SDOAGGRTYPE(v.veg_geometria, 0.05))
                                       FROM VEGETACAO_NATIVA v
                                      WHERE v.veg_geometria IS NOT NULL
                                        AND SDO_RELATE(v.veg_geometria, i.imo_geometria,
                                                       'mask=ANYINTERACT') = 'TRUE')), 0)
                                AS area_vegetacao,
                              NVL((SELECT COUNT(*)
                                     FROM VEGETACAO_NATIVA v
                                    WHERE v.veg_geometria IS NOT NULL
                                      AND SDO_RELATE(v.veg_geometria, i.imo_geometria,
                                                     'mask=ANYINTERACT') = 'TRUE'), 0) AS poligonos
                         FROM IMOVEL_RURAL i
                        WHERE i.imo_id IN (SELECT column_value FROM TABLE(v_ids))) o) f
           ON (d.imo_id = f.imo_id AND d.ind_id = f.ind_id AND d.ver_id = p_ver_id)
         WHEN MATCHED THEN
              UPDATE SET d.rin_valor_percentual = f.pct,
                         d.rin_valor_hectares   = f.area_vegetacao,
                         d.rin_valor_absoluto   = f.area_vegetacao,
                         d.rin_situacao         = f.situacao,
                         d.rin_detalhes         = f.detalhes,
                         d.rin_data_calculo     = SYSTIMESTAMP,
                         d.exe_id               = p_exe_id,
                         d.rca_id               = p_rca_id
         WHEN NOT MATCHED THEN
              INSERT (imo_id, ind_id, ver_id, exe_id, rca_id, rin_valor_percentual,
                      rin_valor_hectares, rin_valor_absoluto, rin_situacao, rin_detalhes,
                      rin_data_calculo, rin_vigente)
              VALUES (f.imo_id, f.ind_id, p_ver_id, p_exe_id, p_rca_id, f.pct,
                      f.area_vegetacao, f.area_vegetacao, f.situacao, f.detalhes,
                      SYSTIMESTAMP, 'N');
        p_calculados := SQL%ROWCOUNT;
    END calcular_icv;

    PROCEDURE agregar_municipio(p_ver_id NUMBER, p_sigla VARCHAR2, p_agregados OUT NUMBER) IS
        v_ind NUMBER := id_indicador(p_sigla);
        v_boa VARCHAR2(20) := CASE p_sigla
                                WHEN 'IAE' THEN 'SEM_EMBARGO'
                                WHEN 'ICV' THEN 'CONSERVADO'
                                ELSE 'CONFORME'
                              END;
    BEGIN
        MERGE INTO RESULTADO_MUNICIPIO d
        USING (SELECT i.mun_id,
                      v_ind AS ind_id,
                      AVG(r.rin_valor_percentual) AS media_pct,
                      '{"imoveis":' || TO_CHAR(COUNT(*))
                        || ',"media_percentual":' || TO_CHAR(ROUND(AVG(r.rin_valor_percentual), 4))
                        || ',"soma_hectares":' || TO_CHAR(ROUND(NVL(SUM(r.rin_valor_hectares), 0), 4))
                        || ',"conformes":' || TO_CHAR(SUM(CASE WHEN r.rin_situacao = v_boa
                                                               THEN 1 ELSE 0 END))
                        || ',"situacao_boa":"' || v_boa || '"'
                        || '}' AS detalhes,
                      COUNT(*) AS imoveis
                 FROM RESULTADO_INDICADOR r
                 JOIN IMOVEL_RURAL i ON i.imo_id = r.imo_id
                WHERE r.ver_id = p_ver_id
                  AND r.ind_id = v_ind
                  AND i.mun_id IS NOT NULL
                GROUP BY i.mun_id) f
           ON (d.mun_id = f.mun_id AND d.ind_id = f.ind_id AND d.ver_id = p_ver_id)
         WHEN MATCHED THEN
              UPDATE SET d.rmu_valor_agregado = f.media_pct,
                         d.rmu_detalhes       = f.detalhes,
                         d.rmu_data_calculo   = SYSTIMESTAMP
         WHEN NOT MATCHED THEN
              INSERT (mun_id, ind_id, ver_id, rmu_valor_agregado, rmu_situacao,
                      rmu_detalhes, rmu_vigente, rmu_data_calculo)
              VALUES (f.mun_id, f.ind_id, p_ver_id, f.media_pct, 'CALCULADO',
                      f.detalhes, 'N', SYSTIMESTAMP);
        p_agregados := SQL%ROWCOUNT;
    END agregar_municipio;

    PROCEDURE publicar(p_ver_id NUMBER, p_publicados OUT NUMBER) IS
        v_con_id NUMBER;
    BEGIN
        SELECT con_id INTO v_con_id FROM VERSAO WHERE ver_id = p_ver_id;

        -- resultados das versoes anteriores do mesmo conjunto saem de vigentes
        UPDATE RESULTADO_INDICADOR r
           SET r.rin_vigente = 'N'
         WHERE r.rin_vigente = 'S'
           AND r.ver_id IN (SELECT ver_id FROM VERSAO
                             WHERE con_id = v_con_id AND ver_id <> p_ver_id);

        UPDATE RESULTADO_MUNICIPIO m
           SET m.rmu_vigente = 'N'
         WHERE m.rmu_vigente = 'S'
           AND m.ver_id IN (SELECT ver_id FROM VERSAO
                             WHERE con_id = v_con_id AND ver_id <> p_ver_id);

        UPDATE RESULTADO_INDICADOR r SET r.rin_vigente = 'S' WHERE r.ver_id = p_ver_id;
        p_publicados := SQL%ROWCOUNT;

        UPDATE RESULTADO_MUNICIPIO m SET m.rmu_vigente = 'S' WHERE m.ver_id = p_ver_id;
    END publicar;

END PKG_CALCULO;
/
