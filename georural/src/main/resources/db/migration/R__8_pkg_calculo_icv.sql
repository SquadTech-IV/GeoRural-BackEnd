CREATE OR REPLACE PACKAGE PKG_CALCULO_ICV AS

    PROCEDURE calcular_icv(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                           p_minimo_pct NUMBER, p_escopo VARCHAR2, p_calculados OUT NUMBER);

END PKG_CALCULO_ICV;
/

CREATE OR REPLACE PACKAGE BODY PKG_CALCULO_ICV AS

    PROCEDURE calcular_icv(p_ver_id NUMBER, p_exe_id NUMBER, p_rca_id NUMBER,
                           p_minimo_pct NUMBER, p_escopo VARCHAR2, p_calculados OUT NUMBER) IS
        v_ids SYS.ODCINUMBERLIST := PKG_CALCULO.imoveis_no_escopo(p_ver_id, p_escopo);
        v_ind NUMBER := PKG_CALCULO.id_indicador('ICV');
    BEGIN
        IF v_ids.COUNT = 0 THEN
            p_calculados := 0;
            RETURN;
        END IF;

        MERGE INTO RESULTADO_INDICADOR d
        USING (SELECT o.imo_id,
                      v_ind AS ind_id,
                      o.pct,
                      o.area_veg,
                      o.area_veg - o.minimo_ha AS saldo_ha,
                      CASE WHEN o.pct >= p_minimo_pct THEN 'CONFORME' ELSE 'DEFICIT' END AS situacao,
                      '{"area_imovel_ha":' || TO_CHAR(ROUND(o.area_imovel, 4))
                        || ',"area_vegetacao_ha":' || TO_CHAR(ROUND(o.area_veg, 4))
                        || ',"minimo_pct":' || TO_CHAR(p_minimo_pct)
                        || ',"minimo_ha":' || TO_CHAR(ROUND(o.minimo_ha, 4))
                        || ',"saldo_ha":' || TO_CHAR(ROUND(o.area_veg - o.minimo_ha, 4))
                        || ',"vegetacoes_consideradas":' || TO_CHAR(o.vegetacoes) || '}' AS detalhes
                 FROM (SELECT i.imo_id,
                              NVL(i.imo_area_total, 0) AS area_imovel,
                              NVL(i.imo_area_total, 0) * p_minimo_pct / 100 AS minimo_ha,
                              NVL((SELECT SUM(PKG_GEO.area_intersecao_ha(i.imo_geometria,
                                                                         v.veg_geometria))
                                     FROM VEGETACAO_NATIVA v
                                    WHERE v.ver_id = p_ver_id
                                      AND v.veg_geometria IS NOT NULL
                                      AND SDO_RELATE(v.veg_geometria, i.imo_geometria,
                                                     'mask=ANYINTERACT') = 'TRUE'), 0) AS area_veg,
                              NVL((SELECT COUNT(*)
                                     FROM VEGETACAO_NATIVA v
                                    WHERE v.ver_id = p_ver_id
                                      AND v.veg_geometria IS NOT NULL
                                      AND SDO_RELATE(v.veg_geometria, i.imo_geometria,
                                                     'mask=ANYINTERACT') = 'TRUE'), 0) AS vegetacoes,
                              NVL((SELECT SUM(PKG_GEO.area_intersecao_ha(i.imo_geometria,
                                                                         v.veg_geometria))
                                     FROM VEGETACAO_NATIVA v
                                    WHERE v.ver_id = p_ver_id
                                      AND v.veg_geometria IS NOT NULL
                                      AND SDO_RELATE(v.veg_geometria, i.imo_geometria,
                                                     'mask=ANYINTERACT') = 'TRUE'), 0)
                                / NULLIF(i.imo_area_total, 0) * 100 AS pct
                         FROM IMOVEL_RURAL i
                        WHERE i.imo_id IN (SELECT column_value FROM TABLE(v_ids))) o) f
           ON (d.imo_id = f.imo_id AND d.ind_id = f.ind_id AND d.ver_id = p_ver_id)
         WHEN MATCHED THEN
              UPDATE SET d.rin_valor_percentual = f.pct,
                         d.rin_valor_hectares   = f.area_veg,
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
                      f.area_veg, f.saldo_ha, f.situacao, f.detalhes,
                      SYSTIMESTAMP, 'N');
        p_calculados := SQL%ROWCOUNT;
    END calcular_icv;

END PKG_CALCULO_ICV;
/