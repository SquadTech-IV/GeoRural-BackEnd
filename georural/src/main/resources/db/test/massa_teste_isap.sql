-- =====================================================================
-- GeoRural DataHub - Massa de Teste Sintetica do Indicador ISAP (US#24.03)
-- SRID: 4674 (SIRGAS 2000) | Regiao: Parana (lon ~ -51.0, lat ~ -25.0)
-- =====================================================================
-- Cenarios cobertos:
--   1. Imovel sem sobreposicao (IMO-ISAP-01)
--   2. Sobreposicao parcial com 1 categoria - UC (IMO-ISAP-02)
--   3. Sobreposicao com as 4 categorias + categorias coincidentes / dupla contagem (IMO-ISAP-03)
--   4. Sobreposicao total 100% + 2 feicoes sobrepostas na mesma categoria + UC fora do filtro (IMO-ISAP-04)
--   5. Camada indisponivel - FLORESTA_PUBLICA nao publicada em versao de teste (IMO-ISAP-05)
--   6. Geometria invalida (auto-intersecao) em FEICAO_STAGING para teste da US#24.05
-- =====================================================================

ALTER SESSION DISABLE PARALLEL DML;

DECLARE
    v_usu_id         NUMBER;
    v_tip_imovel     NUMBER;
    v_tip_ap         NUMBER;

    -- Fontes e Conjuntos de teste ISAP
    v_fon_isap       NUMBER;
    v_con_imovel     NUMBER;
    v_con_uc         NUMBER;
    v_con_ti         NUMBER;
    v_con_quil       NUMBER;
    v_con_fp         NUMBER;
    v_con_fp_indisp  NUMBER; -- Conjunto propositalmente sem versao vigente (CAMADA_INDISPONIVEL)

    -- Arquivos brutos sinteticos
    v_arq_imovel     NUMBER;
    v_arq_uc         NUMBER;
    v_arq_ti         NUMBER;
    v_arq_quil       NUMBER;
    v_arq_fp         NUMBER;

    -- Versoes vigentes de teste
    v_ver_imovel     NUMBER;
    v_ver_uc         NUMBER;
    v_ver_ti         NUMBER;
    v_ver_quil       NUMBER;
    v_ver_fp         NUMBER;

    -- Execucao sintetica para staging (geometria invalida)
    v_exe_teste      NUMBER;

    -- Geometrias base (quadrados 0.01 x 0.01 grau ~ 111.7 ha cada celula 1x1)
    g_imo_01 SDO_GEOMETRY;
    g_imo_02 SDO_GEOMETRY;
    g_imo_03 SDO_GEOMETRY;
    g_imo_04 SDO_GEOMETRY;
    g_imo_05 SDO_GEOMETRY;

    a_imo_01 NUMBER;
    a_imo_02 NUMBER;
    a_imo_03 NUMBER;
    a_imo_04 NUMBER;
    a_imo_05 NUMBER;
BEGIN
    -- 0. Usuario espelho ADMIN para FKs obrigatorias
    SELECT MIN(usu_id) INTO v_usu_id FROM USUARIO;

    SELECT tip_id INTO v_tip_imovel FROM TIPO_CONJUNTO WHERE tip_codigo = 'IMOVEL';
    SELECT tip_id INTO v_tip_ap     FROM TIPO_CONJUNTO WHERE tip_codigo = 'AREA_PROTEGIDA';

    -- 1. Limpeza idempotente de execucao anterior da massa de teste ISAP
    DELETE FROM AREA_PROTEGIDA WHERE apr_nome LIKE '[TESTE-ISAP]%';
    DELETE FROM IMOVEL_RURAL   WHERE imo_codigo_car LIKE 'PR-TESTE-ISAP-%';
    DELETE FROM FEICAO_STAGING WHERE stg_identificador LIKE 'TESTE-ISAP-%';

    -- 2. Fonte e Conjuntos de Dados sinteticos
    MERGE INTO FONTE_DO_DADO dst
    USING (SELECT 'TESTE_ISAP' AS fon_sigla, 'Fonte Sintética Testes ISAP' AS fon_nome FROM DUAL) src
       ON (dst.fon_sigla = src.fon_sigla)
     WHEN NOT MATCHED THEN
          INSERT (fon_nome, fon_sigla, fon_esfera)
          VALUES (src.fon_nome, src.fon_sigla, 'FEDERAL');

    SELECT fon_id INTO v_fon_isap FROM FONTE_DO_DADO WHERE fon_sigla = 'TESTE_ISAP';

    -- Conjunto Imoveis Teste
    MERGE INTO CONJUNTO_DADOS dst
    USING (SELECT v_fon_isap AS fon_id, v_tip_imovel AS tip_id, 'ISAP Teste - Imóveis' AS con_nome FROM DUAL) src
       ON (dst.fon_id = src.fon_id AND dst.tip_id = src.tip_id)
     WHEN NOT MATCHED THEN
          INSERT (fon_id, tip_id, usu_id, con_nome, con_srs, con_situacao)
          VALUES (src.fon_id, src.tip_id, v_usu_id, src.con_nome, 4674, 'PUBLICADO');
    SELECT con_id INTO v_con_imovel FROM CONJUNTO_DADOS WHERE fon_id = v_fon_isap AND tip_id = v_tip_imovel;

    -- Conjunto Areas Protegidas Teste (camadas disponiveis)
    MERGE INTO CONJUNTO_DADOS dst
    USING (SELECT v_fon_isap AS fon_id, v_tip_ap AS tip_id, 'ISAP Teste - Áreas Protegidas' AS con_nome FROM DUAL) src
       ON (dst.fon_id = src.fon_id AND dst.tip_id = src.tip_id)
     WHEN NOT MATCHED THEN
          INSERT (fon_id, tip_id, usu_id, con_nome, con_srs, con_situacao)
          VALUES (src.fon_id, src.tip_id, v_usu_id, src.con_nome, 4674, 'PUBLICADO');
    SELECT con_id INTO v_con_uc FROM CONJUNTO_DADOS WHERE fon_id = v_fon_isap AND tip_id = v_tip_ap;
    v_con_ti   := v_con_uc;
    v_con_quil := v_con_uc;
    v_con_fp   := v_con_uc;

    -- Fonte e Conjunto extra SEM versao publicada (para simular CAMADA_INDISPONIVEL)
    MERGE INTO FONTE_DO_DADO dst
    USING (SELECT 'TESTE_ISAP_INDISP' AS fon_sigla, 'Fonte Sintética Indisponível ISAP' AS fon_nome FROM DUAL) src
       ON (dst.fon_sigla = src.fon_sigla)
     WHEN NOT MATCHED THEN
          INSERT (fon_nome, fon_sigla, fon_esfera)
          VALUES (src.fon_nome, src.fon_sigla, 'FEDERAL');
    SELECT fon_id INTO v_con_fp_indisp FROM FONTE_DO_DADO WHERE fon_sigla = 'TESTE_ISAP_INDISP';

    MERGE INTO CONJUNTO_DADOS dst
    USING (SELECT v_con_fp_indisp AS fon_id, v_tip_ap AS tip_id, 'ISAP Teste - Camada Indisponível' AS con_nome FROM DUAL) src
       ON (dst.fon_id = src.fon_id AND dst.tip_id = src.tip_id)
     WHEN NOT MATCHED THEN
          INSERT (fon_id, tip_id, usu_id, con_nome, con_srs, con_situacao)
          VALUES (src.fon_id, src.tip_id, v_usu_id, src.con_nome, 4674, 'RASCUNHO');

    -- 3. Arquivos Brutos e Versoes Vigentes (competencia 2026-10)
    MERGE INTO ARQUIVO_BRUTO dst
    USING (SELECT v_con_imovel AS con_id, '2026-10' AS comp, LPAD('A', 64, 'A') AS h FROM DUAL) src
       ON (dst.con_id = src.con_id AND dst.arq_competencia = src.comp AND dst.arq_hash = src.h)
     WHEN NOT MATCHED THEN
          INSERT (con_id, usu_id, arq_nome, arq_formato, arq_srs_declarado, arq_srs_utilizado,
                  arq_path_armazenamento, arq_status, arq_hash, arq_competencia)
          VALUES (src.con_id, v_usu_id, 'imoveis_teste_isap.geojson', 'GEOJSON', 4674, 4674,
                  'teste/imoveis_teste_isap.geojson', 'PROCESSADO', src.h, src.comp);
    SELECT arq_bruto_id INTO v_arq_imovel
      FROM ARQUIVO_BRUTO WHERE con_id = v_con_imovel AND arq_hash = LPAD('A', 64, 'A');

    MERGE INTO ARQUIVO_BRUTO dst
    USING (SELECT v_con_uc AS con_id, '2026-10' AS comp, LPAD('B', 64, 'B') AS h FROM DUAL) src
       ON (dst.con_id = src.con_id AND dst.arq_competencia = src.comp AND dst.arq_hash = src.h)
     WHEN NOT MATCHED THEN
          INSERT (con_id, usu_id, arq_nome, arq_formato, arq_srs_declarado, arq_srs_utilizado,
                  arq_path_armazenamento, arq_status, arq_hash, arq_competencia)
          VALUES (src.con_id, v_usu_id, 'areas_protegidas_teste_isap.geojson', 'GEOJSON', 4674, 4674,
                  'teste/areas_protegidas_teste_isap.geojson', 'PROCESSADO', src.h, src.comp);
    SELECT arq_bruto_id INTO v_arq_uc
      FROM ARQUIVO_BRUTO WHERE con_id = v_con_uc AND arq_hash = LPAD('B', 64, 'B');
    v_arq_ti := v_arq_uc; v_arq_quil := v_arq_uc; v_arq_fp := v_arq_uc;

    -- Versoes vigentes (numero 999 para nao colidir com cargas reais)
    MERGE INTO VERSAO dst
    USING (SELECT v_con_imovel AS con_id, 999 AS num FROM DUAL) src
       ON (dst.con_id = src.con_id AND dst.ver_numero = src.num)
     WHEN NOT MATCHED THEN
          INSERT (con_id, usu_id, ver_numero, ver_hash, ver_competencia, ver_situacao, ver_data_publicacao)
          VALUES (src.con_id, v_usu_id, src.num, LPAD('1', 64, '1'), '2026-10', 'VIGENTE', SYSTIMESTAMP);
    SELECT ver_id INTO v_ver_imovel FROM VERSAO WHERE con_id = v_con_imovel AND ver_numero = 999;

    MERGE INTO VERSAO dst
    USING (SELECT v_con_uc AS con_id, 999 AS num FROM DUAL) src
       ON (dst.con_id = src.con_id AND dst.ver_numero = src.num)
     WHEN NOT MATCHED THEN
          INSERT (con_id, usu_id, ver_numero, ver_hash, ver_competencia, ver_situacao, ver_data_publicacao)
          VALUES (src.con_id, v_usu_id, src.num, LPAD('2', 64, '2'), '2026-10', 'VIGENTE', SYSTIMESTAMP);
    SELECT ver_id INTO v_ver_uc FROM VERSAO WHERE con_id = v_con_uc AND ver_numero = 999;
    v_ver_ti := v_ver_uc; v_ver_quil := v_ver_uc; v_ver_fp := v_ver_uc;

    UPDATE CONJUNTO_DADOS SET con_ver_vigente_id = v_ver_imovel WHERE con_id = v_con_imovel;
    UPDATE CONJUNTO_DADOS SET con_ver_vigente_id = v_ver_uc     WHERE con_id = v_con_uc;

    -- =================================================================
    -- 4. IMOVEIS RURAIS SINTETICOS (grade regular 0.02 x 0.02 graus)
    -- =================================================================
    -- IMO-01: [-51.10, -25.10] a [-51.08, -25.08] -> SEM SOBREPOSICAO (0%)
    g_imo_01 := SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
        SDO_ORDINATE_ARRAY(-51.10,-25.10, -51.08,-25.10, -51.08,-25.08, -51.10,-25.08, -51.10,-25.10));

    -- IMO-02: [-51.06, -25.10] a [-51.04, -25.08] -> 1 CATEGORIA (UC cobre metade oeste: 50%)
    g_imo_02 := SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
        SDO_ORDINATE_ARRAY(-51.06,-25.10, -51.04,-25.10, -51.04,-25.08, -51.06,-25.08, -51.06,-25.10));

    -- IMO-03: [-51.02, -25.10] a [-51.00, -25.08] -> 4 CATEGORIAS + DUPLA CONTAGEM
    --   * UC: metade oeste [-51.02, -51.01] x [-25.10, -25.08] -> 50%
    --   * TERRA_INDIGENA: metade sul [-51.02, -51.00] x [-25.10, -25.09] -> 50% (coincide com UC no quadrante SW = 25%!)
    --   * QUILOMBOLA: quadrante NE [-51.01, -51.00] x [-25.09, -25.08] -> 25% (disjunto de UC e TI)
    --   * FLORESTA_PUBLICA: quadrante NW [-51.02, -51.01] x [-25.09, -25.08] -> 25% (100% coincidente com UC!)
    --   * Soma aritmetica = 50% + 50% + 25% + 25% = 150%, mas TOTAL dissolvido = 100% do imovel!
    g_imo_03 := SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
        SDO_ORDINATE_ARRAY(-51.02,-25.10, -51.00,-25.10, -51.00,-25.08, -51.02,-25.08, -51.02,-25.10));

    -- IMO-04: [-50.98, -25.10] a [-50.96, -25.08] -> 100% DENTRO DE UC + 2 UCs SOBREPOSTAS + 1 UC EXCLUIDA PELO FILTRO
    --   * UC-A (PARNA - incluida): engloba todo o imovel [-50.99, -50.95] x [-25.11, -25.07] -> 100%
    --   * UC-B (ESEC - incluida): sobrepoe metade leste [-50.97, -50.96] x [-25.10, -25.08] -> 50% isolada, mas dissolvida com UC-A = 100%
    --   * UC-C (APA - excluida quando filtro = 'PARNA,ESEC'): cobre todo o imovel -> 0% se excluida pelo filtro
    g_imo_04 := SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
        SDO_ORDINATE_ARRAY(-50.98,-25.10, -50.96,-25.10, -50.96,-25.08, -50.98,-25.08, -50.98,-25.10));

    -- IMO-05: [-50.94, -25.10] a [-50.92, -25.08] -> TESTE DE CAMADA INDISPONIVEL
    --   * Sobrepoe 25% com TERRA_INDIGENA, mas quando avaliado com FLORESTA_PUBLICA indisponivel,
    --     FLORESTA_PUBLICA = CAMADA_INDISPONIVEL (NULL) e TOTAL = CAMADA_INDISPONIVEL (NULL).
    g_imo_05 := SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
        SDO_ORDINATE_ARRAY(-50.94,-25.10, -50.92,-25.10, -50.92,-25.08, -50.94,-25.08, -50.94,-25.10));

    a_imo_01 := ROUND(PKG_GEO.area_ha(g_imo_01), 4);
    a_imo_02 := ROUND(PKG_GEO.area_ha(g_imo_02), 4);
    a_imo_03 := ROUND(PKG_GEO.area_ha(g_imo_03), 4);
    a_imo_04 := ROUND(PKG_GEO.area_ha(g_imo_04), 4);
    a_imo_05 := ROUND(PKG_GEO.area_ha(g_imo_05), 4);

    INSERT INTO IMOVEL_RURAL (arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_arq_imovel, v_ver_imovel, 'PR-TESTE-ISAP-01', 'Fazenda Sem Conflito', 'ARQUIVO', g_imo_01, a_imo_01);

    INSERT INTO IMOVEL_RURAL (arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_arq_imovel, v_ver_imovel, 'PR-TESTE-ISAP-02', 'Fazenda Parcial UC', 'ARQUIVO', g_imo_02, a_imo_02);

    INSERT INTO IMOVEL_RURAL (arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_arq_imovel, v_ver_imovel, 'PR-TESTE-ISAP-03', 'Fazenda Quatro Camadas Coincidentes', 'ARQUIVO', g_imo_03, a_imo_03);

    INSERT INTO IMOVEL_RURAL (arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_arq_imovel, v_ver_imovel, 'PR-TESTE-ISAP-04', 'Fazenda Integralmente Protegida', 'ARQUIVO', g_imo_04, a_imo_04);

    INSERT INTO IMOVEL_RURAL (arq_bruto_id, ver_id, imo_codigo_car, imo_nome, imo_nome_origem, imo_geometria, imo_area_total)
    VALUES (v_arq_imovel, v_ver_imovel, 'PR-TESTE-ISAP-05', 'Fazenda Camada Indisponivel', 'ARQUIVO', g_imo_05, a_imo_05);

    -- =================================================================
    -- 5. FEICOES DAS 4 CAMADAS DE AREAS PROTEGIDAS
    -- =================================================================
    -- 5.1 UC para IMO-02 (metade oeste: [-51.06, -51.05] x [-25.10, -25.08])
    INSERT INTO AREA_PROTEGIDA (arq_bruto_id, ver_id, apr_tipo, apr_nome, apr_geometria)
    VALUES (v_arq_uc, v_ver_uc, 'UC', '[TESTE-ISAP] PARNA Serra Oeste (PARNA)',
        SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(-51.06,-25.10, -51.05,-25.10, -51.05,-25.08, -51.06,-25.08, -51.06,-25.10)));

    -- 5.2 Camadas para IMO-03 (4 categorias sobrepostas entre si)
    -- UC (metade oeste: 50%)
    INSERT INTO AREA_PROTEGIDA (arq_bruto_id, ver_id, apr_tipo, apr_nome, apr_geometria)
    VALUES (v_arq_uc, v_ver_uc, 'UC', '[TESTE-ISAP] REBIO Vale Central (REBIO)',
        SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(-51.02,-25.10, -51.01,-25.10, -51.01,-25.08, -51.02,-25.08, -51.02,-25.10)));

    -- TERRA_INDIGENA (metade sul: 50%, coincide 25% com UC)
    INSERT INTO AREA_PROTEGIDA (arq_bruto_id, ver_id, apr_tipo, apr_nome, apr_geometria)
    VALUES (v_arq_ti, v_ver_ti, 'TERRA_INDIGENA', '[TESTE-ISAP] TI Rio Sul',
        SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(-51.02,-25.10, -51.00,-25.10, -51.00,-25.09, -51.02,-25.09, -51.02,-25.10)));

    -- QUILOMBOLA (quadrante NE: 25%, disjunto)
    INSERT INTO AREA_PROTEGIDA (arq_bruto_id, ver_id, apr_tipo, apr_nome, apr_geometria)
    VALUES (v_arq_quil, v_ver_quil, 'QUILOMBOLA', '[TESTE-ISAP] Comunidade Quilombola Nordeste',
        SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(-51.01,-25.09, -51.00,-25.09, -51.00,-25.08, -51.01,-25.08, -51.01,-25.09)));

    -- FLORESTA_PUBLICA (quadrante NW: 25%, 100% coincidente com UC)
    INSERT INTO AREA_PROTEGIDA (arq_bruto_id, ver_id, apr_tipo, apr_nome, apr_geometria)
    VALUES (v_arq_fp, v_ver_fp, 'FLORESTA_PUBLICA', '[TESTE-ISAP] Floresta Nacional Noroeste',
        SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(-51.02,-25.09, -51.01,-25.09, -51.01,-25.08, -51.02,-25.08, -51.02,-25.09)));

    -- 5.3 UCs para IMO-04 (imovel 100% dentro + 2 UCs sobrepostas + 1 UC excluida pelo filtro)
    INSERT INTO AREA_PROTEGIDA (arq_bruto_id, ver_id, apr_tipo, apr_nome, apr_geometria)
    VALUES (v_arq_uc, v_ver_uc, 'UC', '[TESTE-ISAP] PARNA Envolvente Total (PARNA)',
        SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(-50.99,-25.11, -50.95,-25.11, -50.95,-25.07, -50.99,-25.07, -50.99,-25.11)));

    INSERT INTO AREA_PROTEGIDA (arq_bruto_id, ver_id, apr_tipo, apr_nome, apr_geometria)
    VALUES (v_arq_uc, v_ver_uc, 'UC', '[TESTE-ISAP] ESEC Sobreposta Leste (ESEC)',
        SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(-50.97,-25.10, -50.96,-25.10, -50.96,-25.08, -50.97,-25.08, -50.97,-25.10)));

    -- UC de tipo APA (excluida quando filtro restringe a Protecao Integral)
    INSERT INTO AREA_PROTEGIDA (arq_bruto_id, ver_id, apr_tipo, apr_nome, apr_geometria)
    VALUES (v_arq_uc, v_ver_uc, 'UC', '[TESTE-ISAP] APA Excluida pelo Filtro (APA)',
        SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(-50.98,-25.10, -50.96,-25.10, -50.96,-25.08, -50.98,-25.08, -50.98,-25.10)));

    -- 5.4 Terra Indigena para IMO-05 (quadrante SW: 25%)
    INSERT INTO AREA_PROTEGIDA (arq_bruto_id, ver_id, apr_tipo, apr_nome, apr_geometria)
    VALUES (v_arq_ti, v_ver_ti, 'TERRA_INDIGENA', '[TESTE-ISAP] TI Cenario Indisponivel',
        SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(-50.94,-25.10, -50.93,-25.10, -50.93,-25.09, -50.94,-25.09, -50.94,-25.10)));

    -- =================================================================
    -- 6. GEOMETRIA INVALIDA (AUTO-INTERSECAO / BOW-TIE) EM FEICAO_STAGING
    --    Para validar saneamento e envio a QUARENTENA na US#24.05
    -- =================================================================
    INSERT INTO EXECUCAO_PIPELINE (con_id, arq_bruto_id, usu_id, exe_etapa, exe_dag_run_id, exe_status, exe_inicio)
    VALUES (v_con_uc, v_arq_uc, v_usu_id, 'TRATAMENTO', 'teste-isap-us24-03', 'EM_ANDAMENTO', SYSTIMESTAMP)
    RETURNING exe_id INTO v_exe_teste;

    -- Poligono em "X" (auto-intersecao: erro ORA-13349)
    INSERT INTO FEICAO_STAGING (exe_id, stg_arq_bruto_id, stg_linha, stg_identificador, stg_atributos, stg_geometria)
    VALUES (v_exe_teste, v_arq_uc, 1, 'TESTE-ISAP-GEOM-INVALIDA-01',
        '{"categoria":"UC","nome":"[TESTE-ISAP] UC Auto-Intersecao","tipo_uc":"PARNA","codigo":"UC-INV-01"}',
        SDO_GEOMETRY(2003, 4674, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1),
            SDO_ORDINATE_ARRAY(-51.05,-25.05, -51.04,-25.04, -51.05,-25.04, -51.04,-25.05, -51.05,-25.05)));

    COMMIT;
END;
/