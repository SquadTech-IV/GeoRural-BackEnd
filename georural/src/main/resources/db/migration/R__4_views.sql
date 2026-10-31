-- =====================================================================
-- GeoRural DataHub - Views
-- =====================================================================

CREATE OR REPLACE VIEW vw_catalogo_conjuntos AS
SELECT c.con_id,
       f.fon_nome             AS fonte,
       f.fon_sigla            AS fonte_sigla,
       t.tip_nome             AS tipo,
       c.con_nome             AS conjunto,
       c.con_forma_entrada    AS forma_entrada,
       c.con_cobertura        AS cobertura,
       c.con_periodicidade    AS periodicidade,
       c.con_situacao         AS situacao,
       v.ver_numero           AS versao_vigente,
       v.ver_competencia      AS competencia,
       v.ver_data_publicacao  AS publicada_em
FROM   CONJUNTO_DADOS c
JOIN   FONTE_DO_DADO  f ON f.fon_id = c.fon_id
JOIN   TIPO_CONJUNTO  t ON t.tip_id = c.tip_id
LEFT JOIN VERSAO      v ON v.ver_id = c.con_ver_vigente_id;

-- Painel do Airflow
CREATE OR REPLACE VIEW vw_painel_execucoes AS
SELECT e.exe_id,
       c.con_nome                  AS conjunto,
       a.arq_nome                  AS arquivo,
       e.exe_etapa                 AS etapa,
       e.exe_dag_run_id            AS dag_run_id,
       e.exe_status                AS situacao,
       e.exe_registros_recebidos   AS recebidos,
       e.exe_registros_processados AS processados,
       e.exe_registros_aprovados   AS aprovados,
       e.exe_registros_rejeitados  AS rejeitados,
       e.exe_mensagem              AS mensagem,
       u.usu_nome                  AS disparado_por,
       v.ver_numero                AS versao_gerada,
       e.exe_duracao_seg           AS duracao_seg,
       e.exe_inicio                AS iniciado_em,
       e.exe_fim                   AS finalizado_em
FROM   EXECUCAO_PIPELINE e
JOIN   CONJUNTO_DADOS    c ON c.con_id = e.con_id
LEFT JOIN ARQUIVO_BRUTO  a ON a.arq_bruto_id = e.arq_bruto_id
LEFT JOIN USUARIO        u ON u.usu_id = e.usu_id
LEFT JOIN VERSAO         v ON v.ver_id = e.exe_ver_gerada_id;


CREATE OR REPLACE VIEW vw_trilha_execucao AS
SELECT e.exe_dag_run_id            AS dag_run_id,
       e.arq_bruto_id,
       a.arq_nome                  AS arquivo,
       a.arq_canal_entrada         AS canal,
       a.arq_competencia           AS competencia,
       c.con_nome                  AS conjunto,
       e.exe_id,
       e.exe_etapa                 AS etapa,
       CASE e.exe_etapa
            WHEN 'INGESTAO'   THEN 1 WHEN 'VALIDACAO'  THEN 2
            WHEN 'TRATAMENTO' THEN 3 WHEN 'CALCULO'    THEN 4
            WHEN 'QUALIDADE'  THEN 5 WHEN 'PUBLICACAO' THEN 6
            ELSE 9 END             AS ordem_etapa,
       e.exe_status                AS situacao,
       e.exe_registros_recebidos   AS recebidos,
       e.exe_registros_aprovados   AS aprovados,
       e.exe_registros_rejeitados  AS rejeitados,
       e.exe_inicio                AS iniciado_em,
       e.exe_duracao_seg           AS duracao_seg
FROM   EXECUCAO_PIPELINE e
JOIN   CONJUNTO_DADOS    c ON c.con_id = e.con_id
LEFT JOIN ARQUIVO_BRUTO  a ON a.arq_bruto_id = e.arq_bruto_id;


CREATE OR REPLACE VIEW vw_arquivos_processados AS
SELECT a.arq_bruto_id,
       a.arq_pai_id,
       a.arq_nome                  AS arquivo,
       a.arq_caminho_interno       AS caminho_interno,
       p.arq_nome                  AS arquivo_pai,
       c.con_nome                  AS conjunto,
       t.tip_codigo                AS tipo_conjunto,
       a.arq_formato_detectado     AS formato,
       a.arq_srs_declarado         AS srs_declarado,
       a.arq_srs_utilizado         AS srs_utilizado,
       a.arq_canal_entrada         AS canal,
       a.arq_competencia           AS competencia,
       a.arq_tamanho_bytes         AS tamanho_bytes,
       a.arq_status                AS situacao,
       CASE WHEN a.arq_status = 'PROCESSADO' THEN 'S' ELSE 'N' END AS processado,
       u.usu_nome                  AS recebido_por,
       a.arq_data_upload           AS recebido_em,
       (SELECT MAX(e.exe_fim) FROM EXECUCAO_PIPELINE e
         WHERE e.arq_bruto_id = a.arq_bruto_id AND e.exe_etapa = 'TRATAMENTO') AS processado_em
FROM   ARQUIVO_BRUTO  a
JOIN   CONJUNTO_DADOS c ON c.con_id = a.con_id
JOIN   TIPO_CONJUNTO  t ON t.tip_id = c.tip_id
LEFT JOIN ARQUIVO_BRUTO p ON p.arq_bruto_id = a.arq_pai_id
LEFT JOIN USUARIO      u ON u.usu_id = a.usu_id;

CREATE OR REPLACE VIEW vw_qualidade_conjunto AS
SELECT c.con_id,
       c.con_nome                  AS conjunto,
       val.val_total_registros     AS total,
       val.val_registros_validos   AS validos,
       val.val_registros_invalidos AS invalidos,
       CASE WHEN val.val_total_registros > 0
            THEN ROUND(val.val_registros_validos / val.val_total_registros * 100, 2)
       END                         AS indice_qualidade,
       val.val_data_execucao       AS validado_em
FROM   VALIDACAO      val
JOIN   ARQUIVO_BRUTO  a ON a.arq_bruto_id = val.arq_bruto_id
JOIN   CONJUNTO_DADOS c ON c.con_id = a.con_id;

CREATE OR REPLACE VIEW vw_resultado_imovel AS
SELECT r.rin_id,
       i.imo_codigo_car       AS imovel_car,
       m.mun_nome             AS municipio,
       ind.ind_sigla          AS indicador,
       ind.ind_nome           AS indicador_nome,
       r.rin_valor_percentual AS valor_percentual,
       r.rin_valor_hectares   AS valor_hectares,
       r.rin_valor_absoluto   AS valor_absoluto,
       r.rin_vigente          AS vigente,
       v.ver_numero           AS versao,
       v.ver_situacao         AS versao_situacao,
       rca.rca_versao         AS regra_calculo,
       r.rin_data_calculo     AS calculado_em
FROM   RESULTADO_INDICADOR r
JOIN   IMOVEL_RURAL   i   ON i.imo_id = r.imo_id
LEFT JOIN MUNICIPIO   m   ON m.mun_id = i.mun_id
JOIN   INDICADOR      ind ON ind.ind_id = r.ind_id
JOIN   VERSAO         v   ON v.ver_id = r.ver_id
LEFT JOIN REGRA_CALCULO rca ON rca.rca_id = r.rca_id;

CREATE OR REPLACE VIEW vw_quarentena AS
SELECT q.qua_id,
       c.con_nome             AS conjunto,
       a.arq_nome             AS arquivo,
       q.qua_etapa            AS etapa,
       q.qua_identificador    AS feicao,
       reg.reg_nome           AS regra,
       reg.reg_tipo           AS tipo_regra,
       reg.reg_severidade     AS severidade,
       q.qua_linha_origem     AS linha,
       q.qua_coluna_erro      AS coluna,
       q.qua_valor_encontrado AS encontrado,
       q.qua_valor_esperado   AS esperado,
       q.qua_motivo_erro      AS motivo,
       q.qua_situacao         AS situacao,
       q.qua_data_registro    AS registrado_em
FROM   QUARENTENA     q
JOIN   VALIDACAO      val ON val.val_id = q.val_id
JOIN   ARQUIVO_BRUTO  a   ON a.arq_bruto_id = val.arq_bruto_id
JOIN   CONJUNTO_DADOS c   ON c.con_id = a.con_id
LEFT JOIN REGRA_QUALIDADE reg ON reg.reg_id = q.reg_id;

CREATE OR REPLACE VIEW vw_auditoria AS
SELECT a.aud_id,
       a.aud_data_hora        AS quando,
       COALESCE(u.usu_nome, a.aud_usuario_nome, 'sistema') AS quem,
       p.per_funcao           AS perfil,
       u.usu_email            AS email,
       a.aud_operacao         AS operacao,
       a.aud_entidade_afetada AS entidade,
       a.aud_registro_id      AS registro_id,
       a.aud_ip_origem        AS ip_origem,
       a.aud_dados_antigos    AS dados_antigos,
       a.aud_dados_novos      AS dados_novos
FROM   AUDITORIA a
LEFT JOIN USUARIO u ON u.usu_id = a.usu_id
LEFT JOIN PERFIL  p ON p.per_id = u.per_id;

CREATE OR REPLACE VIEW vw_versao_reconstituicao AS
SELECT v.ver_id,
       v.ver_numero            AS versao,
       v.ver_situacao          AS situacao,
       v.ver_competencia       AS competencia,
       v.ver_hash              AS hash_da_versao,
       v.ver_data_publicacao   AS publicada_em,
       up.usu_nome             AS publicada_por,
       c.con_nome              AS conjunto,
       e.exe_id                AS execucao_id,
       e.exe_dag_run_id        AS dag_run_id,
       e.exe_inicio            AS executada_em,
       e.exe_duracao_seg       AS duracao_seg,
       a.arq_bruto_id,
       a.arq_nome              AS arquivo_origem,
       a.arq_hash              AS hash_arquivo,
       a.arq_algoritmo_hash    AS algoritmo_hash,
       a.arq_srs_declarado     AS srs_declarado,
       a.arq_srs_utilizado     AS srs_utilizado,
       CASE WHEN a.arq_srs_declarado IS NULL THEN 'SUPOSTO' ELSE 'LIDO DO ARQUIVO' END
                               AS origem_do_srs,
       a.arq_competencia       AS competencia_arquivo,
       a.arq_canal_entrada     AS canal,
       a.arq_path_armazenamento AS caminho_zona_bruta,
       col.col_endpoint        AS api_endpoint,
       col.col_parametros      AS api_parametros,
       col.col_data_coleta     AS api_coletado_em
FROM   VERSAO v
JOIN   CONJUNTO_DADOS c  ON c.con_id = v.con_id
LEFT JOIN USUARIO     up ON up.usu_id = v.usu_id
LEFT JOIN EXECUCAO_PIPELINE e ON e.exe_id = v.ver_exe_id
LEFT JOIN ARQUIVO_BRUTO a  ON a.arq_bruto_id = e.arq_bruto_id
LEFT JOIN COLETA_API   col ON col.arq_bruto_id = a.arq_bruto_id;