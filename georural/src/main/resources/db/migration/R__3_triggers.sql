-- =====================================================================
-- GeoRural DataHub - Triggers
-- =====================================================================


-- Versao publicada nao pode ser apagada nem alterada.
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
BEGIN
    PKG_AUDIT.registrar(
        CASE WHEN INSERTING THEN 'INSERT' ELSE 'UPDATE' END,
        'VERSAO',
        :NEW.ver_id,
        '{"numero":' || :NEW.ver_numero ||
        ',"situacao":"' || :NEW.ver_situacao || '"}');
END;
/


-- autor vem do token do Keycloak.
CREATE OR REPLACE TRIGGER trg_aud_regra
AFTER INSERT OR UPDATE ON REGRA_CALCULO
FOR EACH ROW
BEGIN
    PKG_AUDIT.registrar(
        CASE WHEN INSERTING THEN 'INSERT' ELSE 'UPDATE' END,
        'REGRA_CALCULO',
        :NEW.rca_id,
        '{"versao":"' || :NEW.rca_versao ||
        '","vigente":"' || :NEW.rca_vigente || '"}');
END;
/

-- Publicacao e ato manual do gestor: registra quem assumiu o numero.
CREATE OR REPLACE TRIGGER trg_aud_publicacao
AFTER UPDATE OF ver_situacao ON VERSAO
FOR EACH ROW
WHEN (NEW.ver_situacao = 'VIGENTE' AND OLD.ver_situacao = 'RASCUNHO')
BEGIN
    PKG_AUDIT.registrar(
        'UPDATE',
        'PUBLICACAO',
        :NEW.ver_id,
        '{"versao":' || :NEW.ver_numero ||
        ',"competencia":"' || NVL(:NEW.ver_competencia,'') || '"}');
END;
/

-- Trilha de quem alterou parametro editavel de regra (ex.: RL de 20% para 40%).
CREATE OR REPLACE TRIGGER trg_aud_param_regra
AFTER UPDATE OF prc_valor ON PARAMETRO_REGRA_CALCULO
FOR EACH ROW
BEGIN
    PKG_AUDIT.registrar(
        'UPDATE',
        'PARAMETRO_REGRA',
        :NEW.prc_id,
        '{"nome":"' || :NEW.prc_nome || '","valor":"' || :NEW.prc_valor || '"}',
        '{"nome":"' || :OLD.prc_nome || '","valor":"' || :OLD.prc_valor || '"}');
END;
/

-- =====================================================================
-- Imutabilidade da regra de calculo
-- =====================================================================

CREATE OR REPLACE TRIGGER trg_param_regra_imutavel
BEFORE UPDATE OR DELETE ON PARAMETRO_REGRA_CALCULO
FOR EACH ROW
DECLARE
    v_usos NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_usos
      FROM RESULTADO_INDICADOR
     WHERE rca_id = :OLD.rca_id
       AND ROWNUM = 1;

    IF v_usos > 0 THEN
        RAISE_APPLICATION_ERROR(-20030,
            'Esta regra ja gerou resultados e esta congelada. Para mudar o parametro "'
            || :OLD.prc_nome || '", crie uma NOVA VERSAO da regra e reprocesse.');
    END IF;
END;
/

CREATE OR REPLACE TRIGGER trg_regra_calculo_imutavel
BEFORE UPDATE OF rca_formula, rca_versao ON REGRA_CALCULO
FOR EACH ROW
DECLARE
    v_usos NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_usos
      FROM RESULTADO_INDICADOR
     WHERE rca_id = :OLD.rca_id
       AND ROWNUM = 1;

    IF v_usos > 0 THEN
        RAISE_APPLICATION_ERROR(-20031,
            'Regra ' || :OLD.rca_versao || ' ja gerou resultados: formula e versao '
            || 'sao imutaveis. Crie uma nova versao da regra.');
    END IF;
END;
/
