-- =====================================================================
-- GeoRural DataHub - PKG_TRATAMENTO
-- =====================================================================


CREATE OR REPLACE PACKAGE PKG_TRATAMENTO AS

    -- desliga o DML paralelo da sessao (evita ORA-12839 no Autonomous)
    PROCEDURE preparar_sessao;

    PROCEDURE sanear(p_exe_id NUMBER, p_corrigidas OUT NUMBER);

    PROCEDURE rejeitar_invalidas(p_exe_id NUMBER, p_val_id NUMBER, p_rejeitadas OUT NUMBER);

    PROCEDURE recortar_uf(p_exe_id NUMBER, p_uf VARCHAR2, p_recortadas OUT NUMBER);

    PROCEDURE rejeitar_fora_da_uf(p_exe_id NUMBER, p_val_id NUMBER, p_uf VARCHAR2,
                                  p_rejeitadas OUT NUMBER);

    -- calcula stg_area_ha uma unica vez, depois do recorte
    PROCEDURE calcular_areas(p_exe_id NUMBER, p_calculadas OUT NUMBER);

    PROCEDURE rejeitar_area_nula(p_exe_id NUMBER, p_val_id NUMBER, p_rejeitadas OUT NUMBER);

    PROCEDURE rejeitar_rl_sem_imovel(p_exe_id NUMBER, p_val_id NUMBER, p_rejeitadas OUT NUMBER);

    PROCEDURE avisar_area_incompativel(p_exe_id NUMBER, p_val_id NUMBER,
                                       p_tolerancia_pct NUMBER, p_avisos OUT NUMBER);

    -- hash do conteudo tratado; e o que evita versao nova quando nada mudou
    FUNCTION impressao(p_exe_id NUMBER) RETURN VARCHAR2;

    FUNCTION restantes(p_exe_id NUMBER) RETURN NUMBER;

    PROCEDURE carregar_imovel(p_exe_id NUMBER, p_ver_id NUMBER, p_gravadas OUT NUMBER);

    PROCEDURE carregar_reserva_legal(p_exe_id NUMBER, p_ver_id NUMBER, p_gravadas OUT NUMBER);

    PROCEDURE carregar_embargo(p_exe_id NUMBER, p_ver_id NUMBER, p_gravadas OUT NUMBER);

    PROCEDURE carregar_municipio(p_exe_id NUMBER, p_gravadas OUT NUMBER);

    PROCEDURE vincular_municipio(p_exe_id NUMBER, p_ver_id NUMBER,
                                 p_por_codigo OUT NUMBER, p_por_geometria OUT NUMBER);

END PKG_TRATAMENTO;
/

CREATE OR REPLACE PACKAGE BODY PKG_TRATAMENTO AS

    -- id da regra de qualidade pelo nome do V2
    FUNCTION id_regra(p_nome VARCHAR2) RETURN NUMBER IS
        v_id NUMBER;
    BEGIN
        SELECT reg_id INTO v_id FROM REGRA_QUALIDADE WHERE reg_nome = p_nome;
        RETURN v_id;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN RETURN NULL;
    END id_regra;

    PROCEDURE preparar_sessao IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER SESSION DISABLE PARALLEL DML';
    END preparar_sessao;

    PROCEDURE sanear(p_exe_id NUMBER, p_corrigidas OUT NUMBER) IS
    BEGIN
        UPDATE FEICAO_STAGING s
           SET s.stg_geometria = PKG_GEO.saneada(s.stg_geometria)
         WHERE s.exe_id = p_exe_id
           AND s.stg_geometria IS NOT NULL
           AND PKG_GEO.valida(s.stg_geometria) <> 'TRUE';
        p_corrigidas := SQL%ROWCOUNT;
    END sanear;

    PROCEDURE rejeitar_invalidas(p_exe_id NUMBER, p_val_id NUMBER, p_rejeitadas OUT NUMBER) IS
    BEGIN
        INSERT INTO QUARENTENA (val_id, reg_id, qua_etapa, qua_identificador, qua_linha_origem,
                                qua_coluna_erro, qua_valor_encontrado, qua_valor_esperado,
                                qua_motivo_erro, qua_dado_original, qua_situacao)
        SELECT p_val_id,
               id_regra('Geometria invalida'),
               'TRATAMENTO',
               SUBSTR(s.stg_identificador, 1, 120),
               s.stg_linha,
               'geometria',
               SUBSTR(PKG_GEO.valida(s.stg_geometria), 1, 400),
               'geometria valida para o Oracle Spatial',
               'A geometria continua invalida depois do saneamento automatico '
                 || '(SDO_UTIL.RECTIFY_GEOMETRY). A feicao nao entra no calculo.',
               s.stg_atributos,
               'PENDENTE'
          FROM FEICAO_STAGING s
         WHERE s.exe_id = p_exe_id
           AND s.stg_geometria IS NOT NULL
           AND PKG_GEO.valida(s.stg_geometria) <> 'TRUE';
        p_rejeitadas := SQL%ROWCOUNT;

        DELETE FROM FEICAO_STAGING s
         WHERE s.exe_id = p_exe_id
           AND s.stg_geometria IS NOT NULL
           AND PKG_GEO.valida(s.stg_geometria) <> 'TRUE';
    END rejeitar_invalidas;

    PROCEDURE recortar_uf(p_exe_id NUMBER, p_uf VARCHAR2, p_recortadas OUT NUMBER) IS
    BEGIN
        UPDATE FEICAO_STAGING s
           SET s.stg_geometria = PKG_GEO.recorte_uf(s.stg_geometria, p_uf)
         WHERE s.exe_id = p_exe_id
           AND s.stg_geometria IS NOT NULL;
        p_recortadas := SQL%ROWCOUNT;
    END recortar_uf;

    -- depois do recorte, geometria nula significa totalmente fora da UF
    PROCEDURE rejeitar_fora_da_uf(p_exe_id NUMBER, p_val_id NUMBER, p_uf VARCHAR2,
                                  p_rejeitadas OUT NUMBER) IS
    BEGIN
        INSERT INTO QUARENTENA (val_id, reg_id, qua_etapa, qua_identificador, qua_linha_origem,
                                qua_coluna_erro, qua_valor_encontrado, qua_valor_esperado,
                                qua_motivo_erro, qua_dado_original, qua_situacao)
        SELECT p_val_id,
               id_regra('Coordenada fora da area de interesse'),
               'TRATAMENTO',
               SUBSTR(s.stg_identificador, 1, 120),
               s.stg_linha,
               'geometria',
               'fora do limite de ' || p_uf,
               'geometria dentro do limite de ' || p_uf,
               'Depois do recorte pelo limite de ' || p_uf || ' nao sobrou area nenhuma da '
                 || 'feicao: ela esta inteiramente fora do recorte do projeto.',
               s.stg_atributos,
               'PENDENTE'
          FROM FEICAO_STAGING s
         WHERE s.exe_id = p_exe_id
           AND s.stg_geometria IS NULL;
        p_rejeitadas := SQL%ROWCOUNT;

        DELETE FROM FEICAO_STAGING s
         WHERE s.exe_id = p_exe_id
           AND s.stg_geometria IS NULL;
    END rejeitar_fora_da_uf;

    -- uma passada so: todas as etapas seguintes leem stg_area_ha
    PROCEDURE calcular_areas(p_exe_id NUMBER, p_calculadas OUT NUMBER) IS
    BEGIN
        UPDATE FEICAO_STAGING s
           SET s.stg_area_ha = PKG_GEO.area_ha(s.stg_geometria)
         WHERE s.exe_id = p_exe_id;
        p_calculadas := SQL%ROWCOUNT;
    END calcular_areas;

    PROCEDURE rejeitar_area_nula(p_exe_id NUMBER, p_val_id NUMBER, p_rejeitadas OUT NUMBER) IS
    BEGIN
        INSERT INTO QUARENTENA (val_id, reg_id, qua_etapa, qua_identificador, qua_linha_origem,
                                qua_coluna_erro, qua_valor_encontrado, qua_valor_esperado,
                                qua_motivo_erro, qua_dado_original, qua_situacao)
        SELECT p_val_id,
               id_regra('Area nula ou negativa'),
               'TRATAMENTO',
               SUBSTR(s.stg_identificador, 1, 120),
               s.stg_linha,
               'area',
               TO_CHAR(NVL(s.stg_area_ha, 0)) || ' ha',
               'area maior que zero',
               'A geometria resultou em area zero ou negativa, o que impede o calculo dos '
                 || 'indicadores em hectares.',
               s.stg_atributos,
               'PENDENTE'
          FROM FEICAO_STAGING s
         WHERE s.exe_id = p_exe_id
           AND NVL(s.stg_area_ha, 0) <= 0;
        p_rejeitadas := SQL%ROWCOUNT;

        DELETE FROM FEICAO_STAGING s
         WHERE s.exe_id = p_exe_id
           AND NVL(s.stg_area_ha, 0) <= 0;
    END rejeitar_area_nula;

    -- reserva legal sem o imovel na base nao tem onde se pendurar
    PROCEDURE rejeitar_rl_sem_imovel(p_exe_id NUMBER, p_val_id NUMBER, p_rejeitadas OUT NUMBER) IS
    BEGIN
        INSERT INTO QUARENTENA (val_id, reg_id, qua_etapa, qua_identificador, qua_linha_origem,
                                qua_coluna_erro, qua_valor_encontrado, qua_valor_esperado,
                                qua_motivo_erro, qua_dado_original, qua_situacao)
        SELECT p_val_id,
               id_regra('Valor fora do dominio'),
               'TRATAMENTO',
               SUBSTR(s.stg_identificador, 1, 120),
               s.stg_linha,
               'codigo_car',
               SUBSTR(JSON_VALUE(s.stg_atributos, '$.codigo_car'), 1, 400),
               'codigo do CAR existente em IMOVEL_RURAL',
               'A reserva legal aponta para um CAR que nao esta na base. Processe o conjunto '
                 || 'de imoveis rurais antes do de reserva legal.',
               s.stg_atributos,
               'PENDENTE'
          FROM FEICAO_STAGING s
         WHERE s.exe_id = p_exe_id
           AND NOT EXISTS (SELECT 1 FROM IMOVEL_RURAL i
                            WHERE i.imo_codigo_car = JSON_VALUE(s.stg_atributos, '$.codigo_car'));
        p_rejeitadas := SQL%ROWCOUNT;

        DELETE FROM FEICAO_STAGING s
         WHERE s.exe_id = p_exe_id
           AND NOT EXISTS (SELECT 1 FROM IMOVEL_RURAL i
                            WHERE i.imo_codigo_car = JSON_VALUE(s.stg_atributos, '$.codigo_car'));
    END rejeitar_rl_sem_imovel;

    -- area declarada x area calculada; so avisa, nao rejeita
    PROCEDURE avisar_area_incompativel(p_exe_id NUMBER, p_val_id NUMBER,
                                       p_tolerancia_pct NUMBER, p_avisos OUT NUMBER) IS
    BEGIN
        INSERT INTO QUARENTENA (val_id, reg_id, qua_etapa, qua_identificador, qua_linha_origem,
                                qua_coluna_erro, qua_valor_encontrado, qua_valor_esperado,
                                qua_motivo_erro, qua_dado_original, qua_situacao)
        SELECT p_val_id,
               id_regra('Area incompativel'),
               'TRATAMENTO',
               SUBSTR(o.identificador, 1, 120),
               o.linha,
               'area_declarada',
               'declarada ' || TO_CHAR(ROUND(o.declarada, 4)) || ' ha, calculada '
                 || TO_CHAR(ROUND(o.calculada, 4)) || ' ha',
               'diferenca de ate ' || TO_CHAR(p_tolerancia_pct) || '%',
               'A area declarada no arquivo difere da calculada pela geometria em '
                 || TO_CHAR(ROUND(o.diferenca_pct, 2)) || '%. A feicao segue no fluxo; o '
                 || 'calculo usa a area da geometria.',
               o.atributos,
               'PENDENTE'
          FROM (SELECT s.stg_identificador AS identificador,
                       s.stg_linha         AS linha,
                       s.stg_atributos     AS atributos,
                       s.declarada,
                       s.stg_area_ha       AS calculada,
                       ABS(s.declarada - s.stg_area_ha) / s.declarada * 100 AS diferenca_pct
                  FROM (SELECT stg_identificador, stg_linha, stg_atributos, stg_area_ha,
                               JSON_VALUE(stg_atributos, '$.area_declarada' RETURNING NUMBER
                                          NULL ON ERROR) AS declarada
                          FROM FEICAO_STAGING
                         WHERE exe_id = p_exe_id) s
                 WHERE s.declarada > 0
                   AND s.stg_area_ha > 0) o
         WHERE o.diferenca_pct > p_tolerancia_pct;
        p_avisos := SQL%ROWCOUNT;
    END avisar_area_incompativel;

    -- impressao do conteudo tratado: XOR dos hashes de linha + hash da contagem.
    FUNCTION impressao(p_exe_id NUMBER) RETURN VARCHAR2 IS
        v_acumulado RAW(32) := HEXTORAW(RPAD('0', 64, '0'));
        v_total     NUMBER := 0;
    BEGIN
        FOR r IN (SELECT NVL(stg_identificador, 'linha-' || stg_linha) AS chave,
                         TO_CHAR(ROUND(NVL(stg_area_ha, 0), 4)) AS area
                    FROM FEICAO_STAGING
                   WHERE exe_id = p_exe_id) LOOP
            v_acumulado := UTL_RAW.BIT_XOR(v_acumulado,
                               STANDARD_HASH(r.chave || '|' || r.area, 'SHA256'));
            v_total := v_total + 1;
        END LOOP;

        v_acumulado := UTL_RAW.BIT_XOR(v_acumulado,
                           STANDARD_HASH('total=' || TO_CHAR(v_total), 'SHA256'));
        RETURN LOWER(RAWTOHEX(v_acumulado));
    END impressao;

    FUNCTION restantes(p_exe_id NUMBER) RETURN NUMBER IS
        v_total NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_total FROM FEICAO_STAGING WHERE exe_id = p_exe_id;
        RETURN v_total;
    END restantes;

    PROCEDURE carregar_imovel(p_exe_id NUMBER, p_ver_id NUMBER, p_gravadas OUT NUMBER) IS
    BEGIN
        MERGE INTO IMOVEL_RURAL d
        USING (SELECT codigo_car, geometria, area, data_informada, arq_bruto_id
                 FROM (SELECT JSON_VALUE(s.stg_atributos, '$.codigo_car') AS codigo_car,
                              s.stg_geometria   AS geometria,
                              s.stg_area_ha     AS area,
                              TO_DATE(SUBSTR(JSON_VALUE(s.stg_atributos, '$.data_informada'), 1, 10)
                                      DEFAULT NULL ON CONVERSION ERROR,
                                      'YYYY-MM-DD') AS data_informada,
                              NVL(s.stg_arq_bruto_id, e.arq_bruto_id) AS arq_bruto_id,
                              ROW_NUMBER() OVER (
                                  PARTITION BY JSON_VALUE(s.stg_atributos, '$.codigo_car')
                                  ORDER BY s.stg_id DESC) AS ordem
                         FROM FEICAO_STAGING s
                         JOIN EXECUCAO_PIPELINE e ON e.exe_id = s.exe_id
                        WHERE s.exe_id = p_exe_id
                          AND JSON_VALUE(s.stg_atributos, '$.codigo_car') IS NOT NULL)
                WHERE ordem = 1) o
           ON (d.imo_codigo_car = o.codigo_car)
         WHEN MATCHED THEN
              UPDATE SET d.imo_geometria      = o.geometria,
                         d.imo_area_total     = o.area,
                         d.imo_data_informada = o.data_informada,
                         d.ver_id             = p_ver_id,
                         d.arq_bruto_id       = o.arq_bruto_id
         WHEN NOT MATCHED THEN
              INSERT (imo_codigo_car, imo_geometria, imo_area_total, imo_data_informada,
                      ver_id, arq_bruto_id)
              VALUES (o.codigo_car, o.geometria, o.area, o.data_informada,
                      p_ver_id, o.arq_bruto_id);
        p_gravadas := SQL%ROWCOUNT;
    END carregar_imovel;

    PROCEDURE carregar_reserva_legal(p_exe_id NUMBER, p_ver_id NUMBER, p_gravadas OUT NUMBER) IS
    BEGIN
        -- substitui a reserva legal dos imoveis que vieram neste arquivo
        DELETE FROM RESERVA_LEGAL r
         WHERE r.imo_id IN (SELECT i.imo_id
                              FROM FEICAO_STAGING s
                              JOIN IMOVEL_RURAL i
                                ON i.imo_codigo_car = JSON_VALUE(s.stg_atributos, '$.codigo_car')
                             WHERE s.exe_id = p_exe_id);

        INSERT INTO RESERVA_LEGAL (imo_id, arq_bruto_id, ver_id, rle_area_ha, rle_geometria)
        SELECT i.imo_id,
               NVL(s.stg_arq_bruto_id, e.arq_bruto_id),
               p_ver_id,
               s.stg_area_ha,
               s.stg_geometria
          FROM FEICAO_STAGING s
          JOIN EXECUCAO_PIPELINE e ON e.exe_id = s.exe_id
          JOIN IMOVEL_RURAL i ON i.imo_codigo_car = JSON_VALUE(s.stg_atributos, '$.codigo_car')
         WHERE s.exe_id = p_exe_id;
        p_gravadas := SQL%ROWCOUNT;
    END carregar_reserva_legal;

    PROCEDURE carregar_embargo(p_exe_id NUMBER, p_ver_id NUMBER, p_gravadas OUT NUMBER) IS
    BEGIN
        -- substitui os embargos com o mesmo identificador
        DELETE FROM EMBARGO b
         WHERE b.emb_identificador IN (SELECT SUBSTR(s.stg_identificador, 1, 80)
                                         FROM FEICAO_STAGING s
                                        WHERE s.exe_id = p_exe_id
                                          AND s.stg_identificador IS NOT NULL);

        INSERT INTO EMBARGO (arq_bruto_id, ver_id, emb_identificador, emb_geometria)
        SELECT NVL(s.stg_arq_bruto_id, e.arq_bruto_id),
               p_ver_id,
               SUBSTR(s.stg_identificador, 1, 80),
               s.stg_geometria
          FROM FEICAO_STAGING s
          JOIN EXECUCAO_PIPELINE e ON e.exe_id = s.exe_id
         WHERE s.exe_id = p_exe_id;
        p_gravadas := SQL%ROWCOUNT;
    END carregar_embargo;

    PROCEDURE carregar_municipio(p_exe_id NUMBER, p_gravadas OUT NUMBER) IS
    BEGIN
        MERGE INTO MUNICIPIO d
        USING (SELECT codigo, nome, uf, area_ha, geometria, arq_bruto_id
                 FROM (SELECT SUBSTR(JSON_VALUE(s.stg_atributos, '$.municipio_codigo'), 1, 7) AS codigo,
                              SUBSTR(JSON_VALUE(s.stg_atributos, '$.municipio_nome'), 1, 120)  AS nome,
                              NVL(SUBSTR(JSON_VALUE(s.stg_atributos, '$.uf'), 1, 2), 'PR')     AS uf,
                              s.stg_area_ha                                                    AS area_ha,
                              s.stg_geometria                                                  AS geometria,
                              NVL(s.stg_arq_bruto_id, e.arq_bruto_id)                          AS arq_bruto_id,
                              ROW_NUMBER() OVER (
                                  PARTITION BY JSON_VALUE(s.stg_atributos, '$.municipio_codigo')
                                  ORDER BY s.stg_id DESC)                                       AS ordem
                         FROM FEICAO_STAGING s
                         JOIN EXECUCAO_PIPELINE e ON e.exe_id = s.exe_id
                        WHERE s.exe_id = p_exe_id
                          AND JSON_VALUE(s.stg_atributos, '$.municipio_codigo') IS NOT NULL
                          AND JSON_VALUE(s.stg_atributos, '$.municipio_nome') IS NOT NULL)
                WHERE ordem = 1) o
           ON (d.mun_codigo_origem = o.codigo)
         WHEN MATCHED THEN
              UPDATE SET d.mun_nome      = o.nome,
                         d.mun_area_ha   = o.area_ha,
                         d.mun_geometria = o.geometria,
                         d.arq_bruto_id  = o.arq_bruto_id
         WHEN NOT MATCHED THEN
              INSERT (mun_codigo_origem, mun_nome, mun_uf, mun_area_ha, mun_geometria, arq_bruto_id)
              VALUES (o.codigo, o.nome, o.uf, o.area_ha, o.geometria, o.arq_bruto_id);
        p_gravadas := SQL%ROWCOUNT;
    END carregar_municipio;

    -- amarra o imovel ao municipio
    PROCEDURE vincular_municipio(p_exe_id NUMBER, p_ver_id NUMBER,
                                 p_por_codigo OUT NUMBER, p_por_geometria OUT NUMBER) IS
    BEGIN
        UPDATE IMOVEL_RURAL i
           SET i.mun_id = (SELECT MIN(m.mun_id)
                             FROM FEICAO_STAGING s
                             JOIN MUNICIPIO m
                               ON m.mun_codigo_origem =
                                  SUBSTR(JSON_VALUE(s.stg_atributos, '$.municipio_codigo'), 1, 7)
                            WHERE s.exe_id = p_exe_id
                              AND JSON_VALUE(s.stg_atributos, '$.codigo_car') = i.imo_codigo_car)
         WHERE i.ver_id = p_ver_id
           AND i.mun_id IS NULL;
        p_por_codigo := SQL%ROWCOUNT;

        -- SDO_RELATE (operador, nao funcao) para usar o indice espacial de MUNICIPIO
        UPDATE IMOVEL_RURAL i
           SET i.mun_id = (SELECT MIN(m.mun_id)
                             FROM MUNICIPIO m
                            WHERE m.mun_geometria IS NOT NULL
                              AND SDO_RELATE(m.mun_geometria, i.imo_geometria,
                                             'mask=ANYINTERACT') = 'TRUE')
         WHERE i.ver_id = p_ver_id
           AND i.mun_id IS NULL
           AND i.imo_geometria IS NOT NULL;
        p_por_geometria := SQL%ROWCOUNT;
    END vincular_municipio;

END PKG_TRATAMENTO;
/
