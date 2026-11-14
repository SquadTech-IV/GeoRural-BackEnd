CREATE OR REPLACE PACKAGE PKG_CARREGAR_VEGETACAO AS

    PROCEDURE carregar_vegetacao(p_exe_id NUMBER, p_ver_id NUMBER, p_gravadas OUT NUMBER,
                                 p_arq_id NUMBER DEFAULT NULL);

END PKG_CARREGAR_VEGETACAO;
/

CREATE OR REPLACE PACKAGE BODY PKG_CARREGAR_VEGETACAO AS

    PROCEDURE carregar_vegetacao(p_exe_id NUMBER, p_ver_id NUMBER, p_gravadas OUT NUMBER,
                                     p_arq_id NUMBER DEFAULT NULL) IS

    BEGIN
        DELETE FROM VEGETACAO_NATIVA v
         WHERE v.ver_id = p_ver_id
           AND v.imo_id IN (
               SELECT i.imo_id
                 FROM FEICAO_STAGING s
                 JOIN IMOVEL_RURAL i ON i.imo_codigo_car = JSON_VALUE(s.stg_atributos, '$.codigo_car')
                WHERE s.exe_id = p_exe_id
                  AND (p_arq_id IS NULL OR s.stg_arq_bruto_id = p_arq_id)
           );

        -- Insere a vegetação aplicando o SDO_AGGR_UNION por imóvel para unificar polígonos sobrepostos
        INSERT INTO VEGETACAO_NATIVA (imo_id, arq_bruto_id, ver_id, veg_area_ha, veg_geometria)
        SELECT i.imo_id,
               NVL(s.stg_arq_bruto_id, e.arq_bruto_id),
               p_ver_id,
               PKG_GEO.area_ha(
                   SDO_AGGR_UNION(
                       SDOAGGRTYPE(s.stg_geometria, 0.005)
                   )
               ) AS area_unida,
               SDO_AGGR_UNION(
                   SDOAGGRTYPE(s.stg_geometria, 0.005)
               ) AS geometria_unida
          FROM FEICAO_STAGING s
          JOIN EXECUCAO_PIPELINE e ON e.exe_id = s.exe_id
          JOIN IMOVEL_RURAL i ON i.imo_codigo_car = JSON_VALUE(s.stg_atributos, '$.codigo_car')
         WHERE s.exe_id = p_exe_id
           AND (p_arq_id IS NULL OR s.stg_arq_bruto_id = p_arq_id)
           AND s.stg_geometria IS NOT NULL
         GROUP BY i.imo_id, NVL(s.stg_arq_bruto_id, e.arq_bruto_id);

        p_gravadas := SQL%ROWCOUNT;
    END carregar_vegetacao;

END PKG_CARREGAR_VEGETACAO;
/