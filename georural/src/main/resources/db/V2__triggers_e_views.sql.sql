-- GeoRural DataHub - Database Script - 02 Triggers e Views

-- Triggers
CREATE OR REPLACE TRIGGER trg_versao_imutavel
BEFORE UPDATE OR DELETE ON VERSAO
FOR EACH ROW
BEGIN
    IF DELETING THEN
        IF :OLD.ver_situacao IN ('VIGENTE','ANTERIOR') THEN
            RAISE_APPLICATION_ERROR(-20001,'Versao publicada e imutavel: nao pode ser excluida.');
        END IF;
    ELSIF UPDATING THEN
        IF :OLD.ver_situacao = 'ANTERIOR' THEN
            RAISE_APPLICATION_ERROR(-20002,'Versao anterior e imutavel: nao pode ser alterada.');
        END IF;
        IF :OLD.ver_situacao = 'VIGENTE' AND :NEW.ver_situacao NOT IN ('VIGENTE','ANTERIOR') THEN
            RAISE_APPLICATION_ERROR(-20003,'Versao vigente so pode passar para ANTERIOR.');
        END IF;
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_aud_versao
AFTER INSERT OR UPDATE ON VERSAO
FOR EACH ROW
DECLARE
    PRAGMA AUTONOMOUS_TRANSACTION;
    v_op VARCHAR2(20);
BEGIN
    v_op := CASE WHEN INSERTING THEN 'INSERT' ELSE 'UPDATE' END;
    INSERT INTO AUDITORIA (usu_id, aud_operacao, aud_entidade_afetada, aud_registro_id, aud_dados_novos)
    VALUES (:NEW.usu_id, v_op, 'VERSAO', :NEW.ver_id,
            '{"numero":' || :NEW.ver_numero || ',"situacao":"' || :NEW.ver_situacao || '"}');
    COMMIT;
END;
/

CREATE OR REPLACE TRIGGER trg_aud_regra
AFTER INSERT OR UPDATE ON REGRA_CALCULO
FOR EACH ROW
DECLARE
    PRAGMA AUTONOMOUS_TRANSACTION;
    v_op VARCHAR2(20);
BEGIN
    v_op := CASE WHEN INSERTING THEN 'INSERT' ELSE 'UPDATE' END;
    INSERT INTO AUDITORIA (aud_operacao, aud_entidade_afetada, aud_registro_id, aud_dados_novos)
    VALUES (v_op, 'REGRA_CALCULO', :NEW.rca_id,
            '{"versao":"' || :NEW.rca_versao || '","vigente":"' || :NEW.rca_vigente || '"}');
    COMMIT;
END;
/

CREATE OR REPLACE TRIGGER trg_rin_imutavel
BEFORE INSERT OR UPDATE OR DELETE ON RESULTADO_INDICADOR
FOR EACH ROW
DECLARE
    v_ver_id   NUMBER;
    v_situacao VARCHAR2(15);
BEGIN
    v_ver_id := CASE WHEN INSERTING THEN :NEW.ver_id ELSE :OLD.ver_id END;
    IF v_ver_id IS NULL THEN
        RETURN;
    END IF;
    SELECT ver_situacao INTO v_situacao FROM VERSAO WHERE ver_id = v_ver_id;
    IF v_situacao IN ('VIGENTE', 'ANTERIOR') THEN
        RAISE_APPLICATION_ERROR(-20011,
            'Resultado de indicador pertence a versao publicada e e imutavel.');
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        NULL; -- versao inexistente: deixa a FK tratar
END;
/

CREATE OR REPLACE TRIGGER trg_rmu_imutavel
BEFORE INSERT OR UPDATE OR DELETE ON RESULTADO_MUNICIPIO
FOR EACH ROW
DECLARE
    v_ver_id   NUMBER;
    v_situacao VARCHAR2(15);
BEGIN
    v_ver_id := CASE WHEN INSERTING THEN :NEW.ver_id ELSE :OLD.ver_id END;
    IF v_ver_id IS NULL THEN
        RETURN;
    END IF;
    SELECT ver_situacao INTO v_situacao FROM VERSAO WHERE ver_id = v_ver_id;
    IF v_situacao IN ('VIGENTE', 'ANTERIOR') THEN
        RAISE_APPLICATION_ERROR(-20012,
            'Resultado municipal pertence a versao publicada e e imutavel.');
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        NULL;
END;
/

-- Views
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

CREATE OR REPLACE VIEW vw_painel_execucoes AS
SELECT e.exe_id,
       c.con_nome                  AS conjunto,
       e.exe_etapa                 AS etapa,
       e.exe_status                AS situacao,
       e.exe_registros_processados AS processados,
       e.exe_registros_rejeitados  AS rejeitados,
       v.ver_numero                AS versao_gerada,
       e.exe_duracao_seg           AS duracao_seg,
       e.exe_inicio                AS iniciado_em
FROM   EXECUCAO_PIPELINE e
JOIN   CONJUNTO_DADOS    c ON c.con_id = e.con_id
LEFT JOIN VERSAO         v ON v.ver_id = e.exe_ver_gerada_id;

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
       v.ver_numero           AS versao,
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
       c.con_nome          AS conjunto,
       reg.reg_nome        AS regra,
       reg.reg_tipo        AS tipo_regra,
       q.qua_linha_origem  AS linha,
       q.qua_coluna_erro   AS coluna,
       q.qua_motivo_erro   AS motivo,
       q.qua_situacao      AS situacao,
       q.qua_data_registro AS registrado_em
FROM   QUARENTENA     q
JOIN   VALIDACAO      val ON val.val_id = q.val_id
JOIN   ARQUIVO_BRUTO  a   ON a.arq_bruto_id = val.arq_bruto_id
JOIN   CONJUNTO_DADOS c   ON c.con_id = a.con_id
LEFT JOIN REGRA_QUALIDADE reg ON reg.reg_id = q.reg_id;
