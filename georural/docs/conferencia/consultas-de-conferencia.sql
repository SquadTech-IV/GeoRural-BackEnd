-- Consultas de conferencia do GeoRural.
-- Vieram do SqlTesteController, que foi removido do codigo da aplicacao.
-- Rode no SQL Developer Web (Database Actions) da OCI. Nao precisam da aplicacao no ar.
--
-- Onde aparece &exe_id, &val_id ou &padrao, o SQL Developer abre uma caixa
-- pedindo o valor quando voce roda a consulta.

-- ===================================================================
-- /execucao
-- ===================================================================
SELECT * FROM EXECUCAO_PIPELINE
ORDER BY EXE_ID DESC
FETCH FIRST 5 ROWS ONLY;

-- ===================================================================
-- /transformacao
-- ===================================================================
SELECT TRA_ID, EXE_ID, TRA_ORDEM, TRA_TIPO, TRA_DESCRICAO
FROM TRANSFORMACAO
WHERE EXE_ID = &exe_id
ORDER BY TRA_ORDEM;

SELECT TRA_ID, EXE_ID, TRA_ORDEM, TRA_TIPO, TRA_DESCRICAO
FROM TRANSFORMACAO
WHERE EXE_ID = (SELECT MAX(EXE_ID) FROM TRANSFORMACAO)
ORDER BY TRA_ORDEM;

-- ===================================================================
-- /versao
-- ===================================================================
SELECT VER_ID, CON_ID, VER_NUMERO, VER_SITUACAO, VER_EXE_ID, VER_HASH
FROM VERSAO
ORDER BY VER_ID DESC
FETCH FIRST 10 ROWS ONLY;

-- ===================================================================
-- /validacao
-- ===================================================================
SELECT v.VAL_ID, v.EXE_ID, v.ARQ_BRUTO_ID,
       v.VAL_TOTAL_REGISTROS, v.VAL_REGISTROS_VALIDOS,
       v.VAL_REGISTROS_INVALIDOS, v.VAL_STATUS_FINAL,
       v.VAL_DATA_EXECUCAO,
       (SELECT COUNT(*) FROM QUARENTENA q WHERE q.VAL_ID = v.VAL_ID)
           AS QTD_QUARENTENA
FROM VALIDACAO v
ORDER BY v.VAL_ID DESC
FETCH FIRST 5 ROWS ONLY;

-- ===================================================================
-- /quarentena
-- ===================================================================
SELECT QUA_ID, VAL_ID, QUA_LINHA_ORIGEM, QUA_COLUNA_ERRO,
       QUA_MOTIVO_ERRO, QUA_SITUACAO, QUA_DATA_REGISTRO,
       JSON_SERIALIZE(QUA_DADO_ORIGINAL RETURNING VARCHAR2(1000) TRUNCATE)
           AS DADO_ORIGINAL
FROM QUARENTENA
WHERE VAL_ID = &val_id
ORDER BY QUA_ID
FETCH FIRST 100 ROWS ONLY;

SELECT QUA_ID, VAL_ID, QUA_LINHA_ORIGEM, QUA_COLUNA_ERRO,
       QUA_MOTIVO_ERRO, QUA_SITUACAO, QUA_DATA_REGISTRO,
       JSON_SERIALIZE(QUA_DADO_ORIGINAL RETURNING VARCHAR2(1000) TRUNCATE)
           AS DADO_ORIGINAL
FROM QUARENTENA
ORDER BY QUA_ID DESC
FETCH FIRST 50 ROWS ONLY;

-- ===================================================================
-- /quarentena/total
-- ===================================================================
SELECT COUNT(*) FROM QUARENTENA;

-- ===================================================================
-- /movimentacao
-- ===================================================================
SELECT MOV_ID, ARQ_BRUTO_ID, EXE_ID, MOV_ZONA, MOV_STATUS,
       MOV_DATA, MOV_OBS
FROM MOVIMENTACAO_ZONA
ORDER BY MOV_ID DESC
FETCH FIRST 10 ROWS ONLY;

-- ===================================================================
-- /imovel/srid
-- ===================================================================
SELECT NVL(TO_CHAR(i.IMO_GEOMETRIA.SDO_SRID), 'NULL') AS SRID,
       COUNT(*) AS TOTAL
FROM IMOVEL_RURAL i
GROUP BY i.IMO_GEOMETRIA.SDO_SRID;

-- ===================================================================
-- /imovel/duplicados
-- ===================================================================
SELECT IMO_CODIGO_CAR, COUNT(*) AS TOTAL
FROM IMOVEL_RURAL
GROUP BY IMO_CODIGO_CAR
HAVING COUNT(*) > 1;

-- ===================================================================
-- /imovel
-- ===================================================================
SELECT i.IMO_ID, i.IMO_CODIGO_CAR, i.VER_ID, i.ARQ_BRUTO_ID,
       i.IMO_AREA_TOTAL, i.MUN_ID,
       i.IMO_GEOMETRIA.SDO_SRID AS SRID
FROM IMOVEL_RURAL i
ORDER BY i.IMO_ID DESC
FETCH FIRST 20 ROWS ONLY;

-- ===================================================================
-- /constraints
-- ===================================================================
SELECT TABLE_NAME, CONSTRAINT_NAME, SEARCH_CONDITION_VC
FROM USER_CONSTRAINTS
WHERE CONSTRAINT_TYPE = 'C'
  AND TABLE_NAME IN ('TRANSFORMACAO','QUARENTENA','VALIDACAO',
                     'MOVIMENTACAO_ZONA','EXECUCAO_PIPELINE','VERSAO')
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- ===================================================================
-- /checklist
-- ===================================================================
SELECT COUNT(*) FROM IMOVEL_RURAL i
WHERE i.IMO_GEOMETRIA.SDO_SRID IS NULL
   OR i.IMO_GEOMETRIA.SDO_SRID <> 4674;

SELECT COUNT(*) FROM (
    SELECT IMO_CODIGO_CAR FROM IMOVEL_RURAL
    GROUP BY IMO_CODIGO_CAR HAVING COUNT(*) > 1);

SELECT EXE_ID FROM EXECUCAO_PIPELINE
WHERE EXE_STATUS = 'CONCLUIDA'
ORDER BY EXE_ID DESC
FETCH FIRST 1 ROWS ONLY;

SELECT v.VAL_ID, v.EXE_ID, v.VAL_TOTAL_REGISTROS,
       v.VAL_REGISTROS_VALIDOS, v.VAL_REGISTROS_INVALIDOS,
       v.VAL_STATUS_FINAL,
       (SELECT COUNT(*) FROM QUARENTENA q WHERE q.VAL_ID = v.VAL_ID)
           AS QTD_QUARENTENA
FROM VALIDACAO v
WHERE v.EXE_ID = &exe_id
ORDER BY v.VAL_ID DESC
FETCH FIRST 1 ROWS ONLY;

SELECT COUNT(*) AS QTD, MIN(TRA_ORDEM) AS MENOR,
       MAX(TRA_ORDEM) AS MAIOR,
       COUNT(DISTINCT TRA_ORDEM) AS DISTINTAS
FROM TRANSFORMACAO
WHERE EXE_ID = &exe_id;

SELECT COUNT(*) FROM TRANSFORMACAO
WHERE EXE_ID = &exe_id AND TRA_DESCRICAO LIKE &padrao;

SELECT COUNT(*) FROM VERSAO
WHERE VER_EXE_ID = &exe_id AND VER_SITUACAO = 'VIGENTE';

SELECT COUNT(*) FROM MOVIMENTACAO_ZONA
WHERE EXE_ID = &exe_id AND MOV_ZONA = 'TRATADA'
  AND MOV_OBS LIKE '%caminho=%' AND MOV_OBS LIKE '%sha256=%';

SELECT EXE_ID,
       REGEXP_SUBSTR(TRA_DESCRICAO, 'sha256=([0-9a-f]{64})', 1, 1, NULL, 1)
           AS HASH_SAIDA,
       REGEXP_SUBSTR(TRA_DESCRICAO, 'caminho=([^,]+)', 1, 1, NULL, 1)
           AS CAMINHO
FROM TRANSFORMACAO
WHERE TRA_DESCRICAO LIKE '[GRAVACAO_ZONA_TRATADA]%'
  AND TRA_DESCRICAO LIKE '%imovel%'
ORDER BY EXE_ID DESC
FETCH FIRST 10 ROWS ONLY;

