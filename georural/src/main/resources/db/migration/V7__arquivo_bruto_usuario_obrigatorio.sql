-- =====================================================================
-- GeoRural DataHub - V7 - ARQUIVO_BRUTO.usu_id NOT NULL, FKs e indice (US#13.04)
-- =====================================================================

-- 1. Preenche eventuais linhas legadas sem dono usando o ADMIN espelho
UPDATE ARQUIVO_BRUTO
   SET usu_id = (
       SELECT usu_id
         FROM USUARIO
        WHERE LOWER(usu_email) = 'admin@georural.invalid'
   )
 WHERE usu_id IS NULL;

COMMIT;

-- 2. Torna ARQUIVO_BRUTO.usu_id obrigatorio (idempotente)
DECLARE
    v_nullable VARCHAR2(1);
BEGIN
    SELECT nullable
      INTO v_nullable
      FROM user_tab_columns
     WHERE table_name = 'ARQUIVO_BRUTO'
       AND column_name = 'USU_ID';

    IF v_nullable = 'Y' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ARQUIVO_BRUTO MODIFY (usu_id NOT NULL)';
    END IF;
END;
/

-- 3. Garante FKs e indice em ARQUIVO_BRUTO(usu_id)
DECLARE
    v_count NUMBER;
BEGIN
    -- FK ARQUIVO_BRUTO.usu_id -> USUARIO.usu_id
    SELECT COUNT(*) INTO v_count
      FROM user_cons_columns
     WHERE table_name = 'ARQUIVO_BRUTO'
       AND column_name = 'USU_ID'
       AND constraint_name IN (
           SELECT constraint_name FROM user_constraints WHERE table_name = 'ARQUIVO_BRUTO' AND constraint_type = 'R'
       );
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ARQUIVO_BRUTO ADD CONSTRAINT fk_arq_usuario FOREIGN KEY (usu_id) REFERENCES USUARIO (usu_id)';
    END IF;

    -- FK VALIDACAO.arq_bruto_id -> ARQUIVO_BRUTO.arq_bruto_id
    SELECT COUNT(*) INTO v_count
      FROM user_cons_columns
     WHERE table_name = 'VALIDACAO'
       AND column_name = 'ARQ_BRUTO_ID'
       AND constraint_name IN (
           SELECT constraint_name FROM user_constraints WHERE table_name = 'VALIDACAO' AND constraint_type = 'R'
       );
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE VALIDACAO ADD CONSTRAINT fk_val_arquivo FOREIGN KEY (arq_bruto_id) REFERENCES ARQUIVO_BRUTO (arq_bruto_id)';
    END IF;

    -- FK QUARENTENA.val_id -> VALIDACAO.val_id
    SELECT COUNT(*) INTO v_count
      FROM user_cons_columns
     WHERE table_name = 'QUARENTENA'
       AND column_name = 'VAL_ID'
       AND constraint_name IN (
           SELECT constraint_name FROM user_constraints WHERE table_name = 'QUARENTENA' AND constraint_type = 'R'
       );
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE QUARENTENA ADD CONSTRAINT fk_qua_validacao FOREIGN KEY (val_id) REFERENCES VALIDACAO (val_id)';
    END IF;

    -- Indice ARQUIVO_BRUTO(usu_id)
    SELECT COUNT(*) INTO v_count
      FROM user_ind_columns
     WHERE table_name = 'ARQUIVO_BRUTO'
       AND column_name = 'USU_ID';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_arquivo_bruto_usuario ON ARQUIVO_BRUTO (usu_id)';
    END IF;
END;
/